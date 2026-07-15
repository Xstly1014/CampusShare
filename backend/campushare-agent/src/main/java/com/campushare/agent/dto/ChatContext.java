package com.campushare.agent.dto;

import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;

import java.util.List;
import java.util.Map;

/**
 * Agent 对话上下文（供 LLM-first 流程使用）。
 *
 * TODO：当前 AgentChatService 内部仍定义了一个同名的 private ChatContext record，
 * 集成阶段需要把 AgentChatService 中的构造调用迁移到本公共 DTO，并统一字段。
 */
public record ChatContext(
        AgentSession session,
        ChatRequest request,
        AgentTurn turn,
        UserProfile userProfile,
        List<Map<String, Object>> previousRefs
) {
}
