package com.campushare.agent.dto;

/**
 * 上下文分层枚举（ADR-071）。
 *
 * 按"重要性 + 可压缩性"分 6 层，自顶向下装入 LLM 上下文窗口：
 *
 * L0 System Prompt     — 永驻，不可压缩（平台级 + 任务级 Prompt）
 * L1 用户画像           — 按需装载，可裁剪（长期记忆摘要）
 * L2 工具定义           — 永驻，不可压缩（Function Calling schema）
 * L3 检索结果           — 按需装载，高可压缩（RAG 检索 + 工具调用结果）
 * L4 对话历史           — 永驻最近 N 轮，中可压缩（短期记忆窗口）
 * L5 用户输入           — 永驻，不可压缩（当前轮 query + 意图分类结果）
 *
 * 总预算 8000 tokens。L0+L2+L5 ≈ 2000 是硬性下限，剩余 6000 在 L1/L3/L4 间动态分配。
 */
public enum ContextLayer {
    L0_SYSTEM("System Prompt", 1000, false),
    L1_USER_PROFILE("用户画像", 300, true),
    L2_TOOL_DEFS("工具定义", 500, false),
    L3_RETRIEVAL("检索结果", 3000, true),
    L4_HISTORY("对话历史", 2500, true),
    L5_USER_INPUT("用户输入", 700, false);

    private final String displayName;
    private final int defaultTokenBudget;
    private final boolean compressible;

    ContextLayer(String displayName, int defaultTokenBudget, boolean compressible) {
        this.displayName = displayName;
        this.defaultTokenBudget = defaultTokenBudget;
        this.compressible = compressible;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultTokenBudget() {
        return defaultTokenBudget;
    }

    public boolean isCompressible() {
        return compressible;
    }
}
