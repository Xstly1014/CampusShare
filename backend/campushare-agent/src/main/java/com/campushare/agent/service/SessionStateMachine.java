package com.campushare.agent.service;

import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentSessionEvent;
import com.campushare.agent.enums.SessionStatus;
import com.campushare.agent.mapper.AgentSessionEventMapper;
import com.campushare.agent.mapper.AgentSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 会话状态机服务（ADR-064~069）。
 *
 * 8 状态严格转移规则，确保多轮对话中 Agent 行为可预测、可恢复、可审计。
 *
 * 状态流转图：
 *   INIT ──首条消息──► ACTIVE
 *   ACTIVE ──工具调用──► TOOL_CALLING ──► ACTIVE
 *   ACTIVE ──需要澄清──► WAITING_CLARIFY ──► ACTIVE
 *   ACTIVE ──回答完成──► ACTIVE（后台触发 REFLECTING ──► ACTIVE）
 *   ACTIVE ──超时30min──► ARCHIVED
 *   ACTIVE ──用户关闭──► CLOSED
 *   任何状态 ──不可恢复错误──► ERROR
 *
 * 转移规则：
 *   - TOOL_CALLING / WAITING_CLARIFY 不能直接到 ARCHIVED，必须先回 ACTIVE 或 ERROR
 *   - REFLECTING 是伪状态（异步），不阻塞 ACTIVE
 *   - 任何状态 → ERROR 合法
 *
 * 原子性（ADR-066）：MVP 阶段用 Redis HGET + HSET（非原子），后续可升级为 Lua 脚本。
 *
 * 审计（ADR-068）：所有转移写入 agent_session_events 表，保留 90 天。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStateMachine {

    private final StringRedisTemplate redis;
    private final AgentSessionMapper sessionMapper;
    private final AgentSessionEventMapper eventMapper;

    /** Redis meta Key 前缀（与 ConversationMemoryService 一致） */
    private static final String META_KEY_PREFIX = "agent:session:";
    private static final String META_KEY_SUFFIX = ":meta";
    private static final String STATUS_FIELD = "status";

    /**
     * 合法转移矩阵。
     *
     * key: 源状态, value: 可转移到的目标状态集合。
     * 任何状态 → ERROR 是全局合法的，在 canTransition 中单独处理。
     */
    private static final Map<SessionStatus, Set<SessionStatus>> TRANSITIONS = new EnumMap<>(SessionStatus.class);

    static {
        TRANSITIONS.put(SessionStatus.INIT, EnumSet.of(SessionStatus.ACTIVE));
        TRANSITIONS.put(SessionStatus.ACTIVE, EnumSet.of(
                SessionStatus.TOOL_CALLING,
                SessionStatus.WAITING_CLARIFY,
                SessionStatus.REFLECTING,
                SessionStatus.ARCHIVED,
                SessionStatus.CLOSED
        ));
        TRANSITIONS.put(SessionStatus.TOOL_CALLING, EnumSet.of(SessionStatus.ACTIVE));
        TRANSITIONS.put(SessionStatus.WAITING_CLARIFY, EnumSet.of(SessionStatus.ACTIVE));
        TRANSITIONS.put(SessionStatus.REFLECTING, EnumSet.of(SessionStatus.ACTIVE));
        TRANSITIONS.put(SessionStatus.ARCHIVED, EnumSet.of(SessionStatus.ACTIVE)); // 重新打开
        TRANSITIONS.put(SessionStatus.CLOSED, EnumSet.noneOf(SessionStatus.class)); // 终态
        TRANSITIONS.put(SessionStatus.ERROR, EnumSet.of(SessionStatus.INIT)); // 恢复需新建会话
    }

    /**
     * 验证状态转移是否合法。
     *
     * 规则：
     *   1. 任何状态 → ERROR 合法
     *   2. 其他转移查 TRANSITIONS 矩阵
     *   3. 同状态转移（如 ACTIVE → ACTIVE）合法（幂等更新）
     *
     * @param from 源状态
     * @param to   目标状态
     * @return true 如果转移合法
     */
    public boolean canTransition(SessionStatus from, SessionStatus to) {
        if (from == to) return true; // 幂等
        if (to == SessionStatus.ERROR) return true; // 任何状态 → ERROR

        Set<SessionStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 执行状态转移（CAS 语义）。
     *
     * 流程：
     *   1. 验证 from → to 合法
     *   2. 从 Redis HGET meta status 获取当前状态
     *   3. 验证当前状态 == from（CAS）
     *   4. HSET meta status = to
     *   5. 更新 DB session status
     *   6. 记录事件到 agent_session_events
     *
     * @param sessionId 会话ID
     * @param from      期望的源状态（CAS 条件）
     * @param to        目标状态
     * @param reason    转移原因（写入事件表）
     * @return true 如果转移成功，false 如果 CAS 失败或转移非法
     */
    public boolean transition(String sessionId, SessionStatus from, SessionStatus to, String reason) {
        try {
            // 1. 验证合法性
            if (!canTransition(from, to)) {
                log.warn("Illegal transition: {} → {} for sessionId={}", from, to, sessionId);
                return false;
            }

            // 2. CAS：检查 Redis 当前状态
            String metaKey = META_KEY_PREFIX + sessionId + META_KEY_SUFFIX;
            String currentStatus = (String) redis.opsForHash().get(metaKey, STATUS_FIELD);

            // Redis 有状态时验证 CAS；Redis 无状态（故障或新会话）时跳过 CAS
            if (currentStatus != null && !currentStatus.equals(from.name())) {
                log.warn("CAS failed: expected={}, actual={}, sessionId={}", from, currentStatus, sessionId);
                return false;
            }

            // 3. 更新 Redis
            redis.opsForHash().put(metaKey, STATUS_FIELD, to.name());

            // 4. 更新 DB
            AgentSession session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setStatus(to.name());
                sessionMapper.updateById(session);
            }

            // 5. 记录事件
            recordEvent(sessionId, from, to, reason);

            log.info("Session transition: {} → {} ({}), sessionId={}", from, to, reason, sessionId);
            return true;
        } catch (Exception e) {
            log.error("Failed to transition: {} → {}, sessionId={}, error={}",
                    from, to, sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 直接设置状态（不验证 CAS，用于初始化或恢复）。
     *
     * @param sessionId 会话ID
     * @param status    目标状态
     * @param reason    原因
     */
    public void setStatus(String sessionId, SessionStatus status, String reason) {
        try {
            String metaKey = META_KEY_PREFIX + sessionId + META_KEY_SUFFIX;
            redis.opsForHash().put(metaKey, STATUS_FIELD, status.name());

            AgentSession session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setStatus(status.name());
                sessionMapper.updateById(session);
            }

            recordEvent(sessionId, null, status, reason);
            log.info("Session status set: {} ({}), sessionId={}", status, reason, sessionId);
        } catch (Exception e) {
            log.error("Failed to set status: {}, sessionId={}, error={}", status, sessionId, e.getMessage());
        }
    }

    /**
     * 从 Redis 获取当前状态。
     *
     * Redis 故障时降级到 DB 查询。
     *
     * @param sessionId 会话ID
     * @return 当前状态，null 表示无法获取
     */
    public SessionStatus getCurrentStatus(String sessionId) {
        try {
            String metaKey = META_KEY_PREFIX + sessionId + META_KEY_SUFFIX;
            String status = (String) redis.opsForHash().get(metaKey, STATUS_FIELD);
            if (status != null) {
                return SessionStatus.valueOf(status);
            }
            // Redis 无状态，降级到 DB
            AgentSession session = sessionMapper.selectById(sessionId);
            if (session != null && session.getStatus() != null) {
                return SessionStatus.valueOf(session.getStatus());
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get current status: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 记录状态转移事件到 DB（ADR-068）。
     */
    private void recordEvent(String sessionId, SessionStatus from, SessionStatus to, String reason) {
        try {
            AgentSessionEvent event = AgentSessionEvent.builder()
                    .sessionId(sessionId)
                    .fromStatus(from != null ? from.name() : null)
                    .toStatus(to.name())
                    .reason(reason)
                    .build();
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("Failed to record event: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
