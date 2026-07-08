package com.campushare.agent.service;

import com.campushare.agent.dto.MemoryMessage;
import com.campushare.agent.entity.AgentTurn;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConversationMemoryService 单元测试。
 *
 * 验证 Redis 5 Key 短期记忆服务的读写逻辑、配对逻辑、压缩触发判断和降级策略。
 *
 * Mock 策略：mock StringRedisTemplate 的 opsForHash/opsForList/opsForValue/expire，
 * 不依赖真实 Redis。
 */
@DisplayName("ConversationMemoryService 短期记忆服务测试")
@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    private ConversationMemoryService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SESSION_ID = "test-session-123";

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().when(redis.opsForList()).thenReturn(listOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new ConversationMemoryService(redis, objectMapper);
    }

    // ========== 1. 初始化 ==========

    @Nested
    @DisplayName("初始化会话记忆")
    class InitSession {

        @Test
        @DisplayName("initSession 写入 meta Hash 并设置 TTL")
        void initSession_writesMetaAndTTL() {
            service.initSession(SESSION_ID, "user-1", "v1.0.0", "deepseek-v4");

            verify(hashOps).putAll(eq("agent:session:" + SESSION_ID + ":meta"), any());
            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":meta"), any());
        }

        @Test
        @DisplayName("initSession Redis 故障时不抛异常（降级）")
        void initSession_redisFailure_noException() {
            org.mockito.Mockito.doThrow(new RuntimeException("Redis down"))
                    .when(hashOps).putAll(anyString(), any());

            service.initSession(SESSION_ID, "user-1", null, null);
            // 不抛异常即通过
        }
    }

    // ========== 2. 读操作 ==========

    @Nested
    @DisplayName("读取短期记忆")
    class LoadOperations {

        @Test
        @DisplayName("loadMeta 返回 meta Hash 内容")
        void loadMeta_returnsHashContent() {
            Map<Object, Object> raw = new HashMap<>();
            raw.put("user_id", "user-1");
            raw.put("turn_count", "3");
            when(hashOps.entries("agent:session:" + SESSION_ID + ":meta")).thenReturn(raw);

            Map<String, String> meta = service.loadMeta(SESSION_ID);

            assertThat(meta).containsEntry("user_id", "user-1").containsEntry("turn_count", "3");
        }

        @Test
        @DisplayName("loadMeta Redis 故障返回空 map")
        void loadMeta_redisFailure_returnsEmpty() {
            when(hashOps.entries(anyString())).thenThrow(new RuntimeException("Redis down"));

            Map<String, String> meta = service.loadMeta(SESSION_ID);

            assertThat(meta).isEmpty();
        }

        @Test
        @DisplayName("loadSummary 返回摘要文本")
        void loadSummary_returnsText() {
            when(valueOps.get("agent:session:" + SESSION_ID + ":rolling_summary"))
                    .thenReturn("用户在求操作系统卷子");

            String summary = service.loadSummary(SESSION_ID);

            assertThat(summary).isEqualTo("用户在求操作系统卷子");
        }

        @Test
        @DisplayName("loadSlots 返回槽位 Hash")
        void loadSlots_returnsSlots() {
            Map<Object, Object> raw = new HashMap<>();
            raw.put("confirmed_intent", "SEARCH");
            raw.put("target_category", "软件");
            when(hashOps.entries("agent:session:" + SESSION_ID + ":slots")).thenReturn(raw);

            Map<String, String> slots = service.loadSlots(SESSION_ID);

            assertThat(slots).containsEntry("confirmed_intent", "SEARCH").containsEntry("target_category", "软件");
        }
    }

    // ========== 3. loadHistoryAsTurns 配对逻辑 ==========

    @Nested
    @DisplayName("历史消息配对为 AgentTurn")
    class HistoryPairing {

        @Test
        @DisplayName("空消息列表返回空 turns")
        void loadHistoryAsTurns_empty_returnsEmpty() {
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(new ArrayList<>());

            List<AgentTurn> turns = service.loadHistoryAsTurns(SESSION_ID);

            assertThat(turns).isEmpty();
        }

        @Test
        @DisplayName("user+assistant 正确配对为 AgentTurn")
        void loadHistoryAsTurns_pairedCorrectly() throws Exception {
            List<String> jsonList = new ArrayList<>();
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(1).role("user").content("求卷子").ts(1000).build()));
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(1).role("assistant").content("这是操作系统卷子").ts(1001).build()));
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(2).role("user").content("谢谢").ts(2000).build()));
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(2).role("assistant").content("不客气").ts(2001).build()));
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(jsonList);

            List<AgentTurn> turns = service.loadHistoryAsTurns(SESSION_ID);

            assertThat(turns).hasSize(2);
            assertThat(turns.get(0).getUserMessage()).isEqualTo("求卷子");
            assertThat(turns.get(0).getAssistantMessage()).isEqualTo("这是操作系统卷子");
            assertThat(turns.get(1).getUserMessage()).isEqualTo("谢谢");
            assertThat(turns.get(1).getAssistantMessage()).isEqualTo("不客气");
        }

        @Test
        @DisplayName("未配对的 user 单独成 turn（assistant=null）")
        void loadHistoryAsTurns_unpairedUser() throws Exception {
            List<String> jsonList = new ArrayList<>();
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(1).role("user").content("求卷子").ts(1000).build()));
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(jsonList);

            List<AgentTurn> turns = service.loadHistoryAsTurns(SESSION_ID);

            assertThat(turns).hasSize(1);
            assertThat(turns.get(0).getUserMessage()).isEqualTo("求卷子");
            assertThat(turns.get(0).getAssistantMessage()).isNull();
        }

        @Test
        @DisplayName("连续两个 user 消息：第一个 user 单独成 turn")
        void loadHistoryAsTurns_consecutiveUsers() throws Exception {
            List<String> jsonList = new ArrayList<>();
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(1).role("user").content("求卷子").ts(1000).build()));
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(2).role("user").content("再求一个").ts(2000).build()));
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(2).role("assistant").content("好的").ts(2001).build()));
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(jsonList);

            List<AgentTurn> turns = service.loadHistoryAsTurns(SESSION_ID);

            assertThat(turns).hasSize(2);
            assertThat(turns.get(0).getUserMessage()).isEqualTo("求卷子");
            assertThat(turns.get(0).getAssistantMessage()).isNull();
            assertThat(turns.get(1).getUserMessage()).isEqualTo("再求一个");
            assertThat(turns.get(1).getAssistantMessage()).isEqualTo("好的");
        }
    }

    // ========== 4. appendMessage ==========

    @Nested
    @DisplayName("追加消息")
    class AppendMessage {

        @Test
        @DisplayName("appendMessage RPUSH 并返回当前长度")
        void appendMessage_rpushReturnsLength() {
            MemoryMessage msg = MemoryMessage.builder()
                    .turnId(1).role("user").content("test").ts(1000).build();
            when(listOps.rightPush(anyString(), anyString())).thenReturn(3L);

            int count = service.appendMessage(SESSION_ID, msg);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("appendMessage 超过 MAX_MESSAGES(20) 时自动 LTRIM")
        void appendMessage_exceedsMax_triggersTrim() {
            MemoryMessage msg = MemoryMessage.builder()
                    .turnId(1).role("user").content("test").ts(1000).build();
            when(listOps.rightPush(anyString(), anyString())).thenReturn(21L);

            int count = service.appendMessage(SESSION_ID, msg);

            assertThat(count).isEqualTo(20);
            verify(listOps).trim(anyString(), eq(-20L), eq(-1L));
        }

        @Test
        @DisplayName("appendMessage Redis 故障返回 0")
        void appendMessage_redisFailure_returnsZero() {
            MemoryMessage msg = MemoryMessage.builder()
                    .turnId(1).role("user").content("test").ts(1000).build();
            when(listOps.rightPush(anyString(), anyString())).thenThrow(new RuntimeException("Redis down"));

            int count = service.appendMessage(SESSION_ID, msg);

            assertThat(count).isEqualTo(0);
        }
    }

    // ========== 5. 压缩相关 ==========

    @Nested
    @DisplayName("压缩触发判断")
    class CompressionCheck {

        @Test
        @DisplayName("needsCompression 长度 > 10 返回 true")
        void needsCompression_aboveThreshold_returnsTrue() {
            when(listOps.size("agent:session:" + SESSION_ID + ":messages")).thenReturn(11L);

            assertThat(service.needsCompression(SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("needsCompression 长度 <= 10 返回 false")
        void needsCompression_atThreshold_returnsFalse() {
            when(listOps.size("agent:session:" + SESSION_ID + ":messages")).thenReturn(10L);

            assertThat(service.needsCompression(SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("needsCompression Redis 故障返回 false")
        void needsCompression_redisFailure_returnsFalse() {
            when(listOps.size(anyString())).thenThrow(new RuntimeException("Redis down"));

            assertThat(service.needsCompression(SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("getMessagesToCompress 返回超出 keepCount 的旧消息")
        void getMessagesToCompress_returnsOldMessages() throws Exception {
            List<String> jsonList = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                        .turnId(i).role("user").content("msg-" + i).ts(1000 + i).build()));
            }
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(jsonList);

            List<MemoryMessage> toCompress = service.getMessagesToCompress(SESSION_ID, 5);

            assertThat(toCompress).hasSize(3);
            assertThat(toCompress.get(0).getContent()).isEqualTo("msg-1");
            assertThat(toCompress.get(2).getContent()).isEqualTo("msg-3");
        }

        @Test
        @DisplayName("getMessagesToCompress 消息数 <= keepCount 返回空")
        void getMessagesToCompress_withinKeepCount_returnsEmpty() throws Exception {
            List<String> jsonList = new ArrayList<>();
            jsonList.add(objectMapper.writeValueAsString(MemoryMessage.builder()
                    .turnId(1).role("user").content("msg-1").ts(1000).build()));
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(jsonList);

            List<MemoryMessage> toCompress = service.getMessagesToCompress(SESSION_ID, 5);

            assertThat(toCompress).isEmpty();
        }
    }

    // ========== 6. TTL 管理 ==========

    @Nested
    @DisplayName("TTL 管理")
    class TTLManagement {

        @Test
        @DisplayName("renewTTL 对 5 个 Key 都调用 expire")
        void renewTTL_expiresAll5Keys() {
            service.renewTTL(SESSION_ID);

            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":meta"), any());
            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":messages"), any());
            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":rolling_summary"), any());
            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":slots"), any());
            verify(redis).expire(eq("agent:session:" + SESSION_ID + ":pinned"), any());
        }

        @Test
        @DisplayName("renewTTL Redis 故障不抛异常")
        void renewTTL_redisFailure_noException() {
            when(redis.expire(anyString(), any())).thenThrow(new RuntimeException("Redis down"));

            service.renewTTL(SESSION_ID);
            // 不抛异常即通过
        }
    }

    // ========== 7. ShortTermMemory record ==========

    @Nested
    @DisplayName("ShortTermMemory DTO")
    class ShortTermMemoryTest {

        @Test
        @DisplayName("turnCount 解析 meta 中的 turn_count 字段")
        void turnCount_parsesFromMeta() {
            Map<String, String> meta = new HashMap<>();
            meta.put("turn_count", "5");
            ConversationMemoryService.ShortTermMemory stm =
                    new ConversationMemoryService.ShortTermMemory(meta, null, null, null, null);

            assertThat(stm.turnCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("turnCount meta 无 turn_count 字段返回 0")
        void turnCount_missingField_returns0() {
            Map<String, String> meta = new HashMap<>();
            ConversationMemoryService.ShortTermMemory stm =
                    new ConversationMemoryService.ShortTermMemory(meta, null, null, null, null);

            assertThat(stm.turnCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("currentIntent 返回 meta 中的 current_intent")
        void currentIntent_returnsFromMeta() {
            Map<String, String> meta = new HashMap<>();
            meta.put("current_intent", "SEARCH");
            ConversationMemoryService.ShortTermMemory stm =
                    new ConversationMemoryService.ShortTermMemory(meta, null, null, null, null);

            assertThat(stm.currentIntent()).isEqualTo("SEARCH");
        }

        @Test
        @DisplayName("isEmpty meta 为空时返回 true")
        void isEmpty_emptyMeta_returnsTrue() {
            ConversationMemoryService.ShortTermMemory stm =
                    new ConversationMemoryService.ShortTermMemory(new HashMap<>(), null, null, null, null);

            assertThat(stm.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty meta 非空时返回 false")
        void isEmpty_nonEmptyMeta_returnsFalse() {
            Map<String, String> meta = new HashMap<>();
            meta.put("user_id", "u1");
            ConversationMemoryService.ShortTermMemory stm =
                    new ConversationMemoryService.ShortTermMemory(meta, null, null, null, null);

            assertThat(stm.isEmpty()).isFalse();
        }
    }
}
