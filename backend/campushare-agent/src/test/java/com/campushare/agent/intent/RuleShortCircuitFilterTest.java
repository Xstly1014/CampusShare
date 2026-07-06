package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.service.RuleShortCircuitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleShortCircuitFilter 单元测试。
 *
 * 验证点：
 *  - 4 类规则命中（指代词/写操作/闲聊/个人列表）
 *  - 优先级：COREFERENCE > WRITE_ACTION > CHITCHAT > MY_LIST
 *  - 空值/空白兜底返回 empty
 *  - 未命中返回 empty（进入 Layer 2 LLM 分类）
 *  - 命中结果 classifyLayer="RULE"
 */
@DisplayName("RuleShortCircuitFilter 单元测试")
class RuleShortCircuitFilterTest {

    private RuleShortCircuitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RuleShortCircuitFilter();
    }

    // ========== 空值/空白兜底 ==========

    @Test
    @DisplayName("null 输入：返回 empty")
    void filter_null_returnsEmpty() {
        assertThat(filter.filter(null)).isEmpty();
    }

    @Test
    @DisplayName("空白输入：返回 empty")
    void filter_blank_returnsEmpty() {
        assertThat(filter.filter("   ")).isEmpty();
    }

    @Test
    @DisplayName("空字符串输入：返回 empty")
    void filter_empty_returnsEmpty() {
        assertThat(filter.filter("")).isEmpty();
    }

    // ========== 指代词强制 CLARIFY（ADR-015） ==========

    @Test
    @DisplayName("指代词「那个」：返回 CLARIFY/coreference")
    void filter_coreference_那个() {
        Optional<IntentResult> result = filter.filter("那个有下载的");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.CLARIFY);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.COREFERENCE);
        assertThat(result.get().getConfidence()).isEqualTo(0.95);
        assertThat(result.get().getClassifyLayer()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("指代词「它」：返回 CLARIFY/coreference")
    void filter_coreference_它() {
        Optional<IntentResult> result = filter.filter("它的作者是谁");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.CLARIFY);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.COREFERENCE);
    }

    @Test
    @DisplayName("指代词「上面那个」：返回 CLARIFY/coreference")
    void filter_coreference_上面那个() {
        Optional<IntentResult> result = filter.filter("上面那个帖子说的对");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.CLARIFY);
    }

    @Test
    @DisplayName("指代词「这个」：返回 CLARIFY/coreference")
    void filter_coreference_这个() {
        Optional<IntentResult> result = filter.filter("这个怎么用");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.CLARIFY);
    }

    // ========== 写操作 OUT_OF_SCOPE/write_action ==========

    @Test
    @DisplayName("写操作「帮我发帖」：返回 OUT_OF_SCOPE/write_action")
    void filter_writeAction_帮我发帖() {
        Optional<IntentResult> result = filter.filter("帮我发帖");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.WRITE_ACTION);
        assertThat(result.get().getConfidence()).isEqualTo(0.99);
        assertThat(result.get().getClassifyLayer()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("写操作「帮我点赞」：返回 OUT_OF_SCOPE/write_action")
    void filter_writeAction_帮我点赞() {
        Optional<IntentResult> result = filter.filter("帮我点赞");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.WRITE_ACTION);
    }

    @Test
    @DisplayName("写操作「帮我改密码」：返回 OUT_OF_SCOPE/write_action")
    void filter_writeAction_帮我改密码() {
        Optional<IntentResult> result = filter.filter("帮我改密码");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.WRITE_ACTION);
    }

    @Test
    @DisplayName("写操作「代替我发」：返回 OUT_OF_SCOPE/write_action")
    void filter_writeAction_代替我发() {
        Optional<IntentResult> result = filter.filter("代替我发");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.WRITE_ACTION);
    }

    // ========== 闲聊 OUT_OF_SCOPE/chitchat ==========

    @Test
    @DisplayName("闲聊「你好」：返回 OUT_OF_SCOPE/chitchat")
    void filter_chitchat_你好() {
        Optional<IntentResult> result = filter.filter("你好");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
        assertThat(result.get().getConfidence()).isEqualTo(0.99);
        assertThat(result.get().getClassifyLayer()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("闲聊「谢谢」：返回 OUT_OF_SCOPE/chitchat")
    void filter_chitchat_谢谢() {
        Optional<IntentResult> result = filter.filter("谢谢");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
    }

    @Test
    @DisplayName("闲聊「你是谁」：返回 OUT_OF_SCOPE/chitchat")
    void filter_chitchat_你是谁() {
        Optional<IntentResult> result = filter.filter("你是谁");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
    }

    @Test
    @DisplayName("闲聊「hi」：返回 OUT_OF_SCOPE/chitchat（大小写不敏感）")
    void filter_chitchat_hi() {
        Optional<IntentResult> result = filter.filter("HI there");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
    }

    @Test
    @DisplayName("闲聊「早上好」：返回 OUT_OF_SCOPE/chitchat")
    void filter_chitchat_早上好() {
        Optional<IntentResult> result = filter.filter("早上好");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
    }

    // ========== 个人列表 NAVIGATE/my_list ==========

    @Test
    @DisplayName("个人列表「我点赞的帖子」：返回 NAVIGATE/my_list")
    void filter_myList_我点赞的() {
        Optional<IntentResult> result = filter.filter("我点赞的帖子");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.NAVIGATE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.MY_LIST);
        assertThat(result.get().getConfidence()).isEqualTo(0.95);
        assertThat(result.get().getClassifyLayer()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("个人列表「我收藏的帖子」：返回 NAVIGATE/my_list")
    void filter_myList_我收藏的() {
        Optional<IntentResult> result = filter.filter("我收藏的帖子");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.NAVIGATE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.MY_LIST);
    }

    @Test
    @DisplayName("个人列表「我的浏览历史」：返回 NAVIGATE/my_list")
    void filter_myList_我的浏览历史() {
        Optional<IntentResult> result = filter.filter("我的浏览历史");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.NAVIGATE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.MY_LIST);
    }

    @Test
    @DisplayName("个人列表「我的粉丝」：返回 NAVIGATE/my_list")
    void filter_myList_我的粉丝() {
        Optional<IntentResult> result = filter.filter("我的粉丝");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.NAVIGATE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.MY_LIST);
    }

    // ========== 未命中（应返回 empty，进入 Layer 2） ==========

    @Test
    @DisplayName("未命中「怎么发帖」：返回 empty（进入 LLM 分类）")
    void filter_unmatched_怎么发帖() {
        assertThat(filter.filter("怎么发帖")).isEmpty();
    }

    @Test
    @DisplayName("未命中「求清华操作系统卷子」：返回 empty（进入 LLM 分类）")
    void filter_unmatched_求清华卷子() {
        assertThat(filter.filter("求清华操作系统卷子")).isEmpty();
    }

    @Test
    @DisplayName("未命中「个人中心在哪」：返回 empty（进入 LLM 分类）")
    void filter_unmatched_个人中心在哪() {
        assertThat(filter.filter("个人中心在哪")).isEmpty();
    }

    @Test
    @DisplayName("未命中「今天天气怎么样」：返回 empty（进入 LLM 分类）")
    void filter_unmatched_今天天气() {
        assertThat(filter.filter("今天天气怎么样")).isEmpty();
    }

    // ========== 优先级测试 ==========

    @Test
    @DisplayName("优先级：指代词 + 写操作 → CLARIFY 优先（那个帮我发的帖子）")
    void filter_priority_coreferenceOverWriteAction() {
        // 「那个」是核心ference，「帮我发」是 write_action，但核心ference 优先
        Optional<IntentResult> result = filter.filter("那个帮我发的帖子");
        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.CLARIFY);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.COREFERENCE);
    }

    @Test
    @DisplayName("rewrittenQuery 保留原始查询")
    void filter_rewrittenQuery_preservesOriginal() {
        String query = "帮我发帖";
        Optional<IntentResult> result = filter.filter(query);
        assertThat(result).isPresent();
        assertThat(result.get().getRewrittenQuery()).isEqualTo(query);
    }
}
