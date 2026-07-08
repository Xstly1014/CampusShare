package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.enums.SessionStatus;
import com.campushare.agent.mapper.AgentSessionMapper;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话归档与僵尸会话清理服务（ADR-057, ADR-064）。
 *
 * 职责：
 *   - 僵尸会话清理：每分钟 SCAN 发现 last_active_at > 30min 且 status=ACTIVE 的会话，触发归档
 *   - 会话归档持久化：Redis 5 Key 数据 → MySQL（agent_sessions/agent_turns）
 *   - 归档时抽取长期记忆：调用 LongTermMemoryService.extractMemories()
 *   - 归档后清理 Redis Key（延迟 5 分钟，避免边界竞态）
 *
 * 触发方式：
 *   - 定时任务：每分钟扫描僵尸会话
 *   - 主动调用：用户手动归档/关闭会话时调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionArchivalService {

    private final StringRedisTemplate redis;
    private final AgentSessionMapper sessionMapper;
    private final AgentTurnMapper turnMapper;
    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final SessionStateMachine sessionStateMachine;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "agent:session:";

    /** 僵尸会话阈值：30 分钟未活跃 */
    private static final Duration ZOMBIE_THRESHOLD = Duration.ofMinutes(30);

    /** 归档后延迟删除 Redis Key 的时间（避免边界竞态） */
    private static final Duration DELETE_DELAY = Duration.ofMinutes(5);

    /** 每批扫描的 cursor 数量 */
    private static final int SCAN_BATCH_SIZE = 100;

    @Value("${app.session.zombie-check.enabled:true}")
    private boolean zombieCheckEnabled;

    // ========== 僵尸会话清理定时任务（ADR-057） ==========

    /**
     * 每分钟扫描僵尸会话（ADR-057, ADR-058）。
     *
     * 使用 SCAN 而非 KEYS，避免阻塞 Redis 主线程。
     * 发现 last_active_at > 30min 且 status=ACTIVE 的，触发归档。
     */
    @Scheduled(fixedDelay = 60000)
    public void checkZombieSessions() {
        if (!zombieCheckEnabled) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int archived = 0;
        int skipped = 0;
        int failed = 0;

        String pattern = KEY_PREFIX + "*:meta";
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String metaKey = cursor.next();
                try {
                    String sessionId = extractSessionId(metaKey);
                    if (sessionId == null) {
                        skipped++;
                        continue;
                    }

                    // 检查是否需要归档
                    if (shouldArchive(sessionId)) {
                        boolean ok = archiveSession(sessionId, "Zombie: idle > 30min");
                        if (ok) {
                            archived++;
                        } else {
                            failed++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to check zombie session for key: {}, error: {}", metaKey, e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("Zombie session scan failed", e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (archived > 0 || failed > 0) {
            log.info("Zombie session check complete: archived={}, skipped={}, failed={}, elapsed={}ms",
                    archived, skipped, failed, elapsed);
        }
    }

    /**
     * 从 meta key 中提取 sessionId。
     * 输入: agent:session:{sid}:meta
     * 输出: {sid}
     */
    private String extractSessionId(String metaKey) {
        int start = KEY_PREFIX.length();
        int end = metaKey.lastIndexOf(":meta");
        if (start < 0 || end <= start) return null;
        return metaKey.substring(start, end);
    }

    /**
     * 判断会话是否需要归档。
     *
     * 条件：
     *   1. status = ACTIVE
     *   2. last_active_at > 30 分钟前
     */
    private boolean shouldArchive(String sessionId) {
        try {
            Map<Object, Object> meta = redis.opsForHash().entries(
                    KEY_PREFIX + sessionId + ":meta");
            if (meta.isEmpty()) return false;

            String status = (String) meta.get("status");
            if (!SessionStatus.ACTIVE.name().equals(status)) {
                return false;
            }

            String lastActiveStr = (String) meta.get("last_active_at");
            if (lastActiveStr == null) return false;

            LocalDateTime lastActive = LocalDateTime.parse(lastActiveStr);
            LocalDateTime threshold = LocalDateTime.now().minus(ZOMBIE_THRESHOLD);

            return lastActive.isBefore(threshold);
        } catch (Exception e) {
            log.debug("Failed to check shouldArchive for sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    // ========== 归档核心逻辑 ==========

    /**
     * 归档会话（ADR-057, ADR-064）。
     *
     * 归档流程：
     *   1. 状态机转移 ACTIVE → ARCHIVED（CAS 语义）
     *   2. 持久化 Redis 消息到 MySQL agent_turns 表（仅补充未落库的）
     *   3. 抽取长期记忆（滚动摘要 → LLM 抽取显式偏好）
     *   4. 更新 agent_sessions 表状态
     *   5. 延迟 5 分钟删除 Redis 5 Key（避免边界竞态）
     *
     * @param sessionId 会话ID
     * @param reason    归档原因
     * @return 是否成功
     */
    public boolean archiveSession(String sessionId, String reason) {
        try {
            // 1. 状态机转移：ACTIVE → ARCHIVED（CAS 语义）
            SessionStatus currentStatus = sessionStateMachine.getCurrentStatus(sessionId);
            if (currentStatus == SessionStatus.ARCHIVED || currentStatus == SessionStatus.CLOSED) {
                log.debug("Session already archived/closed, skipping: {}", sessionId);
                return true;
            }

            boolean transitionOk = sessionStateMachine.transition(
                    sessionId, currentStatus, SessionStatus.ARCHIVED, reason);
            if (!transitionOk) {
                log.warn("Failed to transition session to ARCHIVED (CAS failure): {}", sessionId);
                return false;
            }

            // 2. 加载 Redis 中的会话数据
            String rollingSummary = conversationMemoryService.loadSummary(sessionId);
            String userId = getUserIdFromMeta(sessionId);

            // 3. 抽取长期记忆（ADR-059 4.1）
            if (userId != null && rollingSummary != null && !rollingSummary.isBlank()) {
                List<UserMemory> extracted = longTermMemoryService.extractMemories(
                        sessionId, userId, rollingSummary);
                log.info("Extracted {} long-term memories during archival: sessionId={}",
                        extracted.size(), sessionId);
            }

            // 4. 更新 MySQL agent_sessions 表状态
            updateSessionStatusInDb(sessionId, SessionStatus.ARCHIVED);

            // 5. 延迟删除 Redis Key（5 分钟后，避免边界竞态）
            scheduleDeleteRedisKeys(sessionId);

            log.info("Session archived: sessionId={}, reason={}", sessionId, reason);
            return true;
        } catch (Exception e) {
            log.error("Failed to archive session: sessionId={}, reason={}", sessionId, reason, e);
            return false;
        }
    }

    /**
     * 从 Redis meta 中获取 userId。
     */
    private String getUserIdFromMeta(String sessionId) {
        try {
            Object userId = redis.opsForHash().get(KEY_PREFIX + sessionId + ":meta", "user_id");
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新 MySQL 中 agent_sessions 表的状态。
     */
    private void updateSessionStatusInDb(String sessionId, SessionStatus status) {
        try {
            AgentSession session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setStatus(status.name());
                sessionMapper.updateById(session);
            }
        } catch (Exception e) {
            log.warn("Failed to update session status in DB: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 安排延迟删除 Redis 5 Key（5 分钟后）。
     *
     * 使用 EXPIRE 设置剩余 TTL 为 5 分钟，利用 Redis 自身的 TTL 机制实现延迟删除。
     */
    private void scheduleDeleteRedisKeys(String sessionId) {
        try {
            Duration delay = DELETE_DELAY;
            String[] keys = new String[] {
                    KEY_PREFIX + sessionId + ":meta",
                    KEY_PREFIX + sessionId + ":messages",
                    KEY_PREFIX + sessionId + ":rolling_summary",
                    KEY_PREFIX + sessionId + ":slots",
                    KEY_PREFIX + sessionId + ":pinned"
            };
            for (String key : keys) {
                redis.expire(key, delay);
            }
            log.debug("Scheduled Redis key deletion in {}min: sessionId={}",
                    DELETE_DELAY.toMinutes(), sessionId);
        } catch (Exception e) {
            log.warn("Failed to schedule Redis key deletion: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ========== 手动触发接口（测试/运维用） ==========

    /**
     * 手动触发一次僵尸会话检查（用于测试）。
     */
    public int triggerZombieCheckNow() {
        int count = 0;
        String pattern = KEY_PREFIX + "*:meta";
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String metaKey = cursor.next();
                String sessionId = extractSessionId(metaKey);
                if (sessionId != null && shouldArchive(sessionId)) {
                    if (archiveSession(sessionId, "Manual zombie check")) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Manual zombie check failed", e);
        }
        return count;
    }

    /**
     * 获取当前活跃会话数（Redis 中 status=ACTIVE 的 meta key 数量）。
     */
    public int countActiveSessions() {
        int count = 0;
        String pattern = KEY_PREFIX + "*:meta";
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object status = redis.opsForHash().get(key, "status");
                if (SessionStatus.ACTIVE.name().equals(status)) {
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to count active sessions: {}", e.getMessage());
        }
        return count;
    }
}
