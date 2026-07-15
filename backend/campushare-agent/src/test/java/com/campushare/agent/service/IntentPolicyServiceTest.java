package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentPolicyService 单元测试。
 *
 * 验证意图策略层的三条核心规则：
 *  - 资源请求降级
 *  - 指代词强制 CLARIFY
 *  - 昵称声明打标
 * 以及置信度保持与幂等性。
 */
@DisplayName("IntentPolicyService 意图策略测试")
class IntentPolicyServiceTest {

    private IntentPolicyService policyService;

    private static final String SESSION_ID = "test-session-001";

    @BeforeEach
    void setUp() {
        policyService = new IntentPolicyService();
    }

    private IntentResult rawOutOfScopeOpenDomain(double confidence) {
        return IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.OPEN_DOMAIN)
                .confidence(confidence)
                .rewrittenQuery("rewritten")
                .classifyLayer("LLM")
                .build();
    }

    @Test
    @DisplayName("OUT_OF_SCOPE/open_domain 且含资源关键词 → 降级为 SEARCH/resource")
    void resourceDowngrade_openDomainWithKeyword_becomesSearchResource() {
        IntentResult raw = rawOutOfScopeOpenDomain(0.75);

        IntentResult result = policyService.applyPolicy(raw, "我想学 Python", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.RESOURCE);
        assertThat(result.getClassifyLayer()).isEqualTo("POLICY");
    }

    @Test
    @DisplayName("资源降级保留原始置信度")
    void resourceDowngrade_preservesConfidence() {
        IntentResult raw = rawOutOfScopeOpenDomain(0.82);

        IntentResult result = policyService.applyPolicy(raw, "求 C++ 算法题库", SESSION_ID);

        assertThat(result.getConfidence()).isEqualTo(0.82);
    }

    @Test
    @DisplayName("指代词强制意图为 CLARIFY/coreference")
    void coreferenceForce_anyIntent_becomesClarifyCoreference() {
        IntentResult raw = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.9)
                .rewrittenQuery("那个帖子")
                .classifyLayer("LLM")
                .build();

        IntentResult result = policyService.applyPolicy(raw, "帮我打开那个帖子", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.CLARIFY);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.COREFERENCE);
    }

    @Test
    @DisplayName("指代词优先级高于资源降级")
    void coreferenceOverridesResource_whenBothPresent() {
        IntentResult raw = rawOutOfScopeOpenDomain(0.7);

        IntentResult result = policyService.applyPolicy(raw, "那个 Python 教程还有吗", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.CLARIFY);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.COREFERENCE);
    }

    @Test
    @DisplayName("昵称声明保留原意图并设置 nicknameDeclared 标记")
    void nicknameDetection_keepsIntentAndSetsFlag() {
        IntentResult raw = IntentResult.builder()
                .intent(Intent.HOW_TO)
                .subIntent(Intent.SubIntent.FEATURE_HELP)
                .confidence(0.88)
                .rewrittenQuery("怎么发帖")
                .classifyLayer("LLM")
                .build();

        IntentResult result = policyService.applyPolicy(raw, "我叫小明，怎么发帖？", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.FEATURE_HELP);
        assertThat(result.isNicknameDeclared()).isTrue();
    }

    @Test
    @DisplayName("昵称声明支持 叫我xxx / 我的名字是xxx 多种形式")
    void nicknameDetection_variousPatterns() {
        assertThat(policyService.applyPolicy(rawOutOfScopeOpenDomain(0.5), "叫我阿强", SESSION_ID).isNicknameDeclared())
                .isTrue();
        assertThat(policyService.applyPolicy(rawOutOfScopeOpenDomain(0.5), "我的名字是李华", SESSION_ID).isNicknameDeclared())
                .isTrue();
    }

    @Test
    @DisplayName("非 OUT_OF_SCOPE/open_domain 含资源关键词不降级")
    void noDowngrade_whenIntentNotOpenDomain() {
        IntentResult raw = IntentResult.builder()
                .intent(Intent.HOW_TO)
                .subIntent(Intent.SubIntent.FEATURE_HELP)
                .confidence(0.9)
                .rewrittenQuery("怎么发帖")
                .classifyLayer("LLM")
                .build();

        IntentResult result = policyService.applyPolicy(raw, "有没有 Python 教程", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.HOW_TO);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.FEATURE_HELP);
    }

    @Test
    @DisplayName("重复应用策略结果不变（幂等）")
    void idempotency_applyTwice_sameResult() {
        IntentResult raw = rawOutOfScopeOpenDomain(0.66);

        IntentResult first = policyService.applyPolicy(raw, "求 Java 资料", SESSION_ID);
        IntentResult second = policyService.applyPolicy(first, "求 Java 资料", SESSION_ID);

        assertThat(second.getIntent()).isEqualTo(first.getIntent());
        assertThat(second.getSubIntent()).isEqualTo(first.getSubIntent());
        assertThat(second.getConfidence()).isEqualTo(first.getConfidence());
        assertThat(second.isNicknameDeclared()).isEqualTo(first.isNicknameDeclared());
    }

    @Test
    @DisplayName("raw 为 null 时返回默认 SEARCH 兜底")
    void nullRaw_returnsDefaultSearch() {
        IntentResult result = policyService.applyPolicy(null, "任何查询", SESSION_ID);

        assertThat(result.getIntent()).isEqualTo(Intent.SEARCH);
        assertThat(result.getSubIntent()).isEqualTo(Intent.SubIntent.RESOURCE);
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }
}
