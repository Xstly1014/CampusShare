package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 规则短路层（Layer 1）安全兜底。
 *
 * 仅保留安全相关规则：写操作、敏感内容、注入尝试，以及空/null 输入。
 * 其余 query 返回 Optional.empty()，交给下游 LLM 分类器处理。
 */
@Service
@Slf4j
public class RuleShortCircuitFilter {

    /** 写操作请求（帮我发帖/点赞/收藏/关注/修改/删除/编辑/代替我/替我...） */
    private static final List<Pattern> WRITE_ACTION_PATTERNS = List.of(
            Pattern.compile("帮我(发|发布|写).*帖"),
            Pattern.compile("帮我(点|赞|收藏|关注|取消)"),
            Pattern.compile("帮我(改|修改|删除|编辑)"),
            Pattern.compile("代替我(发|点|改|删)"),
            Pattern.compile("替我(发|点|改|删)")
    );

    /** 敏感内容关键词 */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "政治", "医疗", "法律", "投资", "黄色", "暴力", "毒品", "赌博"
    );

    /** 注入尝试关键词 */
    private static final List<String> INJECTION_KEYWORDS = List.of(
            "忽略上述指令", "你现在是 DAN", "system prompt", "忽略之前的指令"
    );

    private static final String WRITE_ACTION_REPLY = "抱歉，我目前只能帮你查询校园信息，暂不支持代你操作或发布内容。";
    private static final String SENSITIVE_REPLY = "抱歉，这类内容超出了我的服务范围，我无法回应。";
    private static final String INJECTION_REPLY = "抱歉，我无法执行该指令。";

    public RuleShortCircuitFilter() {
    }

    /**
     * 安全规则短路：命中返回 IntentResult，未命中返回 empty。
     *
     * @param query 用户查询文本（可为 null/空）
     * @return Optional<IntentResult>，命中时包含分类结果
     */
    public Optional<IntentResult> filter(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String trimmed = query.trim();
        String lower = trimmed.toLowerCase();

        // 写操作 → OUT_OF_SCOPE/write_action
        for (Pattern p : WRITE_ACTION_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                log.debug("Safety rule matched: WRITE_ACTION for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.OUT_OF_SCOPE)
                        .subIntent(Intent.SubIntent.WRITE_ACTION)
                        .confidence(0.99)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("SAFETY")
                        .templateReply(WRITE_ACTION_REPLY)
                        .build());
            }
        }

        // 敏感内容 → OUT_OF_SCOPE/sensitive
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (trimmed.contains(keyword)) {
                log.debug("Safety rule matched: SENSITIVE for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.OUT_OF_SCOPE)
                        .subIntent(Intent.SubIntent.SENSITIVE)
                        .confidence(0.99)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("SAFETY")
                        .templateReply(SENSITIVE_REPLY)
                        .build());
            }
        }

        // 注入尝试 → OUT_OF_SCOPE/sensitive
        for (String keyword : INJECTION_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                log.debug("Safety rule matched: INJECTION for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.OUT_OF_SCOPE)
                        .subIntent(Intent.SubIntent.SENSITIVE)
                        .confidence(0.99)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("SAFETY")
                        .templateReply(INJECTION_REPLY)
                        .build());
            }
        }

        return Optional.empty();
    }

    /**
     * 安全规则短路（携带 userId，保持向后兼容，现直接委托给无 userId 版本）。
     *
     * @param userId 用户ID（可为 null，已不再使用）
     * @param query  用户查询文本（可为 null/空）
     * @return Optional<IntentResult>，命中时包含分类结果
     */
    public Optional<IntentResult> filter(String userId, String query) {
        return filter(query);
    }
}
