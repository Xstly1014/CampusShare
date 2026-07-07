package com.campushare.agent.dto;

import java.time.LocalDateTime;

/**
 * 质量评分输入（FourDimensionQualityScorer.score 方法参数）。
 *
 * 四维：
 * - recallCount：召回频次（归一化到 0-1）
 * - feedbackScore：用户反馈分（0-1，点赞/点踩调整后的值）
 * - updatedAt：最后更新时间（计算新鲜度）
 * - chunkCount：分块数（计算完整度）
 *
 * @param recallCount   被召回次数
 * @param feedbackScore 用户反馈分（0-1）
 * @param updatedAt     最后更新时间
 * @param chunkCount    分块数量
 */
public record QualityInput(
        int recallCount,
        double feedbackScore,
        LocalDateTime updatedAt,
        int chunkCount
) {
}
