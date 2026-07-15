package com.campushare.agent.intent;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RouteDecision;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.service.IntentRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentRouter 单元测试。
 *
 * 验证点：
 *  - OUT_OF_SCOPE 4 子意图模板回复
 *  - NAVIGATE my_list 路由映射
 *  - HOW_TO/SEARCH/CLARIFY 返回 empty
 *  - null 输入安全处理
 */
@DisplayName("IntentRouter 单元测试")
class IntentRouterTest {

    private final IntentRouter router = new IntentRouter();

    // ========== OUT_OF_SCOPE 快路径 ==========

    @Test
    @DisplayName("OUT_OF_SCOPE/chitchat → 返回闲聊模板")
    void shortCircuit_outOfScopeChitchat_returnsTemplate() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.CHITCHAT)
                .confidence(0.99)
                .rewrittenQuery("你好")
                .classifyLayer("RULE")
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        RouteDecision decision = result.get();
        assertThat(decision.isShortCircuit()).isTrue();
        assertThat(decision.getTemplateReply()).contains("CampusShare AI 助手小享");
        assertThat(decision.getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/write_action → 返回写操作拒绝模板")
    void shortCircuit_outOfScopeWriteAction_returnsTemplate() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.WRITE_ACTION)
                .confidence(0.99)
                .rewrittenQuery("帮我发帖")
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        assertThat(result.get().getTemplateReply()).contains("无法代替你执行");
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/open_domain → 不短接，走 RAG")
    void shortCircuit_outOfScopeOpenDomain_returnsNonShortCircuit() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.OPEN_DOMAIN)
                .confidence(0.85)
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        RouteDecision decision = result.get();
        assertThat(decision.isShortCircuit()).isFalse();
        assertThat(decision.getTemplateReply()).isNullOrEmpty();
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/sensitive → 返回敏感话题拒绝模板")
    void shortCircuit_outOfScopeSensitive_returnsTemplate() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.SENSITIVE)
                .confidence(0.95)
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        assertThat(result.get().getTemplateReply()).contains("无法回答");
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/未知子意图 → 不短接，走 RAG")
    void shortCircuit_outOfScopeUnknownSubIntent_returnsNonShortCircuit() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent("unknown_subtype")
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        RouteDecision decision = result.get();
        assertThat(decision.isShortCircuit()).isFalse();
        assertThat(decision.getTemplateReply()).isNullOrEmpty();
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/null 子意图 → 不短接，走 RAG")
    void shortCircuit_outOfScopeNullSubIntent_returnsNonShortCircuit() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(null)
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        RouteDecision decision = result.get();
        assertThat(decision.isShortCircuit()).isFalse();
        assertThat(decision.getTemplateReply()).isNullOrEmpty();
    }

    // ========== NAVIGATE 快路径 ==========

    @Test
    @DisplayName("NAVIGATE/my_list + query含「点赞」→ 跳转 /profile/liked")
    void shortCircuit_navigateMyListLiked_returnsRoute() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.NAVIGATE)
                .subIntent(Intent.SubIntent.MY_LIST)
                .confidence(0.95)
                .rewrittenQuery("我点赞的帖子")
                .build();

        Optional<RouteDecision> result = router.tryShortCircuit(intent);

        assertThat(result).isPresent();
        RouteDecision decision = result.get();
        assertThat(decision.isShortCircuit()).isTrue();
        assertThat(decision.getNavigateRoute()).isEqualTo("/profile/liked");
        assertThat(decision.getTemplateReply()).contains("查看你的内容");
        assertThat(decision.getTemplateReply()).contains("点击");
    }

    @Test
    @DisplayName("NAVIGATE/my_list + query含「收藏」→ 跳转 /profile/starred")
    void shortCircuit_navigateMyListFavorited_returnsRoute() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.NAVIGATE)
                .subIntent(Intent.SubIntent.MY_LIST)
                .rewrittenQuery("我收藏的帖子")
                .build();

        RouteDecision decision = router.tryShortCircuit(intent).orElseThrow();
        assertThat(decision.getNavigateRoute()).isEqualTo("/profile/starred");
    }

    @Test
    @DisplayName("NAVIGATE/my_list + query含「浏览」→ 跳转 /profile/history")
    void shortCircuit_navigateMyListHistory_returnsRoute() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.NAVIGATE)
                .subIntent(Intent.SubIntent.MY_LIST)
                .rewrittenQuery("我浏览历史")
                .build();

        RouteDecision decision = router.tryShortCircuit(intent).orElseThrow();
        assertThat(decision.getNavigateRoute()).isEqualTo("/profile/history");
    }

    @Test
    @DisplayName("NAVIGATE/my_list + query 无匹配关键词 → 默认 /profile")
    void shortCircuit_navigateMyListNoMatch_returnsDefaultRoute() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.NAVIGATE)
                .subIntent(Intent.SubIntent.MY_LIST)
                .rewrittenQuery("我的东西")
                .build();

        RouteDecision decision = router.tryShortCircuit(intent).orElseThrow();
        assertThat(decision.getNavigateRoute()).isEqualTo("/profile");
    }

    // ========== HOW_TO/SEARCH/CLARIFY 返回 empty ==========

    @Test
    @DisplayName("HOW_TO → 返回 empty（走 RAG）")
    void shortCircuit_howTo_returnsEmpty() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.HOW_TO)
                .subIntent(Intent.SubIntent.FEATURE_HELP)
                .confidence(0.85)
                .build();

        assertThat(router.tryShortCircuit(intent)).isEmpty();
    }

    @Test
    @DisplayName("SEARCH → 返回 empty（走 RAG）")
    void shortCircuit_search_returnsEmpty() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.9)
                .build();

        assertThat(router.tryShortCircuit(intent)).isEmpty();
    }

    @Test
    @DisplayName("CLARIFY → 返回 empty（走 RAG）")
    void shortCircuit_clarify_returnsEmpty() {
        IntentResult intent = IntentResult.builder()
                .intent(Intent.CLARIFY)
                .subIntent(Intent.SubIntent.COREFERENCE)
                .confidence(0.95)
                .build();

        assertThat(router.tryShortCircuit(intent)).isEmpty();
    }

    // ========== 边界情况 ==========

    @Test
    @DisplayName("null IntentResult → 返回 empty")
    void shortCircuit_null_returnsEmpty() {
        assertThat(router.tryShortCircuit(null)).isEmpty();
    }
}
