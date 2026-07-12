package com.campushare.agent.tool.impl;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.service.RetrievalService;
import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolDef;
import com.campushare.agent.tool.ToolParam;
import com.campushare.agent.tool.ToolResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@ToolDef(
    name = "search_posts",
    description = "按学校、分类、关键词搜索帖子。用于用户想找资源、求资料、搜索校园讨论的场景。",
    intent = {"SEARCH", "HOW_TO"},
    readOnly = true,
    timeoutMs = 5000
)
@Component
@RequiredArgsConstructor
public class SearchPostsTool implements Tool {

    private final RetrievalService retrievalService;

    @Data
    public static class Params {
        @ToolParam(name = "query", description = "搜索关键词，如'操作系统期末卷子'", required = true, type = "string")
        private String query;

        @ToolParam(name = "school", description = "学校名称，如'清华'、'北大'。可选", required = false, type = "string")
        private String school;

        @ToolParam(name = "category", description = "分类名称，如'资源'、'讨论'。可选", required = false, type = "string")
        private String category;

        @ToolParam(name = "limit", description = "返回条数，默认10，最大20", required = false, type = "integer")
        private Integer limit;
    }

    @Override
    public Class<?> getParameterClass() {
        return Params.class;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, String userId) {
        String query = (String) arguments.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.error("TOOL_ARGS_INVALID", "query parameter is required");
        }

        String school = (String) arguments.get("school");
        String category = (String) arguments.get("category");
        Integer limit = arguments.get("limit") != null
                ? ((Number) arguments.get("limit")).intValue() : 10;

        if (limit > 20) limit = 20;

        IntentResult intentResult = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.8)
                .rewrittenQuery(query)
                .build();

        if (school != null || category != null) {
            IntentResult.SlotResult slots = IntentResult.SlotResult.builder()
                    .school(school)
                    .build();
            intentResult.setSlots(slots);
        }

        try {
            List<RetrievalResult> results = retrievalService.retrieve(query, intentResult, null).block();

            if (results == null || results.isEmpty()) {
                return ToolResult.empty("No relevant posts found for: " + query);
            }

            List<RetrievalResult> postResults = results.stream()
                    .filter(r -> r.source() == RetrievalResult.Source.POST)
                    .limit(Math.min(limit, results.size()))
                    .collect(Collectors.toList());

            if (postResults.isEmpty()) {
                return ToolResult.empty("No relevant posts found, try different keywords");
            }

            List<Map<String, Object>> posts = new ArrayList<>();
            List<ToolResult.Ref> refs = new ArrayList<>();
            int idx = 1;
            for (RetrievalResult r : postResults) {
                Map<String, Object> post = new HashMap<>();
                post.put("post_id", r.id());
                post.put("title", r.title());
                post.put("excerpt", r.content() != null && r.content().length() > 200
                        ? r.content().substring(0, 200) + "..." : r.content());
                post.put("score", r.score());
                post.put("source", r.source().name());
                if (r.metadata() != null) {
                    post.putAll(r.metadata());
                }
                posts.add(post);
                refs.add(ToolResult.Ref.builder()
                        .type("post")
                        .id(r.id())
                        .title(r.title())
                        .url("/post/" + r.id())
                        .build());
                idx++;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("total", postResults.size());
            data.put("posts", posts);

            String summary = String.format("Found %d relevant posts", postResults.size());
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .summary(summary)
                    .data(data)
                    .refs(refs)
                    .build();

        } catch (Exception e) {
            return ToolResult.error("TOOL_UPSTREAM_ERROR", "Post search failed: " + e.getMessage());
        }
    }
}
