package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 规则短路层（Layer 1）。
 *
 * 处理高确定性请求，<5ms 返回，过滤约 25% 流量省 LLM 成本。
 *
 * 四类规则（按优先级）：
 *  1. 指代词强制 CLARIFY（ADR-015）：含"那个/它/上面那个"等 → CLARIFY/coreference
 *  2. 写操作：含"帮我发/帮我点赞/帮我改" → OUT_OF_SCOPE/write_action
 *  3. 闲聊问候：匹配"^(你好|谢谢|你是谁).*" → OUT_OF_SCOPE/chitchat
 *  4. 个人列表：含"我点赞的/我收藏的" → NAVIGATE/my_list
 *
 * 未命中返回 Optional.empty()，进入 Layer 2（LLM 分类）。
 */
@Service
@Slf4j
public class RuleShortCircuitFilter {

    /** 规则 1：指代词强制 CLARIFY（ADR-015） */
    private static final Pattern COREFERENCE_PATTERN = Pattern.compile(
            ".*(那个|它|上面那个|刚才那个|第几个|有下载的|带图的|最热的|这个|那个帖子).*"
    );

    /** 规则 2：写操作请求（帮我发帖/帮我点赞/帮我关注/帮我改密码） */
    private static final List<Pattern> WRITE_ACTION_PATTERNS = List.of(
            Pattern.compile("帮我(发|发布|写).*帖"),
            Pattern.compile("帮我(点|赞|收藏|关注|取消)"),
            Pattern.compile("帮我(改|修改|删除|编辑)"),
            Pattern.compile("代替我(发|点|改|删)"),
            Pattern.compile("替我(发|点|改|删)")
    );

    /** 规则 3：闲聊问候（^开头匹配） */
    private static final List<Pattern> CHITCHAT_PATTERNS = List.of(
            Pattern.compile("^(你好|您好|hi|hello|嗨|哈喽|hey).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(谢谢|感谢|thx|thanks).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(你是谁|你叫什么|你能做什么|你是什么).*$"),
            Pattern.compile("^(早上好|晚上好|中午好|晚安|早).*$"),
            Pattern.compile("^(再见|拜拜|bye).*$", Pattern.CASE_INSENSITIVE)
    );

    /** 规则 4：个人列表（我点赞的/我收藏的/我回复的/我的浏览历史） */
    private static final List<Pattern> MY_LIST_PATTERNS = List.of(
            Pattern.compile("我(点赞|赞过|喜欢).*帖"),
            Pattern.compile("我(收藏|存|关注).*帖"),
            Pattern.compile("我(回复|评论)过.*"),
            Pattern.compile("我(浏览|看过)历史"),
            Pattern.compile("我(关注|粉丝|互关)列表"),
            Pattern.compile("我的(点赞|收藏|回复|评论|浏览|关注|粉丝).*")
    );

    /**
     * 规则短路：命中返回 IntentResult，未命中返回 empty。
     *
     * @param query 用户查询文本（可为 null/空）
     * @return Optional<IntentResult>，命中时包含分类结果
     */
    public Optional<IntentResult> filter(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String trimmed = query.trim();

        // 优先级 1：指代词 → 强制 CLARIFY（ADR-015）
        if (COREFERENCE_PATTERN.matcher(trimmed).matches()) {
            log.debug("Rule matched: COREFERENCE for query='{}'", trimmed);
            return Optional.of(IntentResult.builder()
                    .intent(Intent.CLARIFY)
                    .subIntent(Intent.SubIntent.COREFERENCE)
                    .confidence(0.95)
                    .rewrittenQuery(trimmed)
                    .classifyLayer("RULE")
                    .build());
        }

        // 优先级 2：写操作 → OUT_OF_SCOPE/write_action
        for (Pattern p : WRITE_ACTION_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                log.debug("Rule matched: WRITE_ACTION for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.OUT_OF_SCOPE)
                        .subIntent(Intent.SubIntent.WRITE_ACTION)
                        .confidence(0.99)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("RULE")
                        .build());
            }
        }

        // 优先级 3：闲聊 → OUT_OF_SCOPE/chitchat
        for (Pattern p : CHITCHAT_PATTERNS) {
            if (p.matcher(trimmed).matches()) {
                log.debug("Rule matched: CHITCHAT for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.OUT_OF_SCOPE)
                        .subIntent(Intent.SubIntent.CHITCHAT)
                        .confidence(0.99)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("RULE")
                        .build());
            }
        }

        // 优先级 4：个人列表 → NAVIGATE/my_list
        for (Pattern p : MY_LIST_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                log.debug("Rule matched: MY_LIST for query='{}'", trimmed);
                return Optional.of(IntentResult.builder()
                        .intent(Intent.NAVIGATE)
                        .subIntent(Intent.SubIntent.MY_LIST)
                        .confidence(0.95)
                        .rewrittenQuery(trimmed)
                        .classifyLayer("RULE")
                        .build());
            }
        }

        return Optional.empty();
    }
}
