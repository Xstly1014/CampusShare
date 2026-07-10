package com.campushare.agent.dto;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;

/**
 * Token 预算分配（ADR-071）。
 *
 * 按"重要性 + 可压缩性"分 6 层，总预算 8000 tokens。
 * 其中输出预留 500 tokens（outputReserve），实际输入可用 7500 tokens。
 * L0+L2+L5 ≈ 2200 是硬性下限，剩余 5300 在 L1/L3/L4 间动态分配。
 *
 * 静态分配（默认）：
 *   L0=1000  L1=300  L2=500  L3=3000  L4=2000  L5=700  total=8000  outputReserve=500  maxInput=7500
 *
 * 动态再分配（按意图）：
 *   HOW_TO  → L3=4000 L4=1000（知识片段优先，历史可压缩）
 *   SEARCH  → L3=3500 L4=1500（帖子摘要 + 历史指代消解）
 *   NAVIGATE→ L3=1000 L4=2500（几乎不需要检索，靠历史）
 *   CLARIFY → L3=500  L4=3000（历史是核心）
 *
 * @param l0System      System Prompt 预算（固定 1000）
 * @param l1Profile     用户画像预算（固定 300）
 * @param l2ToolDefs    工具定义预算（固定 500）
 * @param l3Retrieval   检索结果预算（动态，按意图调整）
 * @param l4History     对话历史预算（动态，按意图调整）
 * @param l5UserInput   用户输入预算（固定 700）
 * @param total         总预算（8000 = maxInput + outputReserve）
 * @param outputReserve 输出预留 tokens（固定 500，防止输出截断）
 */
public record TokenBudget(
        int l0System,
        int l1Profile,
        int l2ToolDefs,
        int l3Retrieval,
        int l4History,
        int l5UserInput,
        int total,
        int outputReserve
) {

    private static final int OUTPUT_RESERVE = 500;
    private static final int TOTAL_BUDGET = 8000;

    /** 默认静态分配：L4=2000（为输出预留500） */
    public static final TokenBudget DEFAULT = new TokenBudget(
            1000, 300, 500, 3000, 2000, 700, TOTAL_BUDGET, OUTPUT_RESERVE);

    /** 输入最大可用 tokens = total - outputReserve */
    public int maxInput() {
        return total - outputReserve;
    }

    /**
     * 按意图选择 L3/L4 动态分配策略（ADR-047）。
     *
     * @param intent 意图识别结果（可为 null，走默认配置）
     * @return 该意图对应的 Token 预算分配
     */
    public static TokenBudget forIntent(IntentResult intent) {
        if (intent == null || intent.getIntent() == null) {
            return DEFAULT;
        }
        return switch (intent.getIntent()) {
            case HOW_TO -> new TokenBudget(1000, 300, 500, 4000, 1000, 700, TOTAL_BUDGET, OUTPUT_RESERVE);
            case SEARCH -> new TokenBudget(1000, 300, 500, 3500, 1500, 700, TOTAL_BUDGET, OUTPUT_RESERVE);
            case NAVIGATE -> new TokenBudget(1000, 300, 500, 1000, 2500, 700, TOTAL_BUDGET, OUTPUT_RESERVE);
            case CLARIFY -> new TokenBudget(1000, 300, 500, 500, 3000, 700, TOTAL_BUDGET, OUTPUT_RESERVE);
            case OUT_OF_SCOPE -> DEFAULT;
        };
    }
}
