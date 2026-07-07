package com.campushare.agent.prompt;

import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.enums.Intent;
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
 *  - 五种意图切换 L2 任务级 Prompt
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
    @DisplayName("空检索结果：输出降级提示 <context> 块（冷启动降级，问题 14）")
    void assemble_emptyResults_outputsDegradedContextBlock() {
        String result = assembler.assemble(Intent.OUT_OF_SCOPE, null);

        // 空检索时也输出 <context> 块，包含降级提示，帮助 LLM 在知识库冷启动时给出合理回答
        assertThat(result)
                .contains(PromptConstants.PLATFORM_PROMPT)
                .contains(PromptConstants.OUT_OF_SCOPE_PROMPT)
                .contains(PromptConstants.FEW_SHOT_PROMPT)
                .contains(PromptConstants.GUARDRAIL_PROMPT)
                .contains("<context>")
                .contains("</context>")
                .contains("# 参考资料")
                .contains("当前无可用检索结果");
    }

    @Test
    @DisplayName("非空检索结果：用 <context> 标签包裹")
    void assemble_withResults_contextWrapped() {
        RetrievalResult r = RetrievalResult.knowledge("k1", "测试文档", "这是测试内容", 0.9, Map.of());
        String result = assembler.assemble(Intent.HOW_TO, List.of(r));

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
        String result = assembler.assemble(Intent.HOW_TO, null);

        assertThat(result).contains(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.OUT_OF_SCOPE_PROMPT);
    }

    @Test
    @DisplayName("SEARCH 意图：包含 SEARCH_PROMPT")
    void assemble_search_intent() {
        String result = assembler.assemble(Intent.SEARCH, null);

        assertThat(result).contains(PromptConstants.SEARCH_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
    }

    @Test
    @DisplayName("OUT_OF_SCOPE 意图：包含 OUT_OF_SCOPE_PROMPT")
    void assemble_outOfScope_intent() {
        String result = assembler.assemble(Intent.OUT_OF_SCOPE, null);

        assertThat(result).contains(PromptConstants.OUT_OF_SCOPE_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
    }

    @Test
    @DisplayName("NAVIGATE 意图：包含 NAVIGATE_PROMPT")
    void assemble_navigate_intent() {
        String result = assembler.assemble(Intent.NAVIGATE, null);

        assertThat(result).contains(PromptConstants.NAVIGATE_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
    }

    @Test
    @DisplayName("CLARIFY 意图：包含 CLARIFY_PROMPT")
    void assemble_clarify_intent() {
        String result = assembler.assemble(Intent.CLARIFY, null);

        assertThat(result).contains(PromptConstants.CLARIFY_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.HOW_TO_PROMPT);
        assertThat(result).doesNotContain(PromptConstants.SEARCH_PROMPT);
    }

    @Test
    @DisplayName("L4 护栏在末尾：L4 是最后一段")
    void assemble_guardrailAtEnd() {
        RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content", 0.9, Map.of());
        String result = assembler.assemble(Intent.OUT_OF_SCOPE, List.of(r));

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

        String result = assembler.assemble(Intent.HOW_TO, null, version);

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

        // OUT_OF_SCOPE 意图：PromptVersion 无 outOfScopePrompt 字段，始终降级到 PromptConstants
        String result = assembler.assemble(Intent.OUT_OF_SCOPE, null, version);

        assertThat(result)
                .contains(PromptConstants.PLATFORM_PROMPT)
                .contains(PromptConstants.OUT_OF_SCOPE_PROMPT)
                .contains(PromptConstants.FEW_SHOT_PROMPT)
                .contains(PromptConstants.GUARDRAIL_PROMPT);
    }

    @Nested
    @DisplayName("检索结果 metadata 展示")
    class MetadataDisplay {

        @Test
        @DisplayName("headingPath → 展示「章节」信息")
        void assemble_withHeadingPath_showsSection() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "发帖指南", "点击加号按钮",
                    0.9, Map.of("headingPath", "使用指南 > 发帖 > 步骤"));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).contains("章节：使用指南 > 发帖 > 步骤");
        }

        @Test
        @DisplayName("qualityScore >= 0.8 → 展示「可信度：高」")
        void assemble_withHighQuality_showsHighConfidence() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("qualityScore", 0.85));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).contains("可信度：高");
        }

        @Test
        @DisplayName("qualityScore 0.5-0.8 → 展示「可信度：中」")
        void assemble_withMediumQuality_showsMediumConfidence() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("qualityScore", 0.6));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).contains("可信度：中");
        }

        @Test
        @DisplayName("qualityScore < 0.5 → 展示「可信度：低」")
        void assemble_withLowQuality_showsLowConfidence() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("qualityScore", 0.3));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).contains("可信度：低");
        }

        @Test
        @DisplayName("chunkHits > 1 → 展示「命中分块数」")
        void assemble_withChunkHits_showsHitCount() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("chunkHits", 3));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).contains("命中分块数：3");
        }

        @Test
        @DisplayName("chunkHits = 1 → 不展示「命中分块数」")
        void assemble_withSingleChunkHit_doesNotShowHitCount() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("chunkHits", 1));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).doesNotContain("命中分块数");
        }

        @Test
        @DisplayName("帖子结果 + category/school → 展示「分类」「学校」")
        void assemble_withPostMetadata_showsCategoryAndSchool() {
            RetrievalResult r = RetrievalResult.post("p1", "清华操作系统卷子", "含5道大题",
                    0.92, Map.of("category", "计科", "school", "清华"));
            String result = assembler.assemble(Intent.SEARCH, List.of(r));

            assertThat(result)
                    .contains("分类：计科")
                    .contains("学校：清华");
        }

        @Test
        @DisplayName("知识库结果不展示「分类」「学校」（仅帖子展示）")
        void assemble_knowledgeResult_doesNotShowCategorySchool() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "doc", "content",
                    0.9, Map.of("category", "计科", "school", "清华"));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result).doesNotContain("分类：");
            assertThat(result).doesNotContain("学校：");
        }

        @Test
        @DisplayName("无 metadata → 仅展示标题和内容")
        void assemble_withoutMetadata_showsOnlyTitleAndContent() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "简单文档", "简单内容", 0.9, Map.of());
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result)
                    .contains("标题：简单文档")
                    .contains("内容：简单内容")
                    .doesNotContain("章节：")
                    .doesNotContain("可信度：")
                    .doesNotContain("命中分块数");
        }

        @Test
        @DisplayName("完整 metadata → 展示所有字段")
        void assemble_withAllMetadata_showsAllFields() {
            RetrievalResult r = RetrievalResult.knowledge("k1", "完整文档", "完整内容",
                    0.95, Map.of(
                            "headingPath", "指南 > 章节",
                            "qualityScore", 0.9,
                            "chunkHits", 2
                    ));
            String result = assembler.assemble(Intent.HOW_TO, List.of(r));

            assertThat(result)
                    .contains("章节：指南 > 章节")
                    .contains("可信度：高")
                    .contains("命中分块数：2")
                    .contains("标题：完整文档")
                    .contains("内容：完整内容");
        }

        @Test
        @DisplayName("多个结果 → 每个都有编号和分隔线")
        void assemble_multipleResults_eachNumbered() {
            RetrievalResult r1 = RetrievalResult.knowledge("k1", "文档1", "内容1", 0.9, Map.of());
            RetrievalResult r2 = RetrievalResult.post("p1", "帖子2", "内容2", 0.8, Map.of());
            String result = assembler.assemble(Intent.SEARCH, List.of(r1, r2));

            assertThat(result)
                    .contains("[1]")
                    .contains("[2]")
                    .contains("文档1")
                    .contains("帖子2");
        }
    }

    @Nested
    @DisplayName("6 要素顺序验证")
    class OrderValidation {

        @Test
        @DisplayName("L1 在 L2 之前")
        void l1BeforeL2() {
            String result = assembler.assemble(Intent.HOW_TO, null);
            int l1Pos = result.indexOf("角色定义");
            int l2Pos = result.indexOf("当前任务");
            assertThat(l1Pos).isLessThan(l2Pos);
        }

        @Test
        @DisplayName("L2 在 L3 之前")
        void l2BeforeL3() {
            String result = assembler.assemble(Intent.HOW_TO, null);
            int l2Pos = result.indexOf("当前任务");
            int l3Pos = result.indexOf("# 示例");
            assertThat(l2Pos).isLessThan(l3Pos);
        }

        @Test
        @DisplayName("L3 在 L4 之前")
        void l3BeforeL4() {
            String result = assembler.assemble(Intent.HOW_TO, null);
            int l3Pos = result.indexOf("# 示例");
            int l4Pos = result.indexOf("# 安全规则");
            assertThat(l3Pos).isLessThan(l4Pos);
        }
    }
}
