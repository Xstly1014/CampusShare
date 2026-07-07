package com.campushare.agent.service;

import com.campushare.agent.dto.QualityInput;

/**
 * 知识库质量评分器接口。
 *
 * 实现类：
 * - FourDimensionQualityScorer：四维加权评分
 *
 * 未来扩展：
 * - 接入 ML 模型评分（基于用户点击/停留时长等行为数据）
 */
public interface KnowledgeQualityScorer {

    /**
     * 计算质量评分（0-1）。
     *
     * @param input 评分输入（召回频次、反馈分、更新时间、分块数）
     * @return 质量评分，范围 [0.0, 1.0]
     */
    double score(QualityInput input);
}
