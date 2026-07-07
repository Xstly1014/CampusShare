package com.campushare.agent.service;

import com.campushare.agent.dto.QualityInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * FourDimensionQualityScorer 单元测试。
 *
 * 验证点：
 *  - 全 0 输入 → 低分
 *  - 全满输入 → 接近 1.0
 *  - 权重验证：单独 recall=100（其他 0）≈ 0.4
 *  - normalizeRecall 边界：0→0.0, 100→1.0
 *  - normalizeFreshness 边界：今天→1.0, 90天前→0.0
 *  - normalizeCompleteness 边界：0→0.0, 5→1.0
 *  - null input → 0.5
 *  - clamp 验证
 *
 * 默认权重：recall 0.4 + feedback 0.3 + freshness 0.2 + completeness 0.1
 */
@DisplayName("FourDimensionQualityScorer 单元测试")
class FourDimensionQualityScorerTest {

    private FourDimensionQualityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new FourDimensionQualityScorer(0.4, 0.3, 0.2, 0.1);
    }

    // ========== null 输入 ==========

    @Nested
    @DisplayName("null / 边界输入")
    class NullInput {

        @Test
        @DisplayName("null input → 0.5（默认值）")
        void score_null_returnsDefault() {
            assertThat(scorer.score(null)).isEqualTo(0.5);
        }
    }

    // ========== 全 0 / 全满 输入 ==========

    @Nested
    @DisplayName("全 0 / 全满输入")
    class ExtremeInput {

        @Test
        @DisplayName("全 0 输入（recall=0, feedback=0, updatedAt=null, chunkCount=0）→ 低分")
        void score_allZero_returnsLowScore() {
            QualityInput input = new QualityInput(0, 0.0, null, 0);
            double score = scorer.score(input);
            assertThat(score).isLessThanOrEqualTo(0.1);
            assertThat(score).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("全满输入（recall=100, feedback=1.0, updatedAt=now, chunkCount=5）→ 接近 1.0")
        void score_allFull_returnsHighScore() {
            QualityInput input = new QualityInput(100, 1.0, LocalDateTime.now(), 5);
            double score = scorer.score(input);
            assertThat(score).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("全满输入恰好 ≈ 1.0（1*0.4 + 1*0.3 + 1*0.2 + 1*0.1，浮点精度内）")
        void score_allFull_equalsOne() {
            QualityInput input = new QualityInput(100, 1.0, LocalDateTime.now(), 5);
            assertThat(scorer.score(input)).isCloseTo(1.0, within(0.0001));
        }
    }

    // ========== 权重验证 ==========

    @Nested
    @DisplayName("权重验证")
    class WeightVerification {

        @Test
        @DisplayName("单独 recall=100（其他 0）≈ 0.4")
        void score_onlyRecall_returnsApproximatelyPointFour() {
            QualityInput input = new QualityInput(100, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.4, within(0.001));
        }

        @Test
        @DisplayName("单独 feedback=1.0（其他 0）= 0.3")
        void score_onlyFeedback_returnsPointThree() {
            QualityInput input = new QualityInput(0, 1.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("单独 freshness=1.0（其他 0）= 0.2")
        void score_onlyFreshness_returnsPointTwo() {
            QualityInput input = new QualityInput(0, 0.0, LocalDateTime.now(), 0);
            assertThat(scorer.score(input)).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("单独 completeness=1.0（其他 0）≈ 0.1")
        void score_onlyCompleteness_returnsApproximatelyPointOne() {
            QualityInput input = new QualityInput(0, 0.0, null, 5);
            assertThat(scorer.score(input)).isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("recall=50 + feedback=0.5 + freshness=1.0 + completeness=0.5 → 加权总分正确（log 归一化 recall≈0.85）")
        void score_partialInput_correctWeightedSum() {
            QualityInput input = new QualityInput(50, 0.5, LocalDateTime.now(), 2);
            double score = scorer.score(input);
            assertThat(score).isGreaterThan(0.5).isLessThan(0.8);
        }
    }

    // ========== normalizeRecall 边界 ==========

    @Nested
    @DisplayName("召回频次归一化")
    class NormalizeRecall {

        @Test
        @DisplayName("recall=0 → 召回维度贡献 0")
        void recall_zero_contributesZero() {
            QualityInput input = new QualityInput(0, 0.0, null, 5);
            double score = scorer.score(input);
            assertThat(score).isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("recall=100 → 召回维度贡献 0.4（封顶）")
        void recall_hundred_contributesPointFour() {
            QualityInput input = new QualityInput(100, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.4, within(0.001));
        }

        @Test
        @DisplayName("recall=200（超过封顶）→ 仍贡献 0.4")
        void recall_overCap_cappedAtPointFour() {
            QualityInput input = new QualityInput(200, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.4, within(0.001));
        }

        @Test
        @DisplayName("recall=1 → 召回维度贡献 > 0 但 < 0.4")
        void recall_one_partialContribution() {
            QualityInput input = new QualityInput(1, 0.0, null, 0);
            double score = scorer.score(input);
            assertThat(score).isGreaterThan(0.0).isLessThan(0.4);
        }
    }

    // ========== normalizeFreshness 边界 ==========

    @Nested
    @DisplayName("新鲜度归一化")
    class NormalizeFreshness {

        @Test
        @DisplayName("updatedAt=今天 → 新鲜度维度 = 1.0（贡献 0.2）")
        void freshness_today_contributesPointTwo() {
            QualityInput input = new QualityInput(0, 0.0, LocalDateTime.now(), 0);
            assertThat(scorer.score(input)).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("updatedAt=30天前 → 新鲜度仍 = 1.0")
        void freshness_30DaysAgo_stillFull() {
            QualityInput input = new QualityInput(0, 0.0, LocalDateTime.now().minusDays(30), 0);
            assertThat(scorer.score(input)).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("updatedAt=90天前 → 新鲜度 = 0.0")
        void freshness_90DaysAgo_zero() {
            QualityInput input = new QualityInput(0, 0.0, LocalDateTime.now().minusDays(90), 0);
            assertThat(scorer.score(input)).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("updatedAt=60天前 → 新鲜度 = 0.5（线性衰减中间值）")
        void freshness_60DaysAgo_half() {
            QualityInput input = new QualityInput(0, 0.0, LocalDateTime.now().minusDays(60), 0);
            assertThat(scorer.score(input)).isCloseTo(0.1, within(0.01));
        }

        @Test
        @DisplayName("updatedAt=null → 新鲜度 = 0.0")
        void freshness_null_zero() {
            QualityInput input = new QualityInput(0, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.0, within(0.001));
        }
    }

    // ========== normalizeCompleteness 边界 ==========

    @Nested
    @DisplayName("完整度归一化")
    class NormalizeCompleteness {

        @Test
        @DisplayName("chunkCount=0 → 完整度 = 0.0")
        void completeness_zero_zero() {
            QualityInput input = new QualityInput(0, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("chunkCount=1 → 完整度 = 0.3（贡献 0.03）")
        void completeness_one_contributesPointZeroThree() {
            QualityInput input = new QualityInput(0, 0.0, null, 1);
            assertThat(scorer.score(input)).isCloseTo(0.03, within(0.001));
        }

        @Test
        @DisplayName("chunkCount=5 → 完整度 = 1.0（贡献 0.1）")
        void completeness_five_fullContribution() {
            QualityInput input = new QualityInput(0, 0.0, null, 5);
            assertThat(scorer.score(input)).isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("chunkCount=10（超过封顶）→ 仍 = 1.0")
        void completeness_overCap_cappedAtOne() {
            QualityInput input = new QualityInput(0, 0.0, null, 10);
            assertThat(scorer.score(input)).isCloseTo(0.1, within(0.001));
        }
    }

    // ========== feedback 边界 ==========

    @Nested
    @DisplayName("用户反馈分")
    class FeedbackScore {

        @Test
        @DisplayName("feedback=0.0 → 贡献 0.0")
        void feedback_zero_zero() {
            QualityInput input = new QualityInput(0, 0.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("feedback=1.0 → 贡献 0.3")
        void feedback_one_pointThree() {
            QualityInput input = new QualityInput(0, 1.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("feedback=0.5 → 贡献 0.15")
        void feedback_half_pointOneFive() {
            QualityInput input = new QualityInput(0, 0.5, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.15, within(0.001));
        }

        @Test
        @DisplayName("feedback=2.0（超出范围）→ clamp 到 1.0 后贡献 0.3")
        void feedback_overOne_clamped() {
            QualityInput input = new QualityInput(0, 2.0, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("feedback=-0.5（负值）→ clamp 到 0.0 后贡献 0.0")
        void feedback_negative_clamped() {
            QualityInput input = new QualityInput(0, -0.5, null, 0);
            assertThat(scorer.score(input)).isCloseTo(0.0, within(0.001));
        }
    }

    // ========== clamp 验证 ==========

    @Nested
    @DisplayName("总分 clamp 到 [0, 1]")
    class ClampTotal {

        @Test
        @DisplayName("总分不超过 1.0")
        void score_neverExceedsOne() {
            QualityInput input = new QualityInput(Integer.MAX_VALUE, 1.0, LocalDateTime.now(), Integer.MAX_VALUE);
            assertThat(scorer.score(input)).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("总分不低于 0.0")
        void score_neverBelowZero() {
            QualityInput input = new QualityInput(0, 0.0, null, 0);
            assertThat(scorer.score(input)).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ========== 自定义权重 ==========

    @Nested
    @DisplayName("自定义权重")
    class CustomWeights {

        @Test
        @DisplayName("全权重给 recall → recall=100 时总分=1.0")
        void customWeights_allRecall() {
            FourDimensionQualityScorer customScorer = new FourDimensionQualityScorer(1.0, 0.0, 0.0, 0.0);
            QualityInput input = new QualityInput(100, 0.0, null, 0);
            assertThat(customScorer.score(input)).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("全权重给 feedback → feedback=1.0 时总分=1.0")
        void customWeights_allFeedback() {
            FourDimensionQualityScorer customScorer = new FourDimensionQualityScorer(0.0, 1.0, 0.0, 0.0);
            QualityInput input = new QualityInput(0, 1.0, null, 0);
            assertThat(customScorer.score(input)).isCloseTo(1.0, within(0.001));
        }
    }
}
