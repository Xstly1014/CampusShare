package com.campushare.agent.dto;

import com.campushare.agent.llm.DeepSeekRequest;

import java.util.List;
import java.util.Map;

/**
 * 上下文快照（ADR-076）。
 *
 * 每次组装完上下文后生成，异步写入 agent_context_snapshots 表，
 * 供后续复盘"为什么答错"——能看到当时给 LLM 的完整输入。
 *
 * @param sessionId         会话ID
 * @param turnId            轮次序号
 * @param messages          发送给 LLM 的完整 messages
 * @param layerTokens       各层 token 占用 {"L0":1024,"L1":280,...}
 * @param totalInputTokens  总输入 token 数
 * @param usedMemoryIds     装载的长期记忆 ID 列表（P2 实现，当前为 null）
 * @param truncated         是否发生截断
 * @param truncationReason  截断原因（如 L4_HISTORY_TRUNCATED）
 */
public record ContextSnapshot(
        String sessionId,
        int turnId,
        List<DeepSeekRequest.Message> messages,
        Map<String, Integer> layerTokens,
        int totalInputTokens,
        List<Long> usedMemoryIds,
        boolean truncated,
        String truncationReason
) {
}
