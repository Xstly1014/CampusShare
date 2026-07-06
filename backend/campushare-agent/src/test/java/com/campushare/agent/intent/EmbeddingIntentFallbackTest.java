package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.service.EmbeddingIntentFallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EmbeddingIntentFallback 单元测试。
 *
 * 验证点：
 *  - 余弦相似度计算正确
 *  - 5 意图描述匹配
 *  - Embedding 失败兜底 SEARCH
 *  - 未初始化时兜底 SEARCH
 *  - 空 query 兜底
 */
@DisplayName("EmbeddingIntentFallback 单元测试")
class EmbeddingIntentFallbackTest {

    private EmbeddingClient embeddingClient;
    private EmbeddingIntentFallback fallback;

    @BeforeEach
    void setUp() throws Exception {
        embeddingClient = mock(EmbeddingClient.class);
        fallback = new EmbeddingIntentFallback(embeddingClient);
    }

    /**
     * 手动预加载意图向量（绕过 @PostConstruct 的异步 subscribe）。
     * 用正交向量模拟 5 个意图，确保余弦相似度可精确控制。
     */
    private void preloadWithOrthogonalVectors() throws Exception {
        Map<Intent, float[]> vectors = Map.of(
                Intent.HOW_TO, new float[]{1, 0, 0, 0, 0},
                Intent.SEARCH, new float[]{0, 1, 0, 0, 0},
                Intent.NAVIGATE, new float[]{0, 0, 1, 0, 0},
                Intent.CLARIFY, new float[]{0, 0, 0, 1, 0},
                Intent.OUT_OF_SCOPE, new float[]{0, 0, 0, 0, 1}
        );

        Field vectorsField = EmbeddingIntentFallback.class.getDeclaredField("intentVectors");
        vectorsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Intent, float[]> intentVectors = (Map<Intent, float[]>) vectorsField.get(fallback);
        intentVectors.putAll(vectors);

        Field initField = EmbeddingIntentFallback.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        initField.setBoolean(fallback, true);
    }

    @Test
    @DisplayName("预加载后 classify → 返回最相似意图")
    void classify_initialized_returnsBestMatch() throws Exception {
        preloadWithOrthogonalVectors();
        // query 向量接近 HOW_TO 方向
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.9f, 0.1f, 0.1f, 0.1f, 0.1f}));

        StepVerifier.create(fallback.classify("怎么发帖"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
                    assertThat(result.getClassifyLayer()).isEqualTo("EMBEDDING");
                    assertThat(result.getConfidence()).isGreaterThan(0.0);
                    assertThat(result.getRewrittenQuery()).isEqualTo("怎么发帖");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("SEARCH 方向的 query → 匹配 SEARCH")
    void classify_searchVector_matchesSearch() throws Exception {
        preloadWithOrthogonalVectors();
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.1f, 0.95f, 0.05f, 0.05f, 0.05f}));

        StepVerifier.create(fallback.classify("求操作系统卷子"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();
    }

    @Test
    @DisplayName("OUT_OF_SCOPE 方向的 query → 匹配 OUT_OF_SCOPE")
    void classify_outOfScopeVector_matchesOutOfScope() throws Exception {
        preloadWithOrthogonalVectors();
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.05f, 0.05f, 0.05f, 0.05f, 0.95f}));

        StepVerifier.create(fallback.classify("你好"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.OUT_OF_SCOPE))
                .verifyComplete();
    }

    @Test
    @DisplayName("未初始化时 classify → 兜底 SEARCH")
    void classify_notInitialized_fallbackSearch() {
        // initialized=false（默认），不调 embeddingClient
        StepVerifier.create(fallback.classify("求卷子"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getConfidence()).isEqualTo(0.0);
                    assertThat(result.getClassifyLayer()).isEqualTo("EMBEDDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Embedding 返回 empty → 兜底 SEARCH")
    void classify_emptyEmbedding_fallbackSearch() throws Exception {
        preloadWithOrthogonalVectors();
        when(embeddingClient.embed(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(fallback.classify("求卷子"))
                .assertNext(result -> {
                    assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
                    assertThat(result.getConfidence()).isEqualTo(0.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Embedding 抛异常 → 兜底 SEARCH")
    void classify_embeddingError_fallbackSearch() throws Exception {
        preloadWithOrthogonalVectors();
        when(embeddingClient.embed(anyString())).thenReturn(Mono.error(new RuntimeException("API down")));

        StepVerifier.create(fallback.classify("求卷子"))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();
    }

    @Test
    @DisplayName("空 query → 兜底 SEARCH")
    void classify_nullOrBlank_fallbackSearch() {
        StepVerifier.create(fallback.classify(null))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();

        StepVerifier.create(fallback.classify(""))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();

        StepVerifier.create(fallback.classify("   "))
                .assertNext(result -> assertThat(result.getIntent()).isEqualTo(Intent.SEARCH))
                .verifyComplete();
    }

    @Test
    @DisplayName("子意图正确分配：HOW_TO → feature_help")
    void classify_assignsCorrectSubIntent() throws Exception {
        preloadWithOrthogonalVectors();
        when(embeddingClient.embed(anyString())).thenReturn(Mono.just(new float[]{0.9f, 0.1f, 0.1f, 0.1f, 0.1f}));

        StepVerifier.create(fallback.classify("怎么发帖"))
                .assertNext(result -> assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.FEATURE_HELP))
                .verifyComplete();
    }
}
