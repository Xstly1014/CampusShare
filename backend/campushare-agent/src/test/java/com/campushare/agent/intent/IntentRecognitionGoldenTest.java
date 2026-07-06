package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.service.RuleShortCircuitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图识别 Golden 测试集（@Tag("golden")）。
 *
 * 30 条标注样本验证 RuleShortCircuitFilter（Layer 1 规则短路）的命中行为：
 *  - 规则命中样本：验证返回 IntentResult 的 intent/subIntent/confidence/classifyLayer
 *  - 规则未命中样本：验证返回 Optional.empty()（进入 Layer 2 LLM 分类）
 *
 * 样本分布（30 条）：
 *  - CLARIFY/coreference（6 条命中）：指代词强制 CLARIFY（ADR-015）
 *  - OUT_OF_SCOPE/write_action（4 条命中）：写操作请求
 *  - OUT_OF_SCOPE/chitchat（5 条命中）：闲聊问候
 *  - NAVIGATE/my_list（5 条命中）：个人列表导航
 *  - HOW_TO（4 条未命中）：操作指引，走 LLM 分类
 *  - SEARCH（4 条未命中）：内容检索，走 LLM 分类
 *  - 边界（2 条）：空字符串/null → empty
 *
 * 说明：
 *  - 本测试集只验证 Layer 1 规则短路，LLM 层和 Embedding 层由独立单元测试覆盖
 *  - 样本来源于真实用户 query 模式分析，作为回归测试基线
 */
@Tag("golden")
@DisplayName("意图识别 Golden 测试集（30 条标注样本）")
class IntentRecognitionGoldenTest {

