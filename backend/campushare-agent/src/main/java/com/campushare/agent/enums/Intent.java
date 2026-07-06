package com.campushare.agent.enums;

import lombok.Getter;

/**
 * 意图枚举（5 大 L1 意图）。
 *
 * 替换旧 IntentDetector.Intent（3 分类），新增 NAVIGATE/CLARIFY/OUT_OF_SCOPE。
 * 子意图用 SubIntent 常量类（String），便于扩展（ADR-009）。
 */
@Getter
public enum Intent {
    HOW_TO("操作指引", 1),
    SEARCH("内容检索", 2),
    NAVIGATE("页面导航", 3),
    CLARIFY("多轮澄清", 4),
    OUT_OF_SCOPE("超范围", 5);

    private final String label;
    private final int code;

    Intent(String label, int code) {
        this.label = label;
        this.code = code;
    }

    /**
     * L2 子意图常量（14 个）。
     *
     * 用 String 而非枚举，便于扩展新子意图时不破坏现有代码。
     */
    public static final class SubIntent {
        public static final String FEATURE_HELP = "feature_help";
        public static final String RULE_EXPLAIN = "rule_explain";
        public static final String RESOURCE = "resource";
        public static final String DISCUSSION = "discussion";
        public static final String CONTENT_QA = "content_qa";
        public static final String FEATURE_LOC = "feature_loc";
        public static final String SECTION_LOC = "section_loc";
        public static final String MY_LIST = "my_list";
        public static final String COREFERENCE = "coreference";
        public static final String REFINE = "refine";
        public static final String FOLLOWUP = "followup";
        public static final String CHITCHAT = "chitchat";
        public static final String OPEN_DOMAIN = "open_domain";
        public static final String WRITE_ACTION = "write_action";
        public static final String SENSITIVE = "sensitive";

        private SubIntent() {
        }
    }
}
