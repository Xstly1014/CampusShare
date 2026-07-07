package com.campushare.agent.dto;

import com.campushare.agent.dto.IntentResult.SlotResult;
import lombok.Builder;

/**
 * 意图驱动检索配置（ADR-024）。
 *
 * 由 {@code RetrievalService.selectConfig(IntentResult)} 按意图/子意图/置信度选择，
 * 控制各路检索来源的 topK、相似度阈值、token 预算、是否启用帖子关键词检索、槽位过滤条件。
 *
 * 设计原则：
 *  - HOW_TO 偏知识库（功能说明），SEARCH/resource 偏帖子（资源贴）
 *  - 低置信度时 topK + {@code low-confidence-boost} 扩大召回
 *  - slots（school/category/postType）作为帖子检索的 SQL WHERE 条件（ADR-025）
 */
@Builder
public record RetrievalConfig(
        int knowledgeTopK,
        int knowledgeKeywordTopK,
        int postTopK,
        int postKeywordTopK,
        int rerankTopK,
        double similarityThreshold,
        int tokenBudget,
        boolean usePostKeyword,
        SlotResult slots
) {
}
