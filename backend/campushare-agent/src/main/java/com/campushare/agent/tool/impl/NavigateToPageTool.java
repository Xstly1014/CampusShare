package com.campushare.agent.tool.impl;

import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolDef;
import com.campushare.agent.tool.ToolParam;
import com.campushare.agent.tool.ToolResult;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

@ToolDef(
    name = "navigate_to_page",
    description = "获取页面跳转路由，返回前端跳转卡片所需的路由地址和标签名称。用于用户问某个功能在哪、如何找到某个页面的场景。",
    intent = {"NAVIGATE", "HOW_TO"},
    readOnly = true,
    timeoutMs = 1000
)
@Component
public class NavigateToPageTool implements Tool {

    @Data
    public static class Params {
        @ToolParam(name = "page", description = "页面名称，如'个人主页'、'我的发布'、'通知'、'创作者认证'", required = true, type = "string")
        private String page;
    }

    @Override
    public Class<?> getParameterClass() {
        return Params.class;
    }

    private static final Map<String, String> ROUTE_MAP = new HashMap<>();
    private static final Map<String, String> LABEL_MAP = new HashMap<>();

    static {
        ROUTE_MAP.put("profile", "/profile");
        ROUTE_MAP.put("my posts", "/profile/posts");
        ROUTE_MAP.put("my liked", "/profile/liked");
        ROUTE_MAP.put("my starred", "/profile/starred");
        ROUTE_MAP.put("my comments", "/profile/comments");
        ROUTE_MAP.put("history", "/profile/history");
        ROUTE_MAP.put("following", "/profile/following");
        ROUTE_MAP.put("followers", "/profile/followers");
        ROUTE_MAP.put("home", "/home");
        ROUTE_MAP.put("messages", "/messages");
        ROUTE_MAP.put("notifications", "/notifications");
        ROUTE_MAP.put("warehouse", "/warehouse");
        ROUTE_MAP.put("agent", "/agent");
        ROUTE_MAP.put("settings", "/settings/account");
        ROUTE_MAP.put("creator verification", "/creator-verification");
        ROUTE_MAP.put("help", "/help");

        LABEL_MAP.put("/profile", "个人主页");
        LABEL_MAP.put("/profile/posts", "我的发布");
        LABEL_MAP.put("/profile/liked", "我的点赞");
        LABEL_MAP.put("/profile/starred", "我的收藏");
        LABEL_MAP.put("/profile/comments", "我的评论");
        LABEL_MAP.put("/profile/history", "浏览历史");
        LABEL_MAP.put("/profile/following", "我的关注");
        LABEL_MAP.put("/profile/followers", "我的粉丝");
        LABEL_MAP.put("/home", "首页");
        LABEL_MAP.put("/messages", "消息");
        LABEL_MAP.put("/notifications", "通知");
        LABEL_MAP.put("/warehouse", "收纳篮");
        LABEL_MAP.put("/agent", "AI 助手");
        LABEL_MAP.put("/settings/account", "账号设置");
        LABEL_MAP.put("/creator-verification", "创作者认证");
        LABEL_MAP.put("/help", "帮助中心");
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, String userId) {
        String page = (String) arguments.get("page");
        if (page == null || page.isBlank()) {
            return ToolResult.error("TOOL_ARGS_INVALID", "page parameter is required");
        }

        String route = findRoute(page.toLowerCase().trim());

        if (route == null) {
            return ToolResult.empty("Could not find matching page for: " + page);
        }

        String label = LABEL_MAP.getOrDefault(route, "点击跳转");

        Map<String, Object> data = new HashMap<>();
        data.put("route", route);
        data.put("label", label);

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .summary("Found page: " + label)
                .data(data)
                .refs(List.of(ToolResult.Ref.builder()
                        .type("page")
                        .id(route)
                        .title(label)
                        .url(route)
                        .build()))
                .build();
    }

    private String findRoute(String page) {
        for (Map.Entry<String, String> entry : ROUTE_MAP.entrySet()) {
            if (entry.getKey().contains(page) || page.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
