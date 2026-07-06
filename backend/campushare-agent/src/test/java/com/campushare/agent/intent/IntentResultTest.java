package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentResult DTO 单元测试。
 *
 * 验证点：
 *  - isHighConfidence 边界（confidence >= 0.6 为 true）
 *  - isLowConfidence 边界（confidence < 0.6 为 true）
 *  - Builder 正确赋值
 *  - SlotResult Builder
 *
 * ADR-010：置信度阈值 0.6，>= 0.6 高置信直接用，< 0.6 低置信兜底 SEARCH。
 */
@DisplayName("IntentResult DTO 单元测试")
class IntentResultTest {

    // ========== isHighConfidence 边界 ==========

    @Nested
    @DisplayName("isHighConfidence：confidence >= 0.6 为 true")
    class IsHighConfidence {

        @Test
        @DisplayName("confidence=0.7 → true")
        void isHighConfidence_0_7_returnsTrue() {
            IntentResult result = IntentResult.builder().confidence(0.7).build();
            assertThat(result.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("confidence=0.6 边界值 → true（>= 0.6）")
        void isHighConfidence_0_6_returnsTrue() {
            IntentResult result = IntentResult.builder().confidence(0.6).build();
            assertThat(result.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("confidence=0.5 → false")
        void isHighConfidence_0_5_returnsFalse() {
            IntentResult result = IntentResult.builder().confidence(0.5).build();
            assertThat(result.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("confidence=0.0 → false")
        void isHighConfidence_0_returnsFalse() {
            IntentResult result = IntentResult.builder().confidence(0.0).build();
            assertThat(result.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("confidence=1.0 → true")
        void isHighConfidence_1_returnsTrue() {
            IntentResult result = IntentResult.builder().confidence(1.0).build();
            assertThat(result.isHighConfidence()).isTrue();
        }
    }

    // ========== isLowConfidence 边界 ==========

    @Nested
    @DisplayName("isLowConfidence：confidence < 0.6 为 true")
    class IsLowConfidence {

        @Test
        @DisplayName("confidence=0.5 → true")
        void isLowConfidence_0_5_returnsTrue() {
            IntentResult result = IntentResult.builder().confidence(0.5).build();
            assertThat(result.isLowConfidence()).isTrue();
        }

        @Test
        @DisplayName("confidence=0.6 边界值 → false（不 < 0.6）")
        void isLowConfidence_0_6_returnsFalse() {
            IntentResult result = IntentResult.builder().confidence(0.6).build();
            assertThat(result.isLowConfidence()).isFalse();
        }

        @Test
        @DisplayName("confidence=0.7 → false")
        void isLowConfidence_0_7_returnsFalse() {
            IntentResult result = IntentResult.builder().confidence(0.7).build();
            assertThat(result.isLowConfidence()).isFalse();
        }

        @Test
        @DisplayName("confidence=0.0 → true")
        void isLowConfidence_0_returnsTrue() {
            IntentResult result = IntentResult.builder().confidence(0.0).build();
            assertThat(result.isLowConfidence()).isTrue();
        }
    }

    // ========== Builder 正确性 ==========

    @Nested
    @DisplayName("Builder 字段赋值")
    class BuilderFields {

        @Test
        @DisplayName("完整 builder：所有字段正确赋值")
        void builder_buildsCorrectObject() {
            IntentResult.SlotResult slots = IntentResult.SlotResult.builder()
                    .school("清华")
                    .category("面经")
                    .postType("resource")
                    .sort("最新")
                    .build();

            IntentResult result = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.92)
                    .rewrittenQuery("操作系统 期末 卷子")
                    .slots(slots)
                    .hydeDoc(null)
                    .classifyLayer("LLM")
                    .build();

            assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
            assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.RESOURCE);
            assertThat(result.getConfidence()).isEqualTo(0.92);
            assertThat(result.getRewrittenQuery()).isEqualTo("操作系统 期末 卷子");
            assertThat(result.getSlots()).isEqualTo(slots);
            assertThat(result.getHydeDoc()).isNull();
            assertThat(result.getClassifyLayer()).isEqualTo("LLM");
        }

        @Test
        @DisplayName("rewrittenQuery 为 null：构建成功")
        void builder_withNullRewrittenQuery_buildsSuccessfully() {
            IntentResult result = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .subIntent(Intent.SubIntent.FEATURE_HELP)
                    .confidence(0.95)
                    .rewrittenQuery(null)
                    .classifyLayer("RULE")
                    .build();

            assertThat(result.getRewrittenQuery()).isNull();
            assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
        }

        @Test
        @DisplayName("slots 为 null：构建成功")
        void builder_withNullSlots_buildsSuccessfully() {
            IntentResult result = IntentResult.builder()
                    .intent(Intent.CLARIFY)
                    .subIntent(Intent.SubIntent.COREFERENCE)
                    .confidence(0.90)
                    .rewrittenQuery("那个")
                    .slots(null)
                    .classifyLayer("RULE")
                    .build();

            assertThat(result.getSlots()).isNull();
            assertThat(result.getIntent()).isEqualTo(Intent.CLARIFY);
        }

        @Test
        @DisplayName("SlotResult builder：所有字段正确赋值")
        void slotResult_builder_buildsCorrectObject() {
            IntentResult.SlotResult slots = IntentResult.SlotResult.builder()
                    .school("北大")
                    .category("游戏")
                    .postType("discussion")
                    .sort("最热")
                    .build();

            assertThat(slots.getSchool()).isEqualTo("北大");
            assertThat(slots.getCategory()).isEqualTo("游戏");
            assertThat(slots.getPostType()).isEqualTo("discussion");
            assertThat(slots.getSort()).isEqualTo("最热");
        }

        @Test
        @DisplayName("SlotResult 部分字段 null：构建成功")
        void slotResult_partialNull_buildsSuccessfully() {
            IntentResult.SlotResult slots = IntentResult.SlotResult.builder()
                    .school("复旦")
                    .build();

            assertThat(slots.getSchool()).isEqualTo("复旦");
            assertThat(slots.getCategory()).isNull();
            assertThat(slots.getPostType()).isNull();
            assertThat(slots.getSort()).isNull();
        }
    }
}
