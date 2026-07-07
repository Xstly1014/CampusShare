package com.campushare.agent.service;

import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.DuplicateDetectionResult;
import com.campushare.agent.store.KnowledgeVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ThresholdDuplicateDetector 单元测试。
 *
 * 验证点：
 *  - similarity ≥ 0.95 → DUPLICATE + matchedArticleId
 *  - 0.85 ≤ similarity < 0.95 → SIMILAR
 *  - similarity < 0.85 → UNIQUE
 *  - findSimilar 返回 null → UNIQUE
 *  - 空 embedding → UNIQUE（不调 findSimilar）
 *  - findSimilar 抛异常 → 降级 UNIQUE
 *  - 构造函数 thresholdExact ≤ thresholdSimilar 抛异常
 */
@DisplayName("ThresholdDuplicateDetector 单元测试")
class ThresholdDuplicateDetectorTest {

    private KnowledgeVectorStore vectorStore;
    private KnowledgeMetricsConfig metricsConfig;
    private ThresholdDuplicateDetector detector;

    @BeforeEach
    void setUp() {
        vectorStore = mock(KnowledgeVectorStore.class);
        metricsConfig = mock(KnowledgeMetricsConfig.class);
        detector = new ThresholdDuplicateDetector(vectorStore, metricsConfig, 0.95, 0.85);
    }

    // ========== 构造函数校验 ==========

    @Nested
    @DisplayName("构造函数参数校验")
    class ConstructorValidation {

        @Test
        @DisplayName("thresholdExact ≤ thresholdSimilar → 抛 IllegalArgumentException")
        void constructor_exactLessOrEqualSimilar_throws() {
            assertThatThrownBy(() ->
                    new ThresholdDuplicateDetector(vectorStore, metricsConfig, 0.85, 0.85))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("threshold-exact");
        }

        @Test
        @DisplayName("thresholdExact < thresholdSimilar → 抛 IllegalArgumentException")
        void constructor_exactLessThanSimilar_throws() {
            assertThatThrownBy(() ->
                    new ThresholdDuplicateDetector(vectorStore, metricsConfig, 0.80, 0.85))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("thresholdExact > thresholdSimilar → 构造成功")
        void constructor_exactGreaterThanSimilar_succeeds() {
            new ThresholdDuplicateDetector(vectorStore, metricsConfig, 0.95, 0.85);
        }
    }

    // ========== DUPLICATE 判定 ==========

    @Nested
    @DisplayName("DUPLICATE 判定（similarity ≥ 0.95）")
    class DuplicateLevel {

        @Test
        @DisplayName("similarity=0.95 → DUPLICATE")
        void detect_exactBoundary_returnsDuplicate() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.95));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.matchedArticleId()).isEqualTo(100L);
            assertThat(result.similarity()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("similarity=0.99 → DUPLICATE")
        void detect_highSimilarity_returnsDuplicate() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(200L, 0.99));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.matchedArticleId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("similarity=1.0 → DUPLICATE")
        void detect_perfectMatch_returnsDuplicate() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(300L, 1.0));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isDuplicate()).isTrue();
        }

        @Test
        @DisplayName("DUPLICATE 时记录 metricsConfig.recordDuplicate('DUPLICATE')")
        void detect_duplicate_recordsMetric() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.95));

            detector.detect("content", new float[]{1.0f});

            verify(metricsConfig).recordDuplicate("DUPLICATE");
        }
    }

    // ========== SIMILAR 判定 ==========

    @Nested
    @DisplayName("SIMILAR 判定（0.85 ≤ similarity < 0.95）")
    class SimilarLevel {

        @Test
        @DisplayName("similarity=0.85 → SIMILAR")
        void detect_similarBoundary_returnsSimilar() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.85));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isSimilar()).isTrue();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.matchedArticleId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("similarity=0.90 → SIMILAR")
        void detect_middleSimilarity_returnsSimilar() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.90));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isSimilar()).isTrue();
        }

        @Test
        @DisplayName("similarity=0.94 → SIMILAR")
        void detect_justBelowExact_returnsSimilar() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.94));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isSimilar()).isTrue();
        }

        @Test
        @DisplayName("SIMILAR 时记录 metricsConfig.recordDuplicate('SIMILAR')")
        void detect_similar_recordsMetric() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.85));

            detector.detect("content", new float[]{1.0f});

            verify(metricsConfig).recordDuplicate("SIMILAR");
        }
    }

    // ========== UNIQUE 判定 ==========

    @Nested
    @DisplayName("UNIQUE 判定（similarity < 0.85）")
    class UniqueLevel {

        @Test
        @DisplayName("similarity=0.84 → UNIQUE")
        void detect_justBelowSimilar_returnsUnique() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.84));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isUnique()).isTrue();
            assertThat(result.matchedArticleId()).isNull();
        }

        @Test
        @DisplayName("similarity=0.50 → UNIQUE")
        void detect_lowSimilarity_returnsUnique() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.50));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isUnique()).isTrue();
        }

        @Test
        @DisplayName("similarity=0.0 → UNIQUE")
        void detect_zeroSimilarity_returnsUnique() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.0));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isUnique()).isTrue();
        }

        @Test
        @DisplayName("UNIQUE 时记录 metricsConfig.recordDuplicate('UNIQUE')")
        void detect_unique_recordsMetric() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenReturn(new KnowledgeVectorStore.SimilarMatch(100L, 0.50));

            detector.detect("content", new float[]{1.0f});

            verify(metricsConfig).recordDuplicate("UNIQUE");
        }
    }

    // ========== 空结果与降级 ==========

    @Nested
    @DisplayName("空结果与异常降级")
    class EmptyAndDegradation {

        @Test
        @DisplayName("findSimilar 返回 null → UNIQUE")
        void detect_findSimilarReturnsNull_returnsUnique() {
            when(vectorStore.findSimilar(any(), anyInt())).thenReturn(null);

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isUnique()).isTrue();
            verify(metricsConfig).recordDuplicate("UNIQUE");
        }

        @Test
        @DisplayName("空 embedding → UNIQUE（不调 findSimilar）")
        void detect_emptyEmbedding_returnsUniqueWithoutQueryingStore() {
            DuplicateDetectionResult result = detector.detect("content", new float[0]);

            assertThat(result.isUnique()).isTrue();
            verify(vectorStore, never()).findSimilar(any(), anyInt());
            verify(metricsConfig).recordDuplicate("UNIQUE");
        }

        @Test
        @DisplayName("null embedding → UNIQUE（不调 findSimilar）")
        void detect_nullEmbedding_returnsUniqueWithoutQueryingStore() {
            DuplicateDetectionResult result = detector.detect("content", null);

            assertThat(result.isUnique()).isTrue();
            verify(vectorStore, never()).findSimilar(any(), anyInt());
            verify(metricsConfig).recordDuplicate("UNIQUE");
        }

        @Test
        @DisplayName("findSimilar 抛异常 → 降级 UNIQUE")
        void detect_findSimilarThrows_degradesToUnique() {
            when(vectorStore.findSimilar(any(), anyInt()))
                    .thenThrow(new RuntimeException("PG connection refused"));

            DuplicateDetectionResult result = detector.detect("content", new float[]{1.0f});

            assertThat(result.isUnique()).isTrue();
            verify(metricsConfig).recordDuplicate("UNIQUE");
        }
    }
}
