package com.campushare.agent.tool.function;

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
        description = "Search for campus posts and resources. Use when the user asks for materials, tutorials, notes, past papers, or discussions.",
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
        @ToolParam(name = "query", description = "Search keywords, e.g. 'operating system final exam papers'", required = true, type = "string")
        private String query;

        @ToolParam(name = "school", description = "School name filter, e.g. 'Tsinghua'. Optional.", required = false, type = "string")
        private String school;

        @ToolParam(name = "category", description = "Category filter, e.g. 'notes'. Optional.", required = false, type = "string")
        private String category;

        @ToolParam(name = "post_type", description = "Type of posts to search.", required = false, type = "string", enumValues = {"resource", "discussion", "all"})
        private String postType;

        @ToolParam(name = "sort", description = "Result ordering.", required = false, type = "string", enumValues = {"latest", "hottest"})
        private String sort;
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
        String postType = (String) arguments.get("post_type");
        String sort = (String) arguments.get("sort");

        if (postType == null || postType.isBlank()) {
            postType = "all";
        }
        if (sort == null || sort.isBlank()) {
            sort = "latest";
        }

        String subIntent = switch (postType) {
            case "resource" -> Intent.SubIntent.RESOURCE;
            case "discussion" -> Intent.SubIntent.DISCUSSION;
            default -> null;
        };

        IntentResult.SlotResult slots = IntentResult.SlotResult.builder()
                .school(school)
                .category(category)
                .postType(postType)
                .sort(sort)
                .build();

        IntentResult intentResult = IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(subIntent)
                .confidence(0.8)
                .rewrittenQuery(query)
                .slots(slots)
                .build();

        try {
            List<RetrievalResult> results = retrievalService.retrieve(query, intentResult, null).block();

            if (results == null || results.isEmpty()) {
                return ToolResult.empty("No relevant posts found for: " + query);
            }

            List<Map<String, Object>> posts = results.stream()
                    .filter(r -> r.source() == RetrievalResult.Source.POST)
                    .map(r -> {
                        Map<String, Object> post = new LinkedHashMap<>();
                        post.put("post_id", r.id());
                        post.put("title", r.title());
                        post.put("excerpt", r.content() != null && r.content().length() > 200
                                ? r.content().substring(0, 200) + "..." : r.content());
                        post.put("score", r.score());
                        post.put("source", r.source().name());
                        if (r.metadata() != null) {
                            post.putAll(r.metadata());
                        }
                        return post;
                    })
                    .collect(Collectors.toList());

            if (posts.isEmpty()) {
                return ToolResult.empty("No relevant posts found, try different keywords");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("results", posts);
            data.put("total", posts.size());

            return ToolResult.success(String.format("Found %d relevant posts", posts.size()), data);
        } catch (Exception e) {
            return ToolResult.error("TOOL_UPSTREAM_ERROR", "Post search failed: " + e.getMessage());
        }
    }
}
