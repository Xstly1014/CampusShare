package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 意图策略层：集中管理所有基于文本内容的意图修正规则。
 *
 * 被 {@link IntentClassifier} 在缓存命中、LLM 分类、Embedding 兜底之后统一调用，
 * 避免业务规则散落在多个分类源中。
 *
 * 规则优先级（从高到低）：
 *  1. 指代词强制 CLARIFY/coreference
 *  2. OUT_OF_SCOPE/open_domain + 资源关键词 → 降级为 SEARCH/resource
 *  3. 昵称声明检测 → 在原意图上打标，不修改意图
 */
@Service
@Slf4j
public class IntentPolicyService {

    /**
     * 资源请求关键词：用户表达想获取学习资料/教程/资源时，即使 LLM 误判为 OUT_OF_SCOPE/open_domain
     * 也应降级为 SEARCH/resource。覆盖 "我想学python" / "求Python教程" / "找C++资料" 等。
     */
    private static final Pattern RESOURCE_KEYWORDS = Pattern.compile(
            "(资料|教程|笔记|卷子|答案|课件|课程|题库|面经|代码|Python|Java|C\\+\\+|前端|后端|算法|考研|期末|入门|学习)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** 指代词：命中后强制进入 CLARIFY/coreference。 */
    private static final Pattern COREFERENCE_WORDS = Pattern.compile(
            "(那个|那个帖子|刚刚|之前|上一轮|上面那个)"
    );

    /** 昵称声明模式：我叫xxx / 叫我xxx / 我的名字是xxx。 */
    private static final Pattern NICKNAME_DECLARATION = Pattern.compile(
            "我叫(.+?)(?:[，。,.\\s]|$)|叫我(.+?)(?:[，。,.\\s]|$)|我的名字是(.+?)(?:[，。,.\\s]|$)"
    );

    /**
     * 对原始分类结果应用策略修正。
     *
     * @param raw       原始分类结果（可能来自缓存/LLM/Embedding）
     * @param query     用户原始查询
     * @param sessionId 会话 ID，用于日志定位
     * @return 修正后的分类结果；如果 raw 为 null，返回默认 SEARCH 兜底
     */
    public IntentResult applyPolicy(IntentResult raw, String query, String sessionId) {
        if (raw == null) {
            raw = buildDefaultSearchIntent(query);
        }

        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();

            // 规则 1：指代词优先级最高，强制 CLARIFY/coreference
            if (containsCoreference(trimmed)) {
                log.info("Policy matched: COREFERENCE for query='{}', session={}", trimmed, sessionId);
                raw.setIntent(Intent.CLARIFY);
                raw.setSubIntent(Intent.SubIntent.COREFERENCE);
                raw.setClassifyLayer("POLICY");
            }
            // 规则 2：资源请求降级
            else if (isResourceRequest(trimmed)
                    && raw.getIntent() == Intent.OUT_OF_SCOPE
                    && Intent.SubIntent.OPEN_DOMAIN.equals(raw.getSubIntent())) {
                log.info("Policy matched: RESOURCE downgrade for query='{}', session={}", trimmed, sessionId);
                raw.setIntent(Intent.SEARCH);
                raw.setSubIntent(Intent.SubIntent.RESOURCE);
                raw.setClassifyLayer("POLICY");
            }

            // 规则 3：昵称声明检测，不改变意图，仅打标
            if (containsNicknameDeclaration(trimmed)) {
                log.info("Policy matched: NICKNAME declared for query='{}', session={}", trimmed, sessionId);
                raw.setNicknameDeclared(true);
            }
        }

        return raw;
    }

    private boolean isResourceRequest(String query) {
        return RESOURCE_KEYWORDS.matcher(query).find();
    }

    private boolean containsCoreference(String query) {
        return COREFERENCE_WORDS.matcher(query).find();
    }

    private boolean containsNicknameDeclaration(String query) {
        return NICKNAME_DECLARATION.matcher(query).find();
    }

    private IntentResult buildDefaultSearchIntent(String query) {
        return IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.0)
                .rewrittenQuery(query != null ? query : "")
                .classifyLayer("DEFAULT")
                .build();
    }
}
