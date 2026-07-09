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
        m.put("点赞", "/profile/liked");
        m.put("收藏", "/profile/starred");
        m.put("回复", "/profile/comments");
        m.put("评论", "/profile/comments");
        m.put("浏览", "/profile/history");
        m.put("看过", "/profile/history");
        m.put("关注", "/profile/following");
        m.put("粉丝", "/profile/followers");
        m.put("帖子", "/profile/posts");
        MY_LIST_ROUTES = Collections.unmodifiableMap(m);
    }

    /** NAVIGATE/feature_loc 子意图 → 功能入口路由映射 */
    private static final Map<String, String> FEATURE_LOC_ROUTES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("个人中心", "/profile");
        m.put("个人主页", "/profile");
        m.put("主页", "/profile");
        m.put("消息", "/messages");
        m.put("私信", "/messages");
        m.put("通知", "/notifications");
        m.put("收纳", "/warehouse");
        m.put("仓库", "/warehouse");
        m.put("首页", "/home");
        m.put("广场", "/home");
        m.put("AI助手", "/agent");
        m.put("智能助手", "/agent");
        m.put("设置", "/settings/account");
        m.put("认证", "/creator-verification");
        m.put("创作者", "/creator-verification");
        FEATURE_LOC_ROUTES = Collections.unmodifiableMap(m);
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
     * 构建 NAVIGATE 快路径路由。
     *
     * 支持三种子意图：
     *  - my_list：我的帖子/点赞/收藏等个人列表
     *  - feature_loc：个人中心/消息/通知等功能入口
     *  - section_loc：分类板块入口（默认跳到 /home 让用户自行选择）
     */
    private RouteDecision buildNavigateRoute(IntentResult intent) {
        String query = intent.getRewrittenQuery() != null ? intent.getRewrittenQuery() : "";
        String subIntent = intent.getSubIntent();
        String route;
        String reply;

        if (Intent.SubIntent.MY_LIST.equals(subIntent)) {
            route = mapMyListRoute(query);
            reply = "你可以在这里查看你的内容：\n\n点击下方卡片即可跳转 👇";
        } else if (Intent.SubIntent.FEATURE_LOC.equals(subIntent)) {
            route = mapFeatureLocRoute(query);
            reply = "你找的功能在这里：\n\n点击下方卡片即可跳转 👇";
        } else if (Intent.SubIntent.SECTION_LOC.equals(subIntent)) {
            route = "/home";
            reply = "所有分类都在首页，你可以从这里进入：\n\n点击下方卡片即可跳转 👇";
        } else {
            route = mapFeatureLocRoute(query);
            if ("/home".equals(route)) {
                reply = "你可以从首页找到你需要的内容：\n\n点击下方卡片即可跳转 👇";
            } else {
                reply = "点击下方卡片即可跳转 👇";
            }
        }

        log.info("Short-circuit NAVIGATE/{} → route={}", subIntent, route);
        return RouteDecision.builder()
                .shortCircuit(true)
                .templateReply(reply)
                .navigateRoute(route)
                .intent(intent.getIntent())
                .rewrittenQuery(intent.getRewrittenQuery())
                .build();
    }

    /**
     * 根据 query 内容映射 my_list 路由。
     * 默认返回 /profile（个人主页）。
     */
    private String mapMyListRoute(String query) {
        return MY_LIST_ROUTES.entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("/profile");
    }

    /**
     * 根据 query 内容映射 feature_loc 路由。
     * 默认返回 /home（首页）。
     */
    private String mapFeatureLocRoute(String query) {
        return FEATURE_LOC_ROUTES.entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("/home");
    }
}
