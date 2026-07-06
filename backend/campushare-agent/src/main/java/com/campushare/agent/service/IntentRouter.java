package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RouteDecision;
import com.campushare.agent.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 意图路由器（ADR-013）。
 *
 * 决定请求走快路径（模板回复，0 LLM 调用）还是慢路径（RAG 管线）。
 *
 * MVP 阶段只处理快路径：
 *  - OUT_OF_SCOPE → 4 子意图模板回复（chitchat/write_action/open_domain/sensitive）
 *  - NAVIGATE → my_list 跳转卡片
 *  - HOW_TO/SEARCH/CLARIFY → 返回 empty（走 RAG 管线）
 *
 * Advanced 阶段可扩展为完整路由（接入 RetrievalService/DeepSeekClient）。
 */
@Service
@Slf4j
public class IntentRouter {

    /** OUT_OF_SCOPE/chitchat 模板 */
    private static final String CHITCHAT_REPLY =
            "你好！我是 CampusShare AI 助手小享，可以帮你找资料、解答平台使用问题。请问有什么可以帮你的？";

    /** OUT_OF_SCOPE/write_action 模板 */
    private static final String WRITE_ACTION_REPLY =
            "抱歉，我无法代替你执行发帖、点赞等操作。请前往对应页面手动操作。如果你不知道在哪操作，可以问我「怎么发帖」。";

    /** OUT_OF_SCOPE/open_domain 模板 */
    private static final String OPEN_DOMAIN_REPLY =
            "抱歉，我是 CampusShare 平台助手，只能回答与校园资源共享相关的问题。";

    /** OUT_OF_SCOPE/sensitive 模板 */
    private static final String SENSITIVE_REPLY =
            "抱歉，这个问题我无法回答。";

    /** OUT_OF_SCOPE 兜底模板（未知子意图） */
    private static final String OUT_OF_SCOPE_DEFAULT_REPLY =
            "抱歉，我无法处理这个请求。我可以帮你找资料、解答平台使用问题。";

    /** NAVIGATE/my_list 子意图 → 前端路由映射（LinkedHashMap 保证匹配顺序，「帖子」放最后避免误匹配） */
    private static final Map<String, String> MY_LIST_ROUTES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("点赞", "/my?tab=liked");
        m.put("收藏", "/my?tab=favorited");
        m.put("回复", "/my?tab=replies");
        m.put("评论", "/my?tab=replies");
        m.put("浏览", "/my?tab=history");
        m.put("看过", "/my?tab=history");
        m.put("关注", "/my?tab=following");
        m.put("粉丝", "/my?tab=followers");
        m.put("帖子", "/my?tab=posts");
        MY_LIST_ROUTES = Collections.unmodifiableMap(m);
    }

    /**
     * 尝试快路径：OUT_OF_SCOPE/NAVIGATE 直接返回模板回复。
     *
     * @param intent 意图分类结果
     * @return Optional.empty() 表示走 RAG 管线（HOW_TO/SEARCH/CLARIFY）
     */
    public Optional<RouteDecision> tryShortCircuit(IntentResult intent) {
        if (intent == null) {
            return Optional.empty();
        }

        return switch (intent.getIntent()) {
            case OUT_OF_SCOPE -> Optional.of(buildOutOfScopeRoute(intent));
            case NAVIGATE -> Optional.of(buildNavigateRoute(intent));
            default -> Optional.empty();  // HOW_TO/SEARCH/CLARIFY 走 RAG
        };
    }

    /**
     * 构建 OUT_OF_SCOPE 快路径路由（按子意图选模板）。
     */
    private RouteDecision buildOutOfScopeRoute(IntentResult intent) {
        String reply = selectOutOfScopeReply(intent.getSubIntent());
        log.info("Short-circuit OUT_OF_SCOPE/{} → template reply", intent.getSubIntent());
        return RouteDecision.builder()
                .shortCircuit(true)
                .templateReply(reply)
                .intent(intent.getIntent())
                .rewrittenQuery(intent.getRewrittenQuery())
                .build();
    }

    private String selectOutOfScopeReply(String subIntent) {
        if (subIntent == null) {
            return OUT_OF_SCOPE_DEFAULT_REPLY;
        }
        return switch (subIntent) {
            case Intent.SubIntent.CHITCHAT -> CHITCHAT_REPLY;
            case Intent.SubIntent.WRITE_ACTION -> WRITE_ACTION_REPLY;
            case Intent.SubIntent.OPEN_DOMAIN -> OPEN_DOMAIN_REPLY;
            case Intent.SubIntent.SENSITIVE -> SENSITIVE_REPLY;
            default -> OUT_OF_SCOPE_DEFAULT_REPLY;
        };
    }

    /**
     * 构建 NAVIGATE 快路径路由（my_list 跳转卡片）。
     */
    private RouteDecision buildNavigateRoute(IntentResult intent) {
        String query = intent.getRewrittenQuery() != null ? intent.getRewrittenQuery() : "";
        String route = mapMyListRoute(query);
        String card = "你可以在这里查看：\n[点击跳转 →](" + route + ")";
        log.info("Short-circuit NAVIGATE/{} → route={}", intent.getSubIntent(), route);
        return RouteDecision.builder()
                .shortCircuit(true)
                .templateReply(card)
                .navigateRoute(route)
                .intent(intent.getIntent())
                .rewrittenQuery(intent.getRewrittenQuery())
                .build();
    }

    /**
     * 根据 query 内容映射 my_list 路由。
     * 默认返回 /my（个人主页）。
     */
    private String mapMyListRoute(String query) {
        return MY_LIST_ROUTES.entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("/my");
    }
}
