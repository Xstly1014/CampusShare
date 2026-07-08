package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.UserMemoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LongTermMemoryService 单元测试（ADR-059~063）。
 *
 * 验证长期用户画像服务的核心逻辑：
 *   - loadUserProfile：Top-K 装载、相关性排序、画像格式化
 *   - extractMemories：LLM 抽取、JSON 解析、降级策略
 *   - upsertMemory：已存在 confidence 累加、新增插入
 *
 * Mock 策略：mock UserMemoryMapper（MyBatis Plus）+ DeepSeekClient（LLM），
 * 不依赖真实 DB 和 LLM。
 */
@DisplayName("LongTermMemoryService 长期记忆服务测试")
@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceTest {

    @Mock
    private UserMemoryMapper userMemoryMapper;
    @Mock
    private DeepSeekClient deepSeekClient;

    private LongTermMemoryService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USER_ID = "user-001";
    private static final String SESSION_ID = "session-001";

    @BeforeEach
    void setUp() {
        service = new LongTermMemoryService(userMemoryMapper, deepSeekClient, objectMapper);
    }

    // ========== 1. loadUserProfile ==========

    @Nested
    @DisplayName("loadUserProfile 用户画像装载")
    class LoadUserProfile {

        @Test
        @DisplayName("无记忆时返回 null")
        void loadUserProfile_noMemories_returnsNull() {
            when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());

            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), new HashMap<>());

            assertThat(profile).isNull();
        }

        @Test
        @DisplayName("单条记忆正确格式化为画像文本")
        void loadUserProfile_singleMemory_formattedCorrectly() {
            UserMemory m = buildMemory(USER_ID, "PREFERENCE", "preferred_format",
                    "PDF", new BigDecimal("1.00"), "EXPLICIT");
            when(userMemoryMapper.selectList(any())).thenReturn(List.of(m));

            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), new HashMap<>());

            assertThat(profile).isNotNull();
            assertThat(profile).contains("[用户画像]");
            assertThat(profile).contains("偏好格式");
            assertThat(profile).contains("PDF");
            assertThat(profile).contains("置信 1.0");
            assertThat(profile).contains("用户明确声明");
        }

        @Test
        @DisplayName("多条记忆取 Top-5 装载")
        void loadUserProfile_multipleMemories_top5Only() {
            List<UserMemory> memories = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                memories.add(buildMemory(USER_ID, "FACT", "key_" + i,
                        "value_" + i, new BigDecimal("0.50"), "INFERRED"));
            }
            when(userMemoryMapper.selectList(any())).thenReturn(memories);

            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), new HashMap<>());

            assertThat(profile).isNotNull();
            // Top-5，每行以 "- " 开头，加上 [用户画像] 标题行
            long lineCount = Arrays.stream(profile.split("\n"))
                    .filter(line -> line.startsWith("- ")).count();
            assertThat(lineCount).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("强相关记忆排在前面（memory_key 匹配意图）")
        void loadUserProfile_relevantMemoryFirst() {
            UserMemory relevant = buildMemory(USER_ID, "PREFERENCE", "search_preference",
                    "操作系统", new BigDecimal("0.60"), "INFERRED");
            UserMemory irrelevant = buildMemory(USER_ID, "FACT", "major",
                    "计算机科学", new BigDecimal("1.00"), "EXPLICIT");
            when(userMemoryMapper.selectList(any())).thenReturn(List.of(irrelevant, relevant));

            Map<String, String> slots = new HashMap<>();
            slots.put("target_category", "操作系统");
            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), slots);

            assertThat(profile).isNotNull();
            // 强相关项应出现在画像前部（relevant 的值"操作系统"应在 irrelevant 的值"计算机科学"之前）
            int relevantIdx = profile.indexOf("操作系统");
            int irrelevantIdx = profile.indexOf("计算机科学");
            assertThat(relevantIdx).isGreaterThan(-1);
            assertThat(irrelevantIdx).isGreaterThan(-1);
            assertThat(relevantIdx).isLessThan(irrelevantIdx);
        }

        @Test
        @DisplayName("DB 异常时降级返回 null")
        void loadUserProfile_dbFailure_returnsNull() {
            when(userMemoryMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));

            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), new HashMap<>());

            assertThat(profile).isNull();
        }

        @Test
        @DisplayName("INFERRED 来源标注为行为推断")
        void loadUserProfile_inferredSource_labeledCorrectly() {
            UserMemory m = buildMemory(USER_ID, "BEHAVIOR", "top_category",
                    "游戏", new BigDecimal("0.80"), "INFERRED");
            when(userMemoryMapper.selectList(any())).thenReturn(List.of(m));

            String profile = service.loadUserProfile(USER_ID,
                    buildIntent(Intent.SEARCH), new HashMap<>());

            assertThat(profile).isNotNull();
            assertThat(profile).contains("行为推断");
            assertThat(profile).doesNotContain("用户明确声明");
        }
    }

    // ========== 2. upsertMemory ==========

    @Nested
    @DisplayName("upsertMemory 记忆 UPSERT")
    class UpsertMemory {

        @Test
        @DisplayName("新增记忆：调用 insert")
        void upsertMemory_notExists_inserts() {
            when(userMemoryMapper.selectOne(any())).thenReturn(null);

            UserMemory result = service.upsertMemory(USER_ID, "PREFERENCE",
                    "preferred_format", "PDF", "EXPLICIT", BigDecimal.ONE, "我喜欢PDF");

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getMemoryType()).isEqualTo("PREFERENCE");
            assertThat(result.getMemoryKey()).isEqualTo("preferred_format");
            assertThat(result.getMemoryValue()).isEqualTo("PDF");
            assertThat(result.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(result.getEvidenceCount()).isEqualTo(1);
            verify(userMemoryMapper).insert(any(UserMemory.class));
        }

        @Test
        @DisplayName("已存在记忆：confidence 累加 0.1（上限 1.0）")
        void upsertMemory_exists_confidenceAccumulated() {
            UserMemory existing = buildMemory(USER_ID, "PREFERENCE", "preferred_format",
                    "DOC", new BigDecimal("0.80"), "EXPLICIT");
            existing.setEvidenceCount(2);
            when(userMemoryMapper.selectOne(any())).thenReturn(existing);

            UserMemory result = service.upsertMemory(USER_ID, "PREFERENCE",
                    "preferred_format", "PDF", "EXPLICIT", BigDecimal.ONE, "我喜欢PDF");

            assertThat(result).isNotNull();
            assertThat(result.getConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
            assertThat(result.getMemoryValue()).isEqualTo("PDF");
            assertThat(result.getEvidenceCount()).isEqualTo(3);
            verify(userMemoryMapper).updateById(existing);
            verify(userMemoryMapper, never()).insert(any());
        }

        @Test
        @DisplayName("已存在记忆 confidence=1.0：累加后不超上限")
        void upsertMemory_existsAtMax_confidenceCapped() {
            UserMemory existing = buildMemory(USER_ID, "PREFERENCE", "preferred_format",
                    "DOC", new BigDecimal("1.00"), "EXPLICIT");
            existing.setEvidenceCount(5);
            when(userMemoryMapper.selectOne(any())).thenReturn(existing);

            UserMemory result = service.upsertMemory(USER_ID, "PREFERENCE",
                    "preferred_format", "PDF", "EXPLICIT", BigDecimal.ONE, "我喜欢PDF");

            assertThat(result).isNotNull();
            assertThat(result.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(result.getEvidenceCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("DB 异常时返回 null")
        void upsertMemory_dbFailure_returnsNull() {
            when(userMemoryMapper.selectOne(any())).thenThrow(new RuntimeException("DB down"));

            UserMemory result = service.upsertMemory(USER_ID, "PREFERENCE",
                    "preferred_format", "PDF", "EXPLICIT", BigDecimal.ONE, "我喜欢PDF");

            assertThat(result).isNull();
        }
    }

    // ========== 3. extractMemories ==========

    @Nested
    @DisplayName("extractMemories LLM 抽取")
    class ExtractMemories {

        @Test
        @DisplayName("空摘要返回空列表")
        void extractMemories_blankSummary_returnsEmpty() {
            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "");

            assertThat(result).isEmpty();
            verify(deepSeekClient, never()).chatCompletion(any());
        }

        @Test
        @DisplayName("null 摘要返回空列表")
        void extractMemories_nullSummary_returnsEmpty() {
            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("LLM 返回有效 JSON 数组：正确解析并 UPSERT")
        void extractMemories_validJson_parsedAndUpserted() {
            String llmContent = "[{\"type\":\"PREFERENCE\",\"key\":\"preferred_format\",\"value\":\"PDF\",\"evidence_quote\":\"我比较喜欢 PDF\"}]";
            mockLlmResponse(llmContent);
            when(userMemoryMapper.selectOne(any())).thenReturn(null);

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "用户问 PDF 相关问题");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemoryType()).isEqualTo("PREFERENCE");
            assertThat(result.get(0).getMemoryKey()).isEqualTo("preferred_format");
            assertThat(result.get(0).getMemoryValue()).isEqualTo("PDF");
            verify(userMemoryMapper).insert(any(UserMemory.class));
        }

        @Test
        @DisplayName("LLM 返回 markdown 包裹的 JSON：正确解析")
        void extractMemories_markdownWrappedJson_parsedCorrectly() {
            String llmContent = "```json\n[{\"type\":\"FACT\",\"key\":\"major\",\"value\":\"计算机\",\"evidence_quote\":\"我是学计算机的\"}]\n```";
            mockLlmResponse(llmContent);
            when(userMemoryMapper.selectOne(any())).thenReturn(null);

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "用户聊专业");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemoryType()).isEqualTo("FACT");
            assertThat(result.get(0).getMemoryKey()).isEqualTo("major");
        }

        @Test
        @DisplayName("LLM 返回空数组 []：不调用 UPSERT")
        void extractMemories_emptyArray_noUpsert() {
            mockLlmResponse("[]");

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "无明确偏好");

            assertThat(result).isEmpty();
            verify(userMemoryMapper, never()).insert(any());
            verify(userMemoryMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("LLM 返回非法 JSON：降级返回空列表")
        void extractMemories_invalidJson_returnsEmpty() {
            mockLlmResponse("这不是 JSON");

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "摘要");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("LLM 返回多条记忆：全部 UPSERT")
        void extractMemories_multipleMemories_allUpserted() {
            String llmContent = "["
                    + "{\"type\":\"PREFERENCE\",\"key\":\"preferred_format\",\"value\":\"PDF\",\"evidence_quote\":\"喜欢PDF\"},"
                    + "{\"type\":\"FACT\",\"key\":\"major\",\"value\":\"计算机\",\"evidence_quote\":\"学计算机\"}"
                    + "]";
            mockLlmResponse(llmContent);
            when(userMemoryMapper.selectOne(any())).thenReturn(null);

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "摘要");

            assertThat(result).hasSize(2);
            verify(userMemoryMapper, times(2)).insert(any(UserMemory.class));
        }

        @Test
        @DisplayName("LLM 调用返回 null：降级返回空列表")
        void extractMemories_nullResponse_returnsEmpty() {
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.empty());

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "摘要");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("LLM 调用抛异常：降级返回空列表")
        void extractMemories_llmException_returnsEmpty() {
            when(deepSeekClient.chatCompletion(any())).thenThrow(new RuntimeException("LLM down"));

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "摘要");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("LLM 返回缺字段的 JSON 项：跳过非法项保留合法项")
        void extractMemories_partialJson_skipsInvalidKeepsValid() {
            String llmContent = "["
                    + "{\"type\":\"PREFERENCE\",\"value\":\"PDF\"}," // 缺 key
                    + "{\"type\":\"FACT\",\"key\":\"major\",\"value\":\"计算机\",\"evidence_quote\":\"学计算机\"}"
                    + "]";
            mockLlmResponse(llmContent);
            when(userMemoryMapper.selectOne(any())).thenReturn(null);

            List<UserMemory> result = service.extractMemories(SESSION_ID, USER_ID, "摘要");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemoryKey()).isEqualTo("major");
        }
    }

    // ========== 辅助方法 ==========

    private UserMemory buildMemory(String userId, String type, String key,
            String value, BigDecimal confidence, String source) {
        return UserMemory.builder()
                .id(System.nanoTime())
                .userId(userId)
                .memoryType(type)
                .memoryKey(key)
                .memoryValue(value)
                .confidence(confidence)
                .source(source)
                .evidenceCount(1)
                .conflictFlag(0)
                .volatileFlag(0)
                .lastUsedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private IntentResult buildIntent(Intent intent) {
        return IntentResult.builder()
                .intent(intent)
                .subIntent("RESOURCE")
                .confidence(0.9)
                .classifyLayer("LLM")
                .build();
    }

    private void mockLlmResponse(String content) {
        DeepSeekResponse response = new DeepSeekResponse();
        DeepSeekResponse.Choice choice = new DeepSeekResponse.Choice();
        DeepSeekResponse.Message message = new DeepSeekResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        lenient().when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(response));
    }
}
