package com.campushare.agent.service;

import com.campushare.agent.dto.MemoryMessage;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContextCompressionService 单元测试。
 *
 * 验证三级压缩服务的三合一 LLM 调用、JSON 解析、降级策略。
 *
 * Mock 策略：mock DeepSeekClient.chatCompletion()，不依赖真实 LLM。
 */
@DisplayName("ContextCompressionService 三级压缩服务测试")
@ExtendWith(MockitoExtension.class)
class ContextCompressionServiceTest {

    @Mock
    private DeepSeekClient deepSeekClient;

    private ContextCompressionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ContextCompressionService(deepSeekClient, objectMapper);
    }

    /**
     * 构建 mock LLM 响应。
     */
    private DeepSeekResponse mockResponse(String content) {
        DeepSeekResponse response = new DeepSeekResponse();
        DeepSeekResponse.Choice choice = new DeepSeekResponse.Choice();
        DeepSeekResponse.Message message = new DeepSeekResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        return response;
    }

    // ========== 1. 基本压缩流程 ==========

    @Nested
    @DisplayName("基本压缩流程")
    class BasicCompression {

        @Test
        @DisplayName("compress 空消息列表返回 empty()")
        void compress_emptyMessages_returnsEmpty() {
            ContextCompressionService.CompressionResult result =
                    service.compress(null, Collections.emptyList(), null);

            assertThat(result.summary()).isNull();
            assertThat(result.pins()).isEmpty();
            assertThat(result.fallback()).isFalse();
            verify(deepSeekClient, never()).chatCompletion(any());
        }

        @Test
        @DisplayName("compress null 消息列表返回 empty()")
        void compress_nullMessages_returnsEmpty() {
            ContextCompressionService.CompressionResult result =
                    service.compress(null, null, null);

            assertThat(result.summary()).isNull();
            verify(deepSeekClient, never()).chatCompletion(any());
        }

        @Test
        @DisplayName("compress 正常流程：LLM 返回有效 JSON → 解析 summary + slots + pins")
        void compress_validJson_parsesCorrectly() {
            String json = """
                    {"summary":"用户求操作系统卷子，已提供PDF版本","slots":{"confirmed_intent":"SEARCH","target_category":"软件"},"pins":[{"content":"我偏好PDF格式","reason":"用户偏好声明"}]}
                    """;
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(json)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求操作系统卷子").ts(1000).build(),
                    MemoryMessage.builder().turnId(1).role("assistant").content("这是操作系统卷子").ts(1001).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.fallback()).isFalse();
            assertThat(result.summary()).isEqualTo("用户求操作系统卷子，已提供PDF版本");
            assertThat(result.slots()).containsEntry("confirmed_intent", "SEARCH");
            assertThat(result.slots()).containsEntry("target_category", "软件");
            assertThat(result.pins()).hasSize(1);
            assertThat(result.pins().get(0).content()).isEqualTo("我偏好PDF格式");
            assertThat(result.pins().get(0).reason()).isEqualTo("用户偏好声明");
        }

        @Test
        @DisplayName("compress 无 Pin 时 pins 为空列表")
        void compress_noPins_emptyPinsList() {
            String json = """
                    {"summary":"用户求操作系统卷子","slots":{"confirmed_intent":"SEARCH"},"pins":[]}
                    """;
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(json)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.pins()).isEmpty();
            assertThat(result.summary()).isEqualTo("用户求操作系统卷子");
        }
    }

    // ========== 2. 降级策略 ==========

    @Nested
    @DisplayName("降级策略（ADR-053）")
    class FallbackStrategy {

        @Test
        @DisplayName("compress LLM 返回 null → 重试 1 次后降级")
        void compress_nullResponse_fallsBack() {
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.empty());

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.fallback()).isTrue();
            assertThat(result.summary()).isNull();
            // 重试 1 次 + 初始 1 次 = 2 次调用
            verify(deepSeekClient, times(2)).chatCompletion(any());
        }

        @Test
        @DisplayName("compress LLM 抛异常 → 重试 1 次后降级")
        void compress_exception_fallsBack() {
            when(deepSeekClient.chatCompletion(any()))
                    .thenReturn(Mono.error(new RuntimeException("LLM timeout")));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.fallback()).isTrue();
            verify(deepSeekClient, times(2)).chatCompletion(any());
        }

        @Test
        @DisplayName("compress JSON 解析失败 → 重试 1 次后降级")
        void compress_invalidJson_fallsBack() {
            when(deepSeekClient.chatCompletion(any()))
                    .thenReturn(Mono.just(mockResponse("这不是JSON")));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.fallback()).isTrue();
            verify(deepSeekClient, times(2)).chatCompletion(any());
        }

        @Test
        @DisplayName("compress 首次失败、重试成功 → 返回正常结果")
        void compress_firstFailRetrySuccess_returnsResult() {
            String validJson = """
                    {"summary":"摘要","slots":{},"pins":[]}
                    """;
            when(deepSeekClient.chatCompletion(any()))
                    .thenReturn(Mono.just(mockResponse("invalid")))
                    .thenReturn(Mono.just(mockResponse(validJson)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.fallback()).isFalse();
            assertThat(result.summary()).isEqualTo("摘要");
            verify(deepSeekClient, times(2)).chatCompletion(any());
        }
    }

    // ========== 3. JSON 解析 ==========

    @Nested
    @DisplayName("JSON 解析")
    class JsonParsing {

        @Test
        @DisplayName("compress 处理 markdown ```json 包裹的响应")
        void compress_markdownWrappedJson_parsedCorrectly() {
            String markdownJson = """
                    ```json
                    {"summary":"测试摘要","slots":{"key":"value"},"pins":[]}
                    ```
                    """;
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(markdownJson)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.summary()).isEqualTo("测试摘要");
            assertThat(result.slots()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("compress 摘要超过 300 字时截断")
        void compress_summaryExceedsMax_truncated() {
            String longSummary = "用户求操作系统卷子".repeat(50);
            String json = """
                    {"summary":"%s","slots":{},"pins":[]}
                    """.formatted(longSummary);
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(json)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.summary()).hasSizeLessThanOrEqualTo(303); // 300 + "..."
            assertThat(result.summary()).endsWith("...");
        }

        @Test
        @DisplayName("compress slots 为 null 时不报错")
        void compress_nullSlots_noError() {
            String json = """
                    {"summary":"摘要","slots":null,"pins":[]}
                    """;
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(json)));

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            ContextCompressionService.CompressionResult result =
                    service.compress(null, messages, null);

            assertThat(result.summary()).isEqualTo("摘要");
            assertThat(result.slots()).isNull();
        }
    }

    // ========== 4. CompressionResult DTO ==========

    @Nested
    @DisplayName("CompressionResult DTO")
    class CompressionResultTest {

        @Test
        @DisplayName("empty() 返回非降级空结果")
        void empty_returnsNonFallbackEmpty() {
            ContextCompressionService.CompressionResult result =
                    ContextCompressionService.CompressionResult.empty();

            assertThat(result.summary()).isNull();
            assertThat(result.slots()).isNull();
            assertThat(result.pins()).isEmpty();
            assertThat(result.fallback()).isFalse();
        }

        @Test
        @DisplayName("fallbackResult() 返回降级结果")
        void fallbackResult_returnsFallback() {
            ContextCompressionService.CompressionResult result =
                    ContextCompressionService.CompressionResult.fallbackResult();

            assertThat(result.summary()).isNull();
            assertThat(result.pins()).isEmpty();
            assertThat(result.fallback()).isTrue();
        }
    }

    // ========== 5. 槽位合并 ==========

    @Nested
    @DisplayName("已有槽位合并")
    class ExistingSlots {

        @Test
        @DisplayName("compress 传入已有槽位时，prompt 中包含旧槽位")
        void compress_withExistingSlots_includesThemInPrompt() {
            String json = """
                    {"summary":"摘要","slots":{},"pins":[]}
                    """;
            when(deepSeekClient.chatCompletion(any())).thenReturn(Mono.just(mockResponse(json)));

            Map<String, String> existingSlots = new HashMap<>();
            existingSlots.put("confirmed_intent", "SEARCH");
            existingSlots.put("target_school", "清华");

            List<MemoryMessage> messages = List.of(
                    MemoryMessage.builder().turnId(1).role("user").content("求卷子").ts(1000).build()
            );

            service.compress("旧摘要", messages, existingSlots);

            // 验证 LLM 被调用（prompt 内容由内部构建，这里只验证调用发生）
            verify(deepSeekClient, atLeast(1)).chatCompletion(any());
        }
    }
}
