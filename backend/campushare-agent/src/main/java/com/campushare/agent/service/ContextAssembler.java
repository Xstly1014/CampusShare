package com.campushare.agent.service;

import com.campushare.agent.dto.ContextSnapshot;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.TokenBudget;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.llm.DeepSeekRequest;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文组装器（ADR-070）。
 *
 * 按 L0-L5 分层装入 LLM 上下文窗口，按意图动态分配 Token 预算，超预算时分级降级。
 *
 * 分层结构：
 *   L0 System Prompt   — 永驻，来自 PromptAssembler（含 L1 平台 + L2 任务 + L3 Few-shot + 检索结果 + L4 Guardrail）
 *   L1 用户画像         — 可裁剪，来自 LongTermMemoryService（P2 实现，当前为 null）
 *   L2 工具定义         — 永驻（P2 实现 Function Calling 时启用，当前跳过）
 *   L3 检索结果         — 已嵌入 L0 的 <context> 标签中（由 PromptAssembler 装配）
 *   L4 对话历史         — 可压缩，来自数据库/Redis，按轮次 user/assistant 交替
 *   L5 用户输入         — 永驻，当前轮 query
 *
 * Token 预算（ADR-071）：
 *   总预算 8000 tokens。L0（含 L3 检索）占 ~4000-5000，L1 占 ~300，L4 占 ~1500-4000（按意图），L5 占 ~700。
 *
 * 降级链（ADR-072）：
 *   降级 1: L4 截断到最近 2 轮（保留指代消解所需的最少上下文）
 *   降级 2: L1 裁剪（丢弃用户画像，保留 L0+L4+L5 核心）
 *   降级 3: 硬上限兜底（L4 截断到最近 1 轮，记录 truncated=true）
 */