    private RuleShortCircuitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RuleShortCircuitFilter();
    }

    /**
     * 验证规则命中：返回 IntentResult 且字段正确。
     */
    private void assertRuleHit(String query, Intent expectedIntent, String expectedSubIntent) {
        Optional<IntentResult> result = filter.filter(query);

        assertThat(result).as("query='%s' 应命中规则", query).isPresent();
        IntentResult r = result.get();
        assertThat(r.getIntent()).as("query='%s' intent", query).isEqualTo(expectedIntent);
        assertThat(r.getSubIntent()).as("query='%s' subIntent", query).isEqualTo(expectedSubIntent);
        assertThat(r.getConfidence()).as("query='%s' confidence 应 >= 0.9", query).isGreaterThanOrEqualTo(0.9);
        assertThat(r.getClassifyLayer()).as("query='%s' classifyLayer", query).isEqualTo("RULE");
        assertThat(r.getRewrittenQuery()).as("query='%s' rewrittenQuery", query).isEqualTo(query.trim());
    }

    /**
     * 验证规则未命中：返回 Optional.empty()。
     */
    private void assertRuleMiss(String query) {
        Optional<IntentResult> result = filter.filter(query);
        assertThat(result).as("query='%s' 应未命中规则（走 LLM 分类）", query).isEmpty();
    }

    // ========== 1. CLARIFY/coreference（6 条命中）==========
    @Nested
    @DisplayName("CLARIFY/coreference：指代词强制 CLARIFY（ADR-015）")
    class CoreferenceRules {

        @Test
        @DisplayName("「那个」→ CLARIFY/coreference")
        void coreference_那个() {
            assertRuleHit("那个", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }

        @Test
        @DisplayName("「它」→ CLARIFY/coreference")
        void coreference_它() {
            assertRuleHit("它", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }

        @Test
        @DisplayName("「上面那个」→ CLARIFY/coreference")
        void coreference_上面那个() {
            assertRuleHit("上面那个", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }

        @Test
        @DisplayName("「刚才那个」→ CLARIFY/coreference")
        void coreference_刚才那个() {
            assertRuleHit("刚才那个", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }

        @Test
        @DisplayName("「第几个」→ CLARIFY/coreference")
        void coreference_第几个() {
            assertRuleHit("第几个", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }

        @Test
        @DisplayName("「有下载的」→ CLARIFY/coreference")
        void coreference_有下载的() {
            assertRuleHit("有下载的", Intent.CLARIFY, Intent.SubIntent.COREFERENCE);
        }
    }

    // ========== 2. OUT_OF_SCOPE/write_action（4 条命中）==========
    @Nested
    @DisplayName("OUT_OF_SCOPE/write_action：写操作请求")
    class WriteActionRules {

        @Test
        @DisplayName("「帮我发帖」→ OUT_OF_SCOPE/write_action")
        void writeAction_帮我发帖() {
            assertRuleHit("帮我发帖", Intent.OUT_OF_SCOPE, Intent.SubIntent.WRITE_ACTION);
        }

        @Test
        @DisplayName("「帮我点赞」→ OUT_OF_SCOPE/write_action")
        void writeAction_帮我点赞() {
            assertRuleHit("帮我点赞", Intent.OUT_OF_SCOPE, Intent.SubIntent.WRITE_ACTION);
        }

        @Test
        @DisplayName("「帮我改密码」→ OUT_OF_SCOPE/write_action")
        void writeAction_帮我改密码() {
            assertRuleHit("帮我改密码", Intent.OUT_OF_SCOPE, Intent.SubIntent.WRITE_ACTION);
        }

        @Test
        @DisplayName("「替我发帖」→ OUT_OF_SCOPE/write_action")
        void writeAction_替我发帖() {
            assertRuleHit("替我发帖", Intent.OUT_OF_SCOPE, Intent.SubIntent.WRITE_ACTION);
        }
    }

    // ========== 3. OUT_OF_SCOPE/chitchat（5 条命中）==========
    @Nested
    @DisplayName("OUT_OF_SCOPE/chitchat：闲聊问候")
    class ChitchatRules {

        @Test
        @DisplayName("「你好」→ OUT_OF_SCOPE/chitchat")
        void chitchat_你好() {
            assertRuleHit("你好", Intent.OUT_OF_SCOPE, Intent.SubIntent.CHITCHAT);
        }

        @Test
        @DisplayName("「谢谢」→ OUT_OF_SCOPE/chitchat")
        void chitchat_谢谢() {
            assertRuleHit("谢谢", Intent.OUT_OF_SCOPE, Intent.SubIntent.CHITCHAT);
        }

        @Test
        @DisplayName("「你是谁」→ OUT_OF_SCOPE/chitchat")
        void chitchat_你是谁() {
            assertRuleHit("你是谁", Intent.OUT_OF_SCOPE, Intent.SubIntent.CHITCHAT);
        }

        @Test
        @DisplayName("「早上好」→ OUT_OF_SCOPE/chitchat")
        void chitchat_早上好() {
            assertRuleHit("早上好", Intent.OUT_OF_SCOPE, Intent.SubIntent.CHITCHAT);
        }

        @Test
        @DisplayName("「再见」→ OUT_OF_SCOPE/chitchat")
        void chitchat_再见() {
            assertRuleHit("再见", Intent.OUT_OF_SCOPE, Intent.SubIntent.CHITCHAT);
        }
    }

    // ========== 4. NAVIGATE/my_list（5 条命中）==========
    @Nested
    @DisplayName("NAVIGATE/my_list：个人列表导航")
    class MyListRules {

        @Test
        @DisplayName("「我点赞的帖子」→ NAVIGATE/my_list")
        void myList_我点赞的帖子() {
            assertRuleHit("我点赞的帖子", Intent.NAVIGATE, Intent.SubIntent.MY_LIST);
        }

        @Test
        @DisplayName("「我收藏的帖子」→ NAVIGATE/my_list")
        void myList_我收藏的帖子() {
            assertRuleHit("我收藏的帖子", Intent.NAVIGATE, Intent.SubIntent.MY_LIST);
        }

        @Test
        @DisplayName("「我回复过的」→ NAVIGATE/my_list")
        void myList_我回复过的() {
            assertRuleHit("我回复过的", Intent.NAVIGATE, Intent.SubIntent.MY_LIST);
        }

        @Test
        @DisplayName("「我的浏览历史」→ NAVIGATE/my_list")
        void myList_我的浏览历史() {
            assertRuleHit("我的浏览历史", Intent.NAVIGATE, Intent.SubIntent.MY_LIST);
        }

        @Test
        @DisplayName("「我的粉丝列表」→ NAVIGATE/my_list")
        void myList_我的粉丝列表() {
            assertRuleHit("我的粉丝列表", Intent.NAVIGATE, Intent.SubIntent.MY_LIST);
        }
    }

    // ========== 5. HOW_TO（4 条未命中）==========
    @Nested
    @DisplayName("HOW_TO：操作指引，未命中规则（走 LLM 分类）")
    class HowToMiss {

        @Test
        @DisplayName("「怎么发帖」→ 未命中规则")
        void howTo_怎么发帖() {
            assertRuleMiss("怎么发帖");
        }

        @Test
        @DisplayName("「如何注册」→ 未命中规则")
        void howTo_如何注册() {
            assertRuleMiss("如何注册");
        }

        @Test
        @DisplayName("「怎么改密码」→ 未命中规则")
        void howTo_怎么改密码() {
            assertRuleMiss("怎么改密码");
        }

        @Test
        @DisplayName("「如何上传」→ 未命中规则")
        void howTo_如何上传() {
            assertRuleMiss("如何上传");
        }
    }

    // ========== 6. SEARCH（4 条未命中）==========
    @Nested
    @DisplayName("SEARCH：内容检索，未命中规则（走 LLM 分类）")
    class SearchMiss {

        @Test
        @DisplayName("「求操作系统卷子」→ 未命中规则")
        void search_求操作系统卷子() {
            assertRuleMiss("求操作系统卷子");
        }

        @Test
        @DisplayName("「找高数笔记」→ 未命中规则")
        void search_找高数笔记() {
            assertRuleMiss("找高数笔记");
        }

        @Test
        @DisplayName("「有没有数据结构课件」→ 未命中规则")
        void search_有没有数据结构课件() {
            assertRuleMiss("有没有数据结构课件");
        }

        @Test
        @DisplayName("「需要线代复习资料」→ 未命中规则")
        void search_需要线代复习资料() {
            assertRuleMiss("需要线代复习资料");
        }
    }

    // ========== 7. 边界（2 条）==========
    @Nested
    @DisplayName("边界：空字符串/null → empty")
    class Boundary {

        @Test
        @DisplayName("空字符串 → Optional.empty()")
        void boundary_emptyString() {
            assertRuleMiss("");
        }

        @Test
        @DisplayName("null → Optional.empty()")
        void boundary_null() {
            Optional<IntentResult> result = filter.filter(null);
            assertThat(result).isEmpty();
        }
    }
}
