package com.campushare.agent.service;

import com.campushare.agent.dto.DuplicateDetectionResult;

/**
 * 知识库重复检测器接口。
 *
 * 实现类：
 * - ThresholdDuplicateDetector：基于向量相似度双阈值检测
 *
 * 未来扩展：
 * - 基于 content_md5 精确匹配 + 向量相似度组合检测
 * - 接入外部去重服务
 */
public interface KnowledgeDuplicateDetector {

    /**
     * 检测给定内容是否与已有知识库文档重复。
     *
     * @param content   待检测的内容文本
     * @param embedding 待检测内容的 embedding 向量
     * @return 检测结果（Level + similarity + matchedArticleId）
     */
    DuplicateDetectionResult detect(String content, float[] embedding);
}
