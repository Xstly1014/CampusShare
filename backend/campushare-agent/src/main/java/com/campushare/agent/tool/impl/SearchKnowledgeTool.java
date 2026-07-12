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
    name = "search_knowledge",
    description = "搜索知识库帮助文章、平台使用指南和操作文档。用于用户询问平台功能、规则、如何操作等场景。",
    intent = {"HOW_TO", "SEARCH"},
    readOnly = true,
    timeoutMs = 5000
)
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements Tool {

    private final RetrievalService retrievalService;

    @Data
    public static class Params {
        @ToolParam(name = "query", description = "搜索关键词，如'如何认证创作者'", required = true, type = "string")
        private String query;

        @ToolParam(name = "topic", description = "主题分类，如'账号'、'发布'、'认证'。可选", required = false, type = "string")
        private String topic;

        @ToolParam(name = "limit", description = "返回条数，默认5，最大10", required = false, type = "integer")
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

        String topic = (String) arguments.get("topic");
        Integer limit = arguments.get("limit") != null
                ? ((Number) arguments.get("limit")).intValue() : 5;

        if (limit > 10) limit = 10;

        IntentResult intentResult = IntentResult.builder()
                .intent(Intent.HOW_TO)
                .confidence(0.8)
                .rewrittenQuery(query)
                .build();

        try {
            List<RetrievalResult> results = retrievalService.retrieve(query, intentResult, null).block();

            if (results == null || results.isEmpty()) {
                return ToolResult.empty("No relevant help articles found for: " + query);
            }

            List<RetrievalResult> knowledgeResults = results.stream()
                    .filter(r -> r.source() == RetrievalResult.Source.KNOWLEDGE)
                    .limit(Math.min(limit, results.size()))
                    .collect(Collectors.toList());

            if (knowledgeResults.isEmpty()) {
                return ToolResult.empty("No relevant knowledge articles found");
            }

            List<Map<String, Object>> articles = new ArrayList<>();
            List<ToolResult.Ref> refs = new ArrayList<>();
            for (RetrievalResult r : knowledgeResults) {
                Map<String, Object> article = new HashMap<>();
                article.put("article_id", r.id());
                article.put("title", r.title());
                article.put("content_excerpt", r.content() != null && r.content().length() > 300
                        ? r.content().substring(0, 300) + "..." : r.content());
                article.put("score", r.score());
                if (r.metadata() != null && r.metadata().get("topic") != null) {
                    article.put("topic", r.metadata().get("topic"));
                }
                articles.add(article);
                refs.add(ToolResult.Ref.builder()
                        .type("knowledge")
                        .id(r.id())
                        .title(r.title())
                        .url("/help/" + r.id())
                        .build());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("articles", articles);

            String summary = String.format("Found %d relevant help articles", knowledgeResults.size());
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .summary(summary)
                    .data(data)
                    .refs(refs)
                    .build();

        } catch (Exception e) {
            return ToolResult.error("TOOL_UPSTREAM_ERROR", "Knowledge search failed: " + e.getMessage());
        }
    }
}
