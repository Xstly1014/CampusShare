package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
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

    /** 简短回答模式（如"都可以"、"随便"等，不应该被识别为闲聊） */
    private static final Pattern SHORT_REPLY_PATTERN = Pattern.compile(
            "^(都可以|都行|随便|好|好的|嗯|是的|对|没问题|OK|ok|可以|也行|随便你|随便吧|都行吧)$"
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

    /** 昵称声明模式（我叫/叫我/我的名字是） */
    private static final List<Pattern> NICKNAME_PATTERNS = List.of(
            Pattern.compile("我叫\\s*(.+?)(?:[。，！？；：,.!?;:\\s]|$)"),
            Pattern.compile("叫我\\s*(.+?)(?:[。，！？；：,.!?;:\\s]|$)"),
            Pattern.compile("我的名字是\\s*(.+?)(?:[。，！？；：,.!?;:\\s]|$)")
    );

    private static final int MAX_NICKNAME_LENGTH = 20;

    private static final String NICKNAME_REPLY_TEMPLATE = "好的 {nickname}，我记住啦～";

    private final LongTermMemoryService longTermMemoryService;

    public RuleShortCircuitFilter() {
        this(null);
    }

    @Autowired
    public RuleShortCircuitFilter(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    /**
     * 规则短路：命中返回 IntentResult，未命中返回 empty。
     *
     * @param query 用户查询文本（可为 null/空）
     * @return Optional<IntentResult>，命中时包含分类结果
     */
    public Optional<IntentResult> filter(String query) {
        return filter(null, query);
    }

    /**
     * 规则短路（携带 userId，支持昵称持久化）。
     *
     * @param userId 用户ID（可为 null，为 null 时不保存记忆）
     * @param query  用户查询文本（可为 null/空）
     * @return Optional<IntentResult>，命中时包含分类结果
     */
    public Optional<IntentResult> filter(String userId, String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String trimmed = query.trim();

        // 优先级 0：简短回答（都可以、随便等）→ 不短路，进入LLM分类层
        // 这些回答需要结合历史上下文理解，不能单独分类
        if (SHORT_REPLY_PATTERN.matcher(trimmed).matches()) {
            log.debug("Rule matched: SHORT_REPLY (skip short circuit), query='{}'", trimmed);
            return Optional.empty();
        }

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

        // 优先级 3：昵称声明 → OUT_OF_SCOPE/chitchat + 自定义模板回复
        Optional<IntentResult> nicknameResult = tryNickname(userId, trimmed);
        if (nicknameResult.isPresent()) {
            return nicknameResult;
        }

        // 优先级 4：闲聊 → OUT_OF_SCOPE/chitchat
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

        // 优先级 5：个人列表 → NAVIGATE/my_list
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

    /**
     * 尝试抽取并保存昵称。
     */
    private Optional<IntentResult> tryNickname(String userId, String query) {
        String nickname = extractNickname(query);
        if (nickname == null || nickname.isBlank()) {
            return Optional.empty();
        }

        if (userId != null && !userId.isBlank() && longTermMemoryService != null) {
            longTermMemoryService.saveNickname(userId, nickname);
        } else {
            log.debug("Nickname detected but no userId or memory service available: nickname={}", nickname);
        }

        String reply = NICKNAME_REPLY_TEMPLATE.replace("{nickname}", nickname);
        log.debug("Rule matched: NICKNAME for query='{}', nickname='{}'", query, nickname);
        return Optional.of(IntentResult.builder()
                .intent(Intent.OUT_OF_SCOPE)
                .subIntent(Intent.SubIntent.CHITCHAT)
                .confidence(0.99)
                .rewrittenQuery(query)
                .classifyLayer("RULE")
                .templateReply(reply)
                .nicknameDeclared(true)
                .build());
    }

    /**
     * 从 query 中抽取昵称（trim 后最多 20 字符）。
     */
    private String extractNickname(String query) {
        for (Pattern p : NICKNAME_PATTERNS) {
            Matcher matcher = p.matcher(query);
            if (matcher.find()) {
                String nickname = matcher.group(1).trim();
                if (nickname.length() > MAX_NICKNAME_LENGTH) {
                    nickname = nickname.substring(0, MAX_NICKNAME_LENGTH);
                }
                if (!nickname.isBlank()) {
                    return nickname;
                }
            }
        }
        return null;
    }
}
