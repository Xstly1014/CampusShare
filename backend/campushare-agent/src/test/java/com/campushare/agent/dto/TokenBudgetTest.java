package com.campushare.agent.dto;

import com.campushare.agent.enums.Intent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenBudget 单元测试（ADR-071）。
 *
 * 验证点：
 *  - DEFAULT 预算总和 = 8000
 *  - HOW_TO：L3 优先（4000），L4 压缩（1500）
 *  - SEARCH：L3/L4 均衡（3500/2000）
 *  - NAVIGATE：L4 优先（3000），L3 压缩（1000）
 *  - CLARIFY：L4 最大化（4000），L3 最小化（500）
 *  - null intent → DEFAULT
 */
@DisplayName("TokenBudget 单元测试")
class TokenBudgetTest {

    @Nested
    @DisplayName("DEFAULT 预算")
    class DefaultBudget {

        @Test
        @DisplayName("DEFAULT 总预算 = 8000")
        void default_totalIs8000() {
            assertThat(TokenBudget.DEFAULT.total()).isEqualTo(8000);
        }

        @Test
        @DisplayName("DEFAULT 各层分配正确")
        void default_layerAllocation() {
            TokenBudget b = TokenBudget.DEFAULT;
            assertThat(b.l0System()).isEqualTo(1000);
            assertThat(b.l1Profile()).isEqualTo(300);
            assertThat(b.l2ToolDefs()).isEqualTo(500);
            assertThat(b.l3Retrieval()).isEqualTo(3000);
            assertThat(b.l4History()).isEqualTo(2500);
            assertThat(b.l5UserInput()).isEqualTo(700);
        }
    }

    @Nested
    @DisplayName("按意图分配")
    class IntentBasedBudget {

        private IntentResult intent(Intent type) {
            return IntentResult.builder()
                    .intent(type)
                    .confidence(0.9)
                    .classifyLayer("RULE")
                    .build();
        }

        @Test
        @DisplayName("HOW_TO：L3=4000 L4=1500（检索优先）")
        void forIntent_howTo_retrievalPriority() {
            TokenBudget b = TokenBudget.forIntent(intent(Intent.HOW_TO));
            assertThat(b.l3Retrieval()).isEqualTo(4000);
            assertThat(b.l4History()).isEqualTo(1500);
            assertThat(b.total()).isEqualTo(8000);
        }

        @Test
        @DisplayName("SEARCH：L3=3500 L4=2000（均衡）")
        void forIntent_search_balanced() {
            TokenBudget b = TokenBudget.forIntent(intent(Intent.SEARCH));
            assertThat(b.l3Retrieval()).isEqualTo(3500);
            assertThat(b.l4History()).isEqualTo(2000);
            assertThat(b.total()).isEqualTo(8000);
        }

        @Test
        @DisplayName("NAVIGATE：L3=1000 L4=3000（历史优先）")
        void forIntent_navigate_historyPriority() {
            TokenBudget b = TokenBudget.forIntent(intent(Intent.NAVIGATE));
            assertThat(b.l3Retrieval()).isEqualTo(1000);
            assertThat(b.l4History()).isEqualTo(3000);
            assertThat(b.total()).isEqualTo(8000);
        }

        @Test
        @DisplayName("CLARIFY：L3=500 L4=4000（历史最大化）")
        void forIntent_clarify_historyMaximized() {
            TokenBudget b = TokenBudget.forIntent(intent(Intent.CLARIFY));
            assertThat(b.l3Retrieval()).isEqualTo(500);
            assertThat(b.l4History()).isEqualTo(4000);
            assertThat(b.total()).isEqualTo(8000);
        }

        @Test
        @DisplayName("OUT_OF_SCOPE：使用 DEFAULT 预算")
        void forIntent_outOfScope_usesDefault() {
            TokenBudget b = TokenBudget.forIntent(intent(Intent.OUT_OF_SCOPE));
            assertThat(b).isEqualTo(TokenBudget.DEFAULT);
        }
    }

    @Nested
    @DisplayName("null/边界处理")
    class NullAndBoundary {

        @Test
        @DisplayName("null IntentResult → DEFAULT")
        void forIntent_null_usesDefault() {
            TokenBudget b = TokenBudget.forIntent(null);
            assertThat(b).isEqualTo(TokenBudget.DEFAULT);
        }

        @Test
        @DisplayName("IntentResult intent 为 null → DEFAULT")
        void forIntent_nullIntentField_usesDefault() {
            IntentResult ir = IntentResult.builder()
                    .intent(null)
                    .confidence(0.5)
                    .build();
            TokenBudget b = TokenBudget.forIntent(ir);
            assertThat(b).isEqualTo(TokenBudget.DEFAULT);
        }

        @Test
        @DisplayName("所有意图的 L0/L1/L2/L5 和 total 都相同")
        void forIntent_allIntents_sharedFieldsIdentical() {
            int sharedL0 = 1000, sharedL1 = 300, sharedL2 = 500, sharedL5 = 700, sharedTotal = 8000;
            for (Intent intent : Intent.values()) {
                IntentResult ir = IntentResult.builder().intent(intent).confidence(0.9).build();
                TokenBudget b = TokenBudget.forIntent(ir);
                assertThat(b.l0System()).isEqualTo(sharedL0);
                assertThat(b.l1Profile()).isEqualTo(sharedL1);
                assertThat(b.l2ToolDefs()).isEqualTo(sharedL2);
                assertThat(b.l5UserInput()).isEqualTo(sharedL5);
                assertThat(b.total()).isEqualTo(sharedTotal);
            }
        }
    }
}
