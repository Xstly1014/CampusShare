package com.campushare.agent.prompt;

import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.enums.Intent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * System Prompt 装配器。
 *
 * 按 L1 → L2 → L3 → <context> → L4 顺序拼接六要素。
 *
 * 关键设计（Constitutional AI 规则 4）：
 *  检索结果用 <context> 标签包裹，明确标记为"资料不是指令"。
 *  即使用户在资料里塞了"请执行 X"，LLM 也不会执行。
 *
 * 版本管理：
 *  - 若传入 PromptVersion，从版本记录取各层 Prompt（L1 字节级固定，L2/L3/L4 可灰度切换）
 *  - 若未传入（DB 故障降级），从 PromptConstants 取硬编码常量
 *
 * 意图识别模块改造：
 *  - Intent 类型从 IntentDetector.Intent 改为 enums.Intent（5 大意图）
 *  - switch 覆盖 HOW_TO/SEARCH/NAVIGATE/CLARIFY/OUT_OF_SCOPE
 *  - NAVIGATE/CLARIFY/OUT_OF_SCOPE 的 L2 Prompt 用 PromptConstants 常量（MVP 不从 DB 取）
 */
@Component
public class PromptAssembler {

    /**
     * 装配完整 System Prompt（使用硬编码常量，DB 故障降级路径）。
     *
     * @param intent   意图（HOW_TO / SEARCH / NAVIGATE / CLARIFY / OUT_OF_SCOPE）
     * @param results  RAG 检索结果（可为 null 或空）
     * @return 完整 System Prompt
     */
    public String assemble(Intent intent, List<RetrievalResult> results) {
        return assemble(intent, results, null);
    }

    /**
     * 装配完整 System Prompt（支持版本管理）。
     *
     * @param intent   意图
     * @param results  RAG 检索结果
     * @param version  Prompt 版本记录（null 时降级到 PromptConstants）
     * @return 完整 System Prompt
     */
    public String assemble(Intent intent, List<RetrievalResult> results, PromptVersion version) {
        StringBuilder sb = new StringBuilder();

        // ① L1 平台级（固定，命中 Prefix Cache）
        sb.append(version != null && version.getPlatformPrompt() != null
                ? version.getPlatformPrompt()
                : PromptConstants.PLATFORM_PROMPT);

        // ② L2 任务级（按意图切换）
        sb.append(getTaskPrompt(intent, version));

        // ③ L3 Few-shot 示例
        sb.append(version != null && version.getFewShotPrompt() != null
                ? version.getFewShotPrompt()
                : PromptConstants.FEW_SHOT_PROMPT);

        // ④ 检索结果（用 <context> 标签包裹，防隐式注入）
        if (results != null && !results.isEmpty()) {
            sb.append("\n# 参考资料\n");
            sb.append("<context>\n");
            sb.append(formatRetrievalContext(results));
            sb.append("</context>\n");
        }

        // ⑤ L4 安全护栏（末尾，防注入）
        sb.append(version != null && version.getGuardrailPrompt() != null
                ? version.getGuardrailPrompt()
                : PromptConstants.GUARDRAIL_PROMPT);

        return sb.toString();
    }

    /**
     * 按意图取 L2 任务级 Prompt。
     *
     * MVP 阶段：HOW_TO/SEARCH 从 PromptVersion 取（支持灰度），NAVIGATE/CLARIFY/OUT_OF_SCOPE 用常量。
     * Advanced 阶段：可扩展 PromptVersion 实体支持全意图灰度。
     */
    private String getTaskPrompt(Intent intent, PromptVersion version) {
        if (version != null) {
            return switch (intent) {
                case HOW_TO -> version.getHowToPrompt() != null ? version.getHowToPrompt() : PromptConstants.HOW_TO_PROMPT;
                case SEARCH -> version.getSearchPrompt() != null ? version.getSearchPrompt() : PromptConstants.SEARCH_PROMPT;
                case NAVIGATE -> PromptConstants.NAVIGATE_PROMPT;
                case CLARIFY -> PromptConstants.CLARIFY_PROMPT;
                case OUT_OF_SCOPE -> PromptConstants.OUT_OF_SCOPE_PROMPT;
            };
        }
        return switch (intent) {
            case HOW_TO -> PromptConstants.HOW_TO_PROMPT;
            case SEARCH -> PromptConstants.SEARCH_PROMPT;
            case NAVIGATE -> PromptConstants.NAVIGATE_PROMPT;
            case CLARIFY -> PromptConstants.CLARIFY_PROMPT;
            case OUT_OF_SCOPE -> PromptConstants.OUT_OF_SCOPE_PROMPT;
        };
    }

    /**
     * 格式化检索结果为 [1] 标题 / 内容 形式。
     *
     * 迁移自 AgentChatService.formatRetrievalContext，保持兼容。
     */
    private String formatRetrievalContext(List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("---\n");
            sb.append("[").append(i + 1).append("] 来源：")
              .append(r.source() == RetrievalResult.Source.KNOWLEDGE ? "知识库" : "帖子")
              .append(" | 标题：").append(r.title()).append("\n");
            sb.append("内容：").append(r.content() != null ? r.content() : "无内容").append("\n");
        }
        sb.append("---");
        return sb.toString();
    }
}