@Slf4j
@Service
public class ContextAssembler {

    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_3_5_TURBO);

    /** 单条消息的 overhead token 数（role + formatting） */
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    /** L1 用户画像的格式化 overhead */
    private static final int L1_FORMAT_OVERHEAD = 20;

    /**
     * 组装上下文（L0-L5 分层 + Token 预算 + 降级链）。
     *
     * @param sessionId     会话ID
     * @param turnId        轮次序号
     * @param userQuery     L5 用户输入
     * @param intent        意图结果（驱动 L4 预算分配）
     * @param systemPrompt  L0 System Prompt（来自 PromptAssembler，已含检索结果）
     * @param history       L4 对话历史（按时间正序，最旧的在前）
     * @param userProfile   L1 用户画像（可为 null）
     * @param toolSchemas   L2 工具定义 Schema（可为 null，Function Calling 启用时传入）
     * @param usedMemoryIds 本轮使用的长期记忆 ID 列表（可为 null）
     * @return 组装结果（messages + token 计数 + 快照）
     */
    public AssembledContext assemble(String sessionId, int turnId, String userQuery,
            IntentResult intent, String systemPrompt,
            List<AgentTurn> history, String userProfile,
            String toolSchemas, List<Long> usedMemoryIds) {

        TokenBudget budget = TokenBudget.forIntent(intent);

        // L0: System Prompt（永驻，不截断）
        int l0Tokens = countTokens(systemPrompt) + MESSAGE_OVERHEAD_TOKENS;

        // L5: 用户输入（永驻，不截断）
        int l5Tokens = countTokens(userQuery) + MESSAGE_OVERHEAD_TOKENS;

        // L1: 用户画像（可裁剪）
        String l1Content = userProfile;
        int l1Tokens = 0;
        if (l1Content != null && !l1Content.isBlank()) {
            l1Tokens = countTokens(l1Content) + L1_FORMAT_OVERHEAD;
            if (l1Tokens > budget.l1Profile()) {
                l1Content = truncateToTokens(l1Content, budget.l1Profile() - L1_FORMAT_OVERHEAD);
                l1Tokens = countTokens(l1Content) + L1_FORMAT_OVERHEAD;
            }
        }

        // L2: 工具定义（永驻，当前为 null，Function Calling 启用时注入）
        int l2Tokens = 0;
        if (toolSchemas != null && !toolSchemas.isBlank()) {
            l2Tokens = countTokens(toolSchemas) + MESSAGE_OVERHEAD_TOKENS;
        }

        // L4: 对话历史（可压缩）
        List<AgentTurn> workingHistory = history != null ? new ArrayList<>(history) : Collections.emptyList();
        int l4Tokens = countHistoryTokens(workingHistory);
        boolean truncated = false;
        String truncationReason = null;

        // 历史超预算时截断
        if (l4Tokens > budget.l4History()) {
            workingHistory = truncateHistory(workingHistory, budget.l4History());
            l4Tokens = countHistoryTokens(workingHistory);
            truncated = true;
            truncationReason = "L4_HISTORY_TRUNCATED";
        }

        // 超预算降级链（使用 maxInput 而非 total，预留输出空间）
        int inputBudget = budget.maxInput();
        int total = l0Tokens + l1Tokens + l2Tokens + l4Tokens + l5Tokens;

        if (total > inputBudget) {
            // 降级 1: L4 截断到最近 2 轮（2 个 AgentTurn = 4 条消息 = 2 个 user+assistant 对）
            if (workingHistory.size() > 2) {
                workingHistory = new ArrayList<>(workingHistory.subList(
                        workingHistory.size() - 2, workingHistory.size()));
                l4Tokens = countHistoryTokens(workingHistory);
                truncated = true;
                truncationReason = "DEGRADE_L4_TO_2_ROUNDS";
            }
            total = l0Tokens + l1Tokens + l2Tokens + l4Tokens + l5Tokens;
            log.debug("Degrade step 1 (L4 to 2 rounds): total={}, inputBudget={}", total, inputBudget);
        }

        if (total > inputBudget && l1Tokens > 0) {
            // 降级 2: L1 裁剪（丢弃用户画像）
            l1Content = null;
            l1Tokens = 0;
            total = l0Tokens + l2Tokens + l4Tokens + l5Tokens;
            truncated = true;
            truncationReason = "DEGRADE_L1_DROPPED";
            log.debug("Degrade step 2 (L1 dropped): total={}, inputBudget={}", total, inputBudget);
        }

        if (total > inputBudget) {
            // 降级 3: 硬上限兜底 — L4 截断到最近 1 轮（1 个 AgentTurn = 2 条消息）
            if (workingHistory.size() > 1) {
                workingHistory = new ArrayList<>(workingHistory.subList(
                        workingHistory.size() - 1, workingHistory.size()));
                l4Tokens = countHistoryTokens(workingHistory);
            }
            total = l0Tokens + l1Tokens + l2Tokens + l4Tokens + l5Tokens;
            truncated = true;
            truncationReason = "HARD_LIMIT_L4_TO_1_ROUND";
            log.warn("Hard limit applied: sessionId={}, turnId={}, total={}, inputBudget={}",
                    sessionId, turnId, total, inputBudget);
        }

        // 构建 messages
        List<DeepSeekRequest.Message> messages = buildMessages(
                systemPrompt, l1Content, toolSchemas, workingHistory, userQuery);

        // 构建 layerTokens 快照
        Map<String, Integer> layerTokens = new LinkedHashMap<>();
        layerTokens.put("L0_SYSTEM", l0Tokens);
        layerTokens.put("L1_PROFILE", l1Tokens);
        layerTokens.put("L2_TOOL_DEFS", l2Tokens);
        layerTokens.put("L4_HISTORY", l4Tokens);
        layerTokens.put("L5_USER_INPUT", l5Tokens);
        layerTokens.put("TOTAL", total);

        ContextSnapshot snapshot = new ContextSnapshot(
                sessionId, turnId, messages, layerTokens, total,
                usedMemoryIds, truncated, truncationReason);

        if (truncated) {
            log.info("Context truncated: sessionId={}, turnId={}, reason={}, total={}, inputBudget={}",
                    sessionId, turnId, truncationReason, total, inputBudget);
        }

        return new AssembledContext(messages, total, snapshot);
    }

    /**
     * 构建发给 LLM 的 messages 列表。
     *
     * 顺序：system（L0+L1+L2） → 历史（L4，user/assistant 交替） → 当前用户输入（L5）
     * L2 工具定义在 Function Calling 启用时注入，当前预留位置。
     */
    private List<DeepSeekRequest.Message> buildMessages(String systemPrompt, String userProfile,
            String toolSchemas, List<AgentTurn> history, String currentMessage) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        // L0 + L1 + L2: System Prompt
        StringBuilder fullSystemPrompt = new StringBuilder(systemPrompt);
        if (userProfile != null && !userProfile.isBlank()) {
            fullSystemPrompt.append("\n\n# 用户画像\n").append(userProfile).append("\n");
        }
        if (toolSchemas != null && !toolSchemas.isBlank()) {
            fullSystemPrompt.append("\n\n# 可用工具\n").append(toolSchemas).append("\n");
        }
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(fullSystemPrompt.toString())
                .build());

        // L4: 对话历史（按时间正序，最旧的在前）
        for (AgentTurn t : history) {
            if (t.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(t.getUserMessage())
                        .build());
            }
            if (t.getAssistantMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(t.getAssistantMessage())
                        .build());
            }
        }

        // L5: 当前用户输入
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(currentMessage)
                .build());

        return messages;
    }

    private int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    private int countHistoryTokens(List<AgentTurn> history) {
        int total = 0;
        for (AgentTurn t : history) {
            if (t.getUserMessage() != null) {
                total += countTokens(t.getUserMessage()) + MESSAGE_OVERHEAD_TOKENS;
            }
            if (t.getAssistantMessage() != null) {
                total += countTokens(t.getAssistantMessage()) + MESSAGE_OVERHEAD_TOKENS;
            }
        }
        return total;
    }

    /**
     * 截断历史到指定 token 预算内（保留最近的轮次）。
     */
    private List<AgentTurn> truncateHistory(List<AgentTurn> history, int maxTokens) {
        if (history.isEmpty()) {
            return history;
        }
        List<AgentTurn> kept = new ArrayList<>();
        int tokens = 0;
        // 从最新往前扫描，保留能放下的轮次
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentTurn t = history.get(i);
            int turnTokens = 0;
            if (t.getUserMessage() != null) {
                turnTokens += countTokens(t.getUserMessage()) + MESSAGE_OVERHEAD_TOKENS;
            }
            if (t.getAssistantMessage() != null) {
                turnTokens += countTokens(t.getAssistantMessage()) + MESSAGE_OVERHEAD_TOKENS;
            }
            if (tokens + turnTokens > maxTokens) {
                break;
            }
            tokens += turnTokens;
            kept.add(0, t); // 头部插入，保持时间正序
        }
        return kept;
    }

    /**
     * 截断文本到指定 token 数内（保留前面的内容）。
     */
    private String truncateToTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int tokens = countTokens(text);
        if (tokens <= maxTokens) {
            return text;
        }
        // 按 token 比例估算截断位置（避免逐字符解码的复杂性）
        int maxChars = (int) (text.length() * ((double) maxTokens / tokens));
        return text.substring(0, Math.min(maxChars, text.length())) + "...";
    }

    /**
     * 组装结果。
     *
     * @param messages   发给 LLM 的 messages 列表
     * @param totalTokens 总 input token 数
     * @param snapshot   上下文快照（用于入库归档）
     */
    public record AssembledContext(
            List<DeepSeekRequest.Message> messages,
            int totalTokens,
            ContextSnapshot snapshot
    ) {
    }
}
