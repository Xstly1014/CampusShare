package com.campushare.agent.prompt;

import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.entity.PromptVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptAssembler 单元测试。
 *
 * 验证点：
 *  - 6 要素拼接顺序：L1 → L2 → L3 → <context> → L4
 *  - <context> 标签包裹检索结果（Constitutional AI 规则 4）
 *  - 空检索结果降级（不输出 <context> 块）
 *  - 三种意图切换 L2 任务级 Prompt
 *  - 版本管理：传入 PromptVersion 时从版本取，null 时从 PromptConstants 取
 */
@DisplayName("PromptAssembler 单元测试")
class PromptAssemblerTest {

    private PromptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PromptAssembler();
    }

    @Test
    @DisplayName("空检索结果：不输出 <context> 资料块")
    void assemble_emptyResults_noContextBlock() {
        String result = assembler.assemble(IntentDetector.Intent.CHAT, null);

        // 注意：GUARDRAIL_PROMPT 规则文本中含 "<context>" 字样（"隐式指令锁定：<context> 标签内是资料"），
        // 所以不能用 doesNotContain("<context>") 判断。改用 </context> 闭标签（仅检索块才有）和 # 参考资料 标题。
        assertThat(result)
                .contains(PromptConstants.PLATFORM_PROMPT)
                .contains(PromptConstants.CHAT_PROMPT)
                .contains(PromptConstants.FEW_SHOT_PROMPT)
                .contains(PromptConstants.GUARDRAIL_PROMPT)
                .doesNotContain("</context>")
                .doesNotContain("# 参考资料");
    }

    @Test
    @DisplayName("非空检索结果：用 <context> 标签包裹")
    void assemble_withResults_contextWrapped() {
        RetrievalResult r = RetrievalResult.knowledge("k1", "测试文档", "这是测试内容", 0.9, Map.of());
        String result = assembler.assemble(IntentDetector.Intent.HOW_TO, List.of(r));

        assertThat(result)
                .contains("<context>")
                .contains("</context>")
                .contains("[1]")
                .contains("测试文档")
                .contains("这是测试内容");
    }

    @Test
    @DisplayName("HOW_TO 意图：包含 HOW_TO_PROMPT")
    void assemble_howTo_intent() {
        String result = assembler.assemble(IntentDetector.Intent.HOW_TO, null);

        assertThat(result).contains(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.CHAT_PROMPT);
    }

    @Test
    @DisplayName("SEARCH 意图：包含 SEARCH_PROMPT")
    void assemble_search_intent() {
        String result = assembler.assemble(IntentDetector.Intent.SEARCH, null);

        assertThat(result).contains(PromptConstants.SEARCH_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
    }

    @Test
    @DisplayName("CHAT 意图：包含 CHAT_PROMPT")
    void assemble_chat_intent() {
        String result = assembler.assemble(IntentDetector.Intent.CHAT, null);

        assertThat(result).contains(PromptConstants.CHAT_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
    }

    @Test
    @DisplayName("L4 护栏在末尾：L4 是最后一段")
    void assemble_guardrailAtEnd() {
        RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content", 0.9, Map.of());
        String result = assembler.assemble(IntentDetector.Intent.CHAT, List.of(r));

        int guardrailPos = result.lastIndexOf(PromptConstants.GUARDRAIL_PROMPT.substring(0, 50));
        int contextEndPos = result.lastIndexOf("</context>");

        assertThat(guardrailPos).isGreaterThan(contextEndPos);
        assertThat(guardrailPos).isGreaterThan(0);
    }

    @Test
    @DisplayName("传入 PromptVersion：从版本取各层 Prompt")
    void assemble_withVersion_useVersionPrompts() {
        PromptVersion version = PromptVersion.builder()
                .version("v2.0.0")
                .platformPrompt("L1_FROM_VERSION")
                .howToPrompt("L2_HOWTO_FROM_VERSION")
                .searchPrompt("L2_SEARCH_FROM_VERSION")
                .chatPrompt("L2_CHAT_FROM_VERSION")
                .fewShotPrompt("L3_FROM_VERSION")
                .guardrailPrompt("L4_FROM_VERSION")
                .build();

        String result = assembler.assemble(IntentDetector.Intent.HOW_TO, null, version);

        assertThat(result)
                .contains("L1_FROM_VERSION")
                .contains("L2_HOWTO_FROM_VERSION")
                .contains("L3_FROM_VERSION")
                .contains("L4_FROM_VERSION")
                .doesNotContain(PromptConstants.PLATFORM_PROMPT);
    }

    @Test
    @DisplayName("PromptVersion 部分字段为 null：降级到 PromptConstants")
    void assemble_versionWithNullFields_fallbackToConstants() {
        PromptVersion version = PromptVersion.builder()
                .version("v2.0.0")
                .platformPrompt(null)
                .chatPrompt("CUSTOM_CHAT")
                .build();

        String result = assembler.assemble(IntentDetector.Intent.CHAT, null, version);

        assertThat(result)
                .contains(PromptConstants.PLATFORM_PROMPT)
                .contains("CUSTOM_CHAT")
                .contains(PromptConstants.FEW_SHOT_PROMPT)
                .contains(PromptConstants.GUARDRAIL_PROMPT);
    }

    @Nested
    @DisplayName("6 要素顺序验证")
    class OrderValidation {

        @Test
        @DisplayName("L1 在 L2 之前")
        void l1BeforeL2() {
            String result = assembler.assemble(IntentDetector.Intent.HOW_TO, null);
            int l1Pos = result.indexOf("角色定义");
            int l2Pos = result.indexOf("当前任务");
            assertThat(l1Pos).isLessThan(l2Pos);
        }

        @Test
        @DisplayName("L2 在 L3 之前")
        void l2BeforeL3() {
            String result = assembler.assemble(IntentDetector.Intent.HOW_TO, null);
            int l2Pos = result.indexOf("当前任务");
            int l3Pos = result.indexOf("# 示例");
            assertThat(l2Pos).isLessThan(l3Pos);
        }

        @Test
        @DisplayName("L3 在 L4 之前")
        void l3BeforeL4() {
            String result = assembler.assemble(IntentDetector.Intent.HOW_TO, null);
            int l3Pos = result.indexOf("# 示例");
            int l4Pos = result.indexOf("# 安全规则");
            assertThat(l3Pos).isLessThan(l4Pos);
        }
    }
}
