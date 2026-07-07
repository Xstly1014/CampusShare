package com.campushare.agent.service;

import com.campushare.agent.dto.QualityInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 四维质量评分器。
 *
 * 四维权重（遵循 project_memory 硬约束）：
 * - 召回频次 0.4：归一化到 0-1（log 归一化，100次封顶）
 * - 用户反馈 0.3：feedbackScore（0-1，点赞/点踩调整）
 * - 新鲜度 0.2：30天内=1.0，90天后=0.0，中间线性衰减
 * - 完整度 0.1：chunkCount 越多越完整（1块=0.3，3块=0.7，5+块=1.0）
 *
 * 总分 = Σ(维度归一化值 × 权重)，范围 [0.0, 1.0]
 */
@Slf4j
@Component
public class FourDimensionQualityScorer implements KnowledgeQualityScorer {

    private final double weightRecall;
    private final double weightFeedback;
    private final double weightFreshness;
    private final double weightCompleteness;

    private static final int RECALL_CAP = 100;
    private static final int FRESHNESS_FULL_DAYS = 30;
    private static final int FRESHNESS_ZERO_DAYS = 90;
    private static final int COMPLETENESS_FULL_CHUNKS = 5;

    public FourDimensionQualityScorer(
            @Value("${app.knowledge.quality.weight-recall:0.4}") double weightRecall,
            @Value("${app.knowledge.quality.weight-feedback:0.3}") double weightFeedback,
            @Value("${app.knowledge.quality.weight-freshness:0.2}") double weightFreshness,
            @Value("${app.knowledge.quality.weight-completeness:0.1}") double weightCompleteness
    ) {
        this.weightRecall = weightRecall;
        this.weightFeedback = weightFeedback;
        this.weightFreshness = weightFreshness;
        this.weightCompleteness = weightCompleteness;
    }

    @Override
    public double score(QualityInput input) {
        if (input == null) {
            return 0.5;
        }

        double recall = normalizeRecall(input.recallCount());
        double feedback = clamp(input.feedbackScore(), 0.0, 1.0);
        double freshness = normalizeFreshness(input.updatedAt());
        double completeness = normalizeCompleteness(input.chunkCount());

        double score = recall * weightRecall
                + feedback * weightFeedback
                + freshness * weightFreshness
                + completeness * weightCompleteness;

        return clamp(score, 0.0, 1.0);
    }

    /**
     * 召回频次归一化：log 归一化，100次封顶。
     * 0次=0.0，1次≈0.15，10次≈0.5，50次≈0.85，100+次=1.0
     */
    private double normalizeRecall(int recallCount) {
        if (recallCount <= 0) return 0.0;
        if (recallCount >= RECALL_CAP) return 1.0;
        return Math.log(1 + recallCount) / Math.log(1 + RECALL_CAP);
    }

    /**
     * 新鲜度归一化：30天内=1.0，90天后=0.0，中间线性衰减。
     */
    private double normalizeFreshness(LocalDateTime updatedAt) {
        if (updatedAt == null) return 0.0;
        LocalDateTime now = LocalDateTime.now();
        long days = Duration.between(updatedAt, now).toDays();
        if (days <= FRESHNESS_FULL_DAYS) return 1.0;
        if (days >= FRESHNESS_ZERO_DAYS) return 0.0;
        return 1.0 - (double) (days - FRESHNESS_FULL_DAYS) / (FRESHNESS_ZERO_DAYS - FRESHNESS_FULL_DAYS);
    }

    /**
     * 完整度归一化：基于分块数。
     * 0块=0.0，1块=0.3，2块=0.5，3块=0.7，4块=0.9，5+块=1.0
     */
    private double normalizeCompleteness(int chunkCount) {
        if (chunkCount <= 0) return 0.0;
        if (chunkCount >= COMPLETENESS_FULL_CHUNKS) return 1.0;
        return 0.1 + 0.2 * chunkCount;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
