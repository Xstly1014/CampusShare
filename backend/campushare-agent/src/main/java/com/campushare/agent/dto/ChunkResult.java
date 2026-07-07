package com.campushare.agent.dto;

/**
 * 分块检索中间结果（KnowledgeVectorStore.searchChunks 返回）。
 *
 * 与 RetrievalResult 的区别：保留 chunk 维度信息，用于文章级聚合前处理。
 * 聚合后转为 RetrievalResult 供 RRF 融合。
 *
 * @param articleId    文章 ID
 * @param chunkIndex   分块序号
 * @param title        文章标题
 * @param chunkContent 分块内容
 * @param headingPath  标题路径
 * @param topic        主题分类
 * @param similarity   相似度分数（向量检索=余弦相似度，关键词检索=trgm 相似度）
 * @param qualityScore 质量评分（用于检索排序加权）
 */
public record ChunkResult(
        Long articleId,
        int chunkIndex,
        String title,
        String chunkContent,
        String headingPath,
        String topic,
        double similarity,
        double qualityScore
) {
}
