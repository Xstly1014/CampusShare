package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.service.EmbeddingIntentFallback;
import com.campushare.agent.service.IntentCacheService;
import com.campushare.agent.service.IntentClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IntentClassifier 单元测试。
 *
 * 验证点：
 *  - JSON 解析成功 → 返回正确 IntentResult
 *  - 低置信度 (<0.6) → 兜底 SEARCH（ADR-010）
 *  - LLM 失败 → Embedding 兜底（Layer 3）
 *  - 缓存命中 → 跳过 LLM
 *  - 空 query → 兜底 SEARCH
 *  - JSON 解析失败 → 兜底 SEARCH
 *  - Markdown 代码块包裹的 JSON → 正确解析
 */
@DisplayName("IntentClassifier 单元测试")
class IntentClassifierTest {

    private DeepSeekClient deepSeekClient;
    private IntentCacheService cacheService;
    private EmbeddingIntentFallback embeddingFallback;
    private CircuitBreaker circuitBreaker;
    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        deepSeekClient = mock(DeepSeekClient.class);
        cacheService = mock(IntentCacheService.class);
        embeddingFallback = mock(EmbeddingIntentFallback.class);
        ObjectMapper objectMapper = new ObjectMapper();

        // 真实 CircuitBreaker（始终关闭，不触发熔断）
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(100)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        circuitBreaker = CircuitBreaker.of("intent-classifier-test", config);

        classifier = new IntentClassifier(deepSeekClient, cacheService, embeddingFallback,
                objectMapper, circuitBreaker);

