package com.campushare.agent.dto;

/**
 * 重复检测结果（KnowledgeDuplicateDetector.detect 返回）。
 *
 * Level 分级：
 * - DUPLICATE：相似度 ≥ 0.95，视为重复，应跳过摄入
 * - SIMILAR：相似度 ≥ 0.85，视为相似，应提示人工确认（或自动合并）
 * - UNIQUE：相似度 < 0.85，视为唯一，正常摄入
 *
 * @param level            检测级别
 * @param similarity       最高相似度分数
 * @param matchedArticleId 匹配到的已有文章 ID（UNIQUE 时为 null）
 */
public record DuplicateDetectionResult(
        Level level,
        double similarity,
        Long matchedArticleId
) {
    public enum Level {
        DUPLICATE,
        SIMILAR,
        UNIQUE
    }

    public static DuplicateDetectionResult unique() {
        return new DuplicateDetectionResult(Level.UNIQUE, 0.0, null);
    }

    public static DuplicateDetectionResult of(Level level, double similarity, Long matchedArticleId) {
        return new DuplicateDetectionResult(level, similarity, matchedArticleId);
    }

    public boolean isDuplicate() {
        return level == Level.DUPLICATE;
    }

    public boolean isSimilar() {
        return level == Level.SIMILAR;
    }

    public boolean isUnique() {
        return level == Level.UNIQUE;
    }
}
