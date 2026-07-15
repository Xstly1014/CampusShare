package com.campushare.agent.dto;

import com.campushare.agent.enums.Intent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图分类结果 DTO。
 *
 * 由三层漏斗产生：
 *  - Layer 1 RULE：RuleShortCircuitFilter 规则命中
 *  - Layer 2 LLM：IntentClassifier LLM 分类
 *  - Layer 3 EMBEDDING：EmbeddingIntentFallback 兜底
 *  - Layer DEFAULT：全部失败，兜底 SEARCH
 *
 * ADR-011：分类 + 查询改写 + 槽位抽取合并为一次 LLM 调用，结果集中在此 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    /** L1 意图 */
    private Intent intent;

    /** L2 子意图（Intent.SubIntent 常量） */
    private String subIntent;

    /** 置信度 0.0-1.0 */
    private double confidence;

    /** 改写后的查询（用于检索，提升召回精准度） */
    private String rewrittenQuery;

    /** 抽取的槽位（school/category/postType/sort） */
    private SlotResult slots;

    /** HyDE 假设文档（仅 SEARCH+短query 时生成，MVP 阶段暂不启用） */
    private String hydeDoc;

    /** 分类层级：RULE / LLM / EMBEDDING / DEFAULT / POLICY */
    private String classifyLayer;

    /** 规则层自定义模板回复（如昵称识别后的个性化回复） */
    @JsonIgnore
    private String templateReply;

    /** 本轮是否声明了昵称（不参与 JSON 序列化，避免破坏缓存格式） */
    @JsonIgnore
    private boolean nicknameDeclared;

    /**
     * 槽位抽取结果。
     *
     * MVP 阶段由 LLM 一次性抽取，Advanced 阶段可接入 NER 模型。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotResult {
        /** 学校名（清华/北大/复旦/...） */
        private String school;
        /** 分类名（音乐/游戏/面经/...） */
        private String category;
        /** 帖子类型（resource/discussion） */
        private String postType;
        /** 排序方式（最新/最热） */
        private String sort;
    }

    /** ADR-010：置信度 ≥ 0.6 视为高置信 */
    @JsonIgnore
    public boolean isHighConfidence() {
        return confidence >= 0.6;
    }

    /** ADR-010：置信度 < 0.6 视为低置信，兜底为 SEARCH */
    @JsonIgnore
    public boolean isLowConfidence() {
        return confidence < 0.6;
    }
}