        // 默认 stub：缓存未命中
        when(cacheService.get(anyString())).thenReturn(Mono.empty());
        when(cacheService.put(anyString(), any())).thenReturn(Mono.empty());
    }

    /**
     * 构造 mock DeepSeek 响应。
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

    @Test
    @DisplayName("LLM 返回有效 JSON → 解析为 IntentResult")
    void classify_validJson_returnsParsedResult() {
        String json = """
                {"intent":"HOW_TO","sub_intent":"feature_help","confidence":0.9,
                 "rewritten_query":"怎么发帖","slots":{"school":null,"category":null,"post_type":null,"sort":null}}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("怎么发帖", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
                    assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.FEATURE_HELP);
                    assertThat(result.getConfidence()).isEqualTo(0.9);
                    assertThat(result.getRewrittenQuery()).isEqualTo("怎么发帖");
                    assertThat(result.getClassifyLayer()).isEqualTo("LLM");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 返回 Markdown 代码块包裹的 JSON → 正确解析")
    void classify_markdownWrappedJson_parsedCorrectly() {
        String json = "```json\n" +
                "{\"intent\":\"SEARCH\",\"sub_intent\":\"resource\",\"confidence\":0.85," +
                "\"rewritten_query\":\"求操作系统卷子\"}\n```";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("求操作系统卷子", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getConfidence()).isEqualTo(0.85);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("低置信度 (0.3 < 0.6) → 兜底 SEARCH（ADR-010）")
    void classify_lowConfidence_fallbackToSearch() {
        String json = """
                {"intent":"HOW_TO","sub_intent":"feature_help","confidence":0.3,
                 "rewritten_query":"test"}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.RESOURCE);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("高置信度 (0.6) → 保持原意图（边界值）")
    void classify_boundaryConfidence_keepsOriginalIntent() {
        String json = """
                {"intent":"HOW_TO","sub_intent":"feature_help","confidence":0.6,
                 "rewritten_query":"test"}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO))
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 返回无效 JSON → 兜底 SEARCH")
    void classify_invalidJson_fallbackToSearch() {
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse("this is not json")));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getClassifyLayer()).isEqualTo("DEFAULT");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 调用失败 → Embedding 兜底")
    void classify_llmError_fallbackToEmbedding() {
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("LLM timeout")));

        IntentResult embeddingResult = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.7)
                .rewrittenQuery("test")
                .classifyLayer("EMBEDDING")
                .build();
        when(embeddingFallback.classify(anyString())).thenReturn(Mono.just(embeddingResult));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getClassifyLayer()).isEqualTo("EMBEDDING");
                })
                .verifyComplete();

        verify(embeddingFallback).classify("test");
    }

    @Test
    @DisplayName("缓存命中 → 跳过 LLM 调用")
    void classify_cacheHit_skipsLlm() {
        IntentResult cached = IntentResult.builder()
                .intent(Intent.HOW_TO)
                .subIntent(Intent.SubIntent.FEATURE_HELP)
                .confidence(0.9)
                .rewrittenQuery("怎么发帖")
                .classifyLayer("LLM")
                .build();
        when(cacheService.get(anyString())).thenReturn(Mono.just(cached));

        StepVerifier.create(classifier.classify("怎么发帖", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
                    assertThat(result.getConfidence()).isEqualTo(0.9);
                })
                .verifyComplete();

        verify(deepSeekClient, never()).chatCompletion(any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("空 query → 兜底 SEARCH（不调 LLM 和缓存）")
    void classify_nullOrBlank_fallbackToSearch() {
        StepVerifier.create(classifier.classify(null, "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getClassifyLayer()).isEqualTo("DEFAULT");
                })
                .verifyComplete();

        StepVerifier.create(classifier.classify("", "session-1"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();

        StepVerifier.create(classifier.classify("   ", "session-1"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();

        verify(deepSeekClient, never()).chatCompletion(any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("LLM 返回带槽位的 JSON → 正确解析槽位")
    void classify_jsonWithSlots_parsesSlots() {
        String json = """
                {"intent":"SEARCH","sub_intent":"resource","confidence":0.85,
                 "rewritten_query":"清华操作系统期末卷子",
                 "slots":{"school":"清华","category":"期末","post_type":"resource","sort":"最新"}}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("求清华操作系统期末卷子", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getSlots()).isNotNull();
                    assertThat(result.getSlots().getSchool()).isEqualTo("清华");
                    assertThat(result.getSlots().getCategory()).isEqualTo("期末");
                    assertThat(result.getSlots().getPostType()).isEqualTo("resource");
                    assertThat(result.getSlots().getSort()).isEqualTo("最新");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 返回缺字段 JSON → 用默认值填充")
    void classify_jsonMissingFields_usesDefaults() {
        String json = """
                {"intent":"SEARCH","confidence":0.8}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.RESOURCE);
                    assertThat(result.getConfidence()).isEqualTo(0.8);
                    assertThat(result.getRewrittenQuery()).isEqualTo("test");  // 回退到原 query
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 成功后写入缓存")
    void classify_llmSuccess_writesCache() {
        String json = """
                {"intent":"SEARCH","sub_intent":"resource","confidence":0.85,
                 "rewritten_query":"求卷子"}""";
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(mockResponse(json)));

        classifier.classify("求卷子", "session-1").block();

        verify(cacheService).put(eq("求卷子"), any(IntentResult.class));
    }

    @Test
    @DisplayName("LLM 返回空 choices → Embedding 兜底 SEARCH")
    void classify_emptyChoices_fallbackToSearch() {
        DeepSeekResponse emptyResponse = new DeepSeekResponse();
        emptyResponse.setChoices(List.of());
        when(deepSeekClient.chatCompletion(any(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(emptyResponse));

        IntentResult embeddingResult = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.7)
                .rewrittenQuery("test")
                .classifyLayer("EMBEDDING")
                .build();
        when(embeddingFallback.classify(anyString())).thenReturn(Mono.just(embeddingResult));

        StepVerifier.create(classifier.classify("test", "session-1"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getClassifyLayer()).isEqualTo("EMBEDDING");
                })
                .verifyComplete();

        verify(embeddingFallback).classify("test");
    }
}
