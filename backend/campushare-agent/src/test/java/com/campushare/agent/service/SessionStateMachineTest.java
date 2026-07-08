package com.campushare.agent.service;

import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.enums.SessionStatus;
import com.campushare.agent.mapper.AgentSessionEventMapper;
import com.campushare.agent.mapper.AgentSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionStateMachine 单元测试（ADR-064~069）。
 *
 * 验证 8 状态机核心逻辑：
 *   - canTransition：合法/非法转移矩阵、幂等、任何→ERROR
 *   - transition：CAS 语义（Redis 状态校验）、DB 更新、事件记录
 *   - setStatus：直接设置（不验证 CAS）
 *   - getCurrentStatus：Redis 优先、DB 降级
 *
 * Mock 策略：mock StringRedisTemplate 的 opsForHash + AgentSessionMapper + AgentSessionEventMapper，
 * 不依赖真实 Redis 和 DB。
 */
@DisplayName("SessionStateMachine 会话状态机测试")
@ExtendWith(MockitoExtension.class)
class SessionStateMachineTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private AgentSessionMapper sessionMapper;
    @Mock
    private AgentSessionEventMapper eventMapper;

    private SessionStateMachine machine;

    private static final String SESSION_ID = "session-001";
    private static final String META_KEY = "agent:session:" + SESSION_ID + ":meta";

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        machine = new SessionStateMachine(redis, sessionMapper, eventMapper);
    }

    // ========== 1. canTransition 转移矩阵 ==========

    @Nested
    @DisplayName("canTransition 转移合法性")
    class CanTransition {

        @Test
        @DisplayName("INIT → ACTIVE 合法")
        void initToActive_legal() {
            assertThat(machine.canTransition(SessionStatus.INIT, SessionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → TOOL_CALLING 合法")
        void activeToToolCalling_legal() {
            assertThat(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.TOOL_CALLING)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → WAITING_CLARIFY 合法")
        void activeToWaitingClarify_legal() {
            assertThat(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.WAITING_CLARIFY)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → REFLECTING 合法")
        void activeToReflecting_legal() {
            assertThat(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.REFLECTING)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → ARCHIVED 合法")
        void activeToArchived_legal() {
            assertThat(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → CLOSED 合法")
        void activeToClosed_legal() {
            assertThat(machine.canTransition(SessionStatus.ACTIVE, SessionStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("TOOL_CALLING → ACTIVE 合法")
        void toolCallingToActive_legal() {
            assertThat(machine.canTransition(SessionStatus.TOOL_CALLING, SessionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("WAITING_CLARIFY → ACTIVE 合法")
        void waitingClarifyToActive_legal() {
            assertThat(machine.canTransition(SessionStatus.WAITING_CLARIFY, SessionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("REFLECTING → ACTIVE 合法")
        void reflectingToActive_legal() {
            assertThat(machine.canTransition(SessionStatus.REFLECTING, SessionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ARCHIVED → ACTIVE 合法（重新打开）")
        void archivedToActive_legal() {
            assertThat(machine.canTransition(SessionStatus.ARCHIVED, SessionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ERROR → INIT 合法（恢复需新建会话）")
        void errorToInit_legal() {
            assertThat(machine.canTransition(SessionStatus.ERROR, SessionStatus.INIT)).isTrue();
        }

        @Test
        @DisplayName("任何状态 → ERROR 合法（全局规则）")
        void anyToError_legal() {
            for (SessionStatus from : SessionStatus.values()) {
                assertThat(machine.canTransition(from, SessionStatus.ERROR))
                        .as("从 %s 到 ERROR 应该是合法的", from)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("同状态转移合法（幂等）")
        void sameState_legal() {
            for (SessionStatus s : SessionStatus.values()) {
                assertThat(machine.canTransition(s, s))
                        .as("从 %s 到 %s 应该是合法的（幂等）", s, s)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("INIT → TOOL_CALLING 非法（必须先经 ACTIVE）")
        void initToToolCalling_illegal() {
            assertThat(machine.canTransition(SessionStatus.INIT, SessionStatus.TOOL_CALLING)).isFalse();
        }

        @Test
        @DisplayName("TOOL_CALLING → ARCHIVED 非法（必须先回 ACTIVE）")
        void toolCallingToArchived_illegal() {
            assertThat(machine.canTransition(SessionStatus.TOOL_CALLING, SessionStatus.ARCHIVED)).isFalse();
        }

        @Test
        @DisplayName("WAITING_CLARIFY → CLOSED 非法")
        void waitingClarifyToClosed_illegal() {
            assertThat(machine.canTransition(SessionStatus.WAITING_CLARIFY, SessionStatus.CLOSED)).isFalse();
        }

        @Test
        @DisplayName("CLOSED → ACTIVE 非法（终态）")
        void closedToActive_illegal() {
            assertThat(machine.canTransition(SessionStatus.CLOSED, SessionStatus.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("CLOSED → ARCHIVED 非法（终态）")
        void closedToArchived_illegal() {
            assertThat(machine.canTransition(SessionStatus.CLOSED, SessionStatus.ARCHIVED)).isFalse();
        }

        @Test
        @DisplayName("ERROR → ACTIVE 非法（恢复需新建会话走 INIT）")
        void errorToActive_illegal() {
            assertThat(machine.canTransition(SessionStatus.ERROR, SessionStatus.ACTIVE)).isFalse();
        }
    }

    // ========== 2. transition CAS 语义 ==========

    @Nested
    @DisplayName("transition 状态转移执行")
    class Transition {

        @Test
        @DisplayName("CAS 成功：Redis 状态匹配 from，执行转移")
        void transition_casSuccess_updatesRedisAndDb() {
            AgentSession session = buildSession(SESSION_ID, "INIT");
            when(hashOps.get(META_KEY, "status")).thenReturn("INIT");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.ACTIVE, "First message");

            assertThat(result).isTrue();
            verify(hashOps).put(META_KEY, "status", "ACTIVE");
            verify(sessionMapper).updateById(any(AgentSession.class));
            verify(eventMapper).insert(any());
            assertThat(session.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("CAS 失败：Redis 状态不匹配 from，拒绝转移")
        void transition_casFailed_rejected() {
            when(hashOps.get(META_KEY, "status")).thenReturn("ACTIVE"); // 实际已是 ACTIVE

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.ACTIVE, "First message");

            assertThat(result).isFalse();
            verify(hashOps, org.mockito.Mockito.never())
                    .put(anyString(), anyString(), anyString());
            verify(eventMapper, org.mockito.Mockito.never()).insert(any());
        }

        @Test
        @DisplayName("Redis 无状态时跳过 CAS，直接执行转移")
        void transition_redisNoStatus_skipsCas() {
            AgentSession session = buildSession(SESSION_ID, "INIT");
            when(hashOps.get(META_KEY, "status")).thenReturn(null);
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.ACTIVE, "Recovery");

            assertThat(result).isTrue();
            verify(hashOps).put(META_KEY, "status", "ACTIVE");
        }

        @Test
        @DisplayName("非法转移被拒绝")
        void transition_illegalTransition_rejected() {
            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.TOOL_CALLING, "Illegal");

            assertThat(result).isFalse();
            verify(hashOps, org.mockito.Mockito.never())
                    .put(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("幂等转移（同状态）成功执行")
        void transition_idempotent_success() {
            AgentSession session = buildSession(SESSION_ID, "ACTIVE");
            when(hashOps.get(META_KEY, "status")).thenReturn("ACTIVE");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.ACTIVE, SessionStatus.ACTIVE, "Idempotent");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("任何状态 → ERROR 成功")
        void transition_anyToError_success() {
            AgentSession session = buildSession(SESSION_ID, "ACTIVE");
            when(hashOps.get(META_KEY, "status")).thenReturn("ACTIVE");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.ACTIVE, SessionStatus.ERROR, "Fatal error");

            assertThat(result).isTrue();
            verify(hashOps).put(META_KEY, "status", "ERROR");
        }

        @Test
        @DisplayName("Redis 异常时返回 false")
        void transition_redisException_returnsFalse() {
            when(hashOps.get(anyString(), anyString())).thenThrow(new RuntimeException("Redis down"));

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.ACTIVE, "Test");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("DB session 不存在时仍更新 Redis 并记录事件")
        void transition_sessionNotFound_updatesRedisOnly() {
            when(hashOps.get(META_KEY, "status")).thenReturn("INIT");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

            boolean result = machine.transition(SESSION_ID,
                    SessionStatus.INIT, SessionStatus.ACTIVE, "Test");

            assertThat(result).isTrue();
            verify(hashOps).put(META_KEY, "status", "ACTIVE");
            verify(eventMapper).insert(any());
            verify(sessionMapper, org.mockito.Mockito.never()).updateById(any());
        }
    }

    // ========== 3. setStatus ==========

    @Nested
    @DisplayName("setStatus 直接设置状态")
    class SetStatus {

        @Test
        @DisplayName("setStatus 更新 Redis 和 DB（不验证 CAS）")
        void setStatus_updatesRedisAndDb() {
            AgentSession session = buildSession(SESSION_ID, "INIT");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            machine.setStatus(SESSION_ID, SessionStatus.ACTIVE, "Init");

            verify(hashOps).put(META_KEY, "status", "ACTIVE");
            verify(sessionMapper).updateById(any(AgentSession.class));
            verify(eventMapper).insert(any());
            assertThat(session.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("setStatus DB 无 session 仍更新 Redis")
        void setStatus_sessionNotFound_updatesRedisOnly() {
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

            machine.setStatus(SESSION_ID, SessionStatus.ERROR, "Recovery");

            verify(hashOps).put(META_KEY, "status", "ERROR");
            verify(eventMapper).insert(any());
        }

        @Test
        @DisplayName("setStatus Redis 异常不抛出")
        void setStatus_redisException_noThrow() {
            org.mockito.Mockito.doThrow(new RuntimeException("Redis down"))
                    .when(hashOps).put(anyString(), anyString(), anyString());

            machine.setStatus(SESSION_ID, SessionStatus.ACTIVE, "Test");
            // 不抛异常即通过
        }
    }

    // ========== 4. getCurrentStatus ==========

    @Nested
    @DisplayName("getCurrentStatus 状态查询")
    class GetCurrentStatus {

        @Test
        @DisplayName("Redis 有状态时返回 Redis 状态")
        void getCurrentStatus_redisHit_returnsRedisStatus() {
            when(hashOps.get(META_KEY, "status")).thenReturn("ACTIVE");

            SessionStatus status = machine.getCurrentStatus(SESSION_ID);

            assertThat(status).isEqualTo(SessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("Redis 无状态时降级到 DB 查询")
        void getCurrentStatus_redisMiss_fallsBackToDb() {
            when(hashOps.get(META_KEY, "status")).thenReturn(null);
            AgentSession session = buildSession(SESSION_ID, "ARCHIVED");
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            SessionStatus status = machine.getCurrentStatus(SESSION_ID);

            assertThat(status).isEqualTo(SessionStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Redis 和 DB 均无状态返回 null")
        void getCurrentStatus_bothNull_returnsNull() {
            when(hashOps.get(META_KEY, "status")).thenReturn(null);
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

            SessionStatus status = machine.getCurrentStatus(SESSION_ID);

            assertThat(status).isNull();
        }

        @Test
        @DisplayName("Redis 异常时降级返回 null（外层 catch 兜底）")
        void getCurrentStatus_redisException_returnsNull() {
            when(hashOps.get(anyString(), anyString())).thenThrow(new RuntimeException("Redis down"));

            SessionStatus status = machine.getCurrentStatus(SESSION_ID);

            assertThat(status).isNull();
        }

        @Test
        @DisplayName("DB session 状态为 null 时返回 null")
        void getCurrentStatus_dbStatusNull_returnsNull() {
            when(hashOps.get(META_KEY, "status")).thenReturn(null);
            AgentSession session = buildSession(SESSION_ID, null);
            when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

            SessionStatus status = machine.getCurrentStatus(SESSION_ID);

            assertThat(status).isNull();
        }
    }

    // ========== 辅助方法 ==========

    private AgentSession buildSession(String id, String status) {
        return AgentSession.builder()
                .id(id)
                .userId("user-001")
                .status(status)
                .build();
    }
}
