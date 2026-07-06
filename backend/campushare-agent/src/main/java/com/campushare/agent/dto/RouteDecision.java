package com.campushare.agent.dto;

import com.campushare.agent.enums.Intent;
import lombok.Builder;
import lombok.Data;

/**
 * 路由决策 DTO。
 *
 * 由 IntentRouter 产生，指示 AgentChatService 如何处理请求：
 *  - shortCircuit=true：走快路径（模板回复，不调 LLM）
 *  - shortCircuit=false：走 RAG 管线（HOW_TO/SEARCH/CLARIFY）
 *
 * ADR-013：简单意图（OUT_OF_SCOPE/NAVIGATE）走快路径，省 60%+ 成本和延迟。
 */
@Data
@Builder
public class RouteDecision {

    /** 是否走快路径（不调 LLM） */
    private boolean shortCircuit;

    /** 快路径模板回复（shortCircuit=true 时使用） */
    private String templateReply;

    /** NAVIGATE 跳转路由（如 /profile/liked，可选） */
    private String navigateRoute;

    /** 路由后的意图（用于选择 L2 Prompt） */
    private Intent intent;

    /** 改写后的查询（用于检索，shortCircuit=false 时使用） */
    private String rewrittenQuery;

    /** 槽位（用于结构化过滤，Advanced 阶段启用） */
    private IntentResult.SlotResult slots;
}
