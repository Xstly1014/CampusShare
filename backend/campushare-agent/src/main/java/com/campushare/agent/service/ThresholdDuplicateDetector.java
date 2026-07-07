package com.campushare.agent.service;

import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.DuplicateDetectionResult;
import com.campushare.agent.dto.DuplicateDetectionResult.Level;
import com.campushare.agent.store.KnowledgeVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于双阈值的重复检测器。
 *
 * 双阈值（遵循 project_memory 硬约束）：
 * - similarity ≥ 0.95 → DUPLICATE（跳过摄入）
 * - similarity ≥ 0.85 → SIMILAR（提示合并或人工确认）
 * - similarity < 0.85 → UNIQUE（正常摄入）
 *
 * 实现原理：
 * 1. 用 chunk_index=0 的代表性分块 embedding 查询最相似文章
 * 2. 按双阈值判定级别
 * 3. 异常时降级为 UNIQUE，记录 warn 日志
 */
@Slf4j
@Component
public class ThresholdDuplicateDetector implements KnowledgeDuplicateDetector {

    private final KnowledgeVectorStore knowledgeVectorStore;
    private final KnowledgeMetricsConfig metricsConfig;
    private final double thresholdExact;
    private final double thresholdSimilar;

    public ThresholdDuplicateDetector(
            KnowledgeVectorStore knowledgeVectorStore,
            KnowledgeMetricsConfig metricsConfig,
            @Value("${app.knowledge.duplicate.threshold-exact:0.95}") double thresholdExact,
            @Value("${app.knowledge.duplicate.threshold-similar:0.85}") double thresholdSimilar
    ) {
        if (thresholdExact <= thresholdSimilar) {
            throw new IllegalArgumentException("threshold-exact (" + thresholdExact + ") must be greater than threshold-similar (" + thresholdSimilar + ")");
        }
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.metricsConfig = metricsConfig;
        this.thresholdExact = thresholdExact;
        this.thresholdSimilar = thresholdSimilar;
    }

    @Override
    public DuplicateDetectionResult detect(String content, float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            log.warn("Empty embedding for duplicate detection, treating as UNIQUE");
            metricsConfig.recordDuplicate(Level.UNIQUE.name());
            return DuplicateDetectionResult.unique();
        }

        try {
            KnowledgeVectorStore.SimilarMatch match = knowledgeVectorStore.findSimilar(embedding, 1);
            if (match == null) {
                metricsConfig.recordDuplicate(Level.UNIQUE.name());
                return DuplicateDetectionResult.unique();
            }

            double similarity = match.similarity();
            if (similarity >= thresholdExact) {
                log.info("Duplicate detected: articleId={}, similarity={}", match.articleId(), similarity);
                metricsConfig.recordDuplicate(Level.DUPLICATE.name());
                return DuplicateDetectionResult.of(Level.DUPLICATE, similarity, match.articleId());
            }
            if (similarity >= thresholdSimilar) {
                log.info("Similar content detected: articleId={}, similarity={}", match.articleId(), similarity);
                metricsConfig.recordDuplicate(Level.SIMILAR.name());
                return DuplicateDetectionResult.of(Level.SIMILAR, similarity, match.articleId());
            }
            metricsConfig.recordDuplicate(Level.UNIQUE.name());
            return DuplicateDetectionResult.unique();
        } catch (Exception e) {
            log.warn("Duplicate detection failed, treating as UNIQUE", e);
            metricsConfig.recordDuplicate(Level.UNIQUE.name());
            return DuplicateDetectionResult.unique();
        }
    }
}
