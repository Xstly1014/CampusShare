package com.campushare.agent.llm.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    private String content;

    private List<ToolCall> toolCalls;

    private Usage usage;

    private String provider;

    private String model;

    private boolean success;

    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String toolName;
        private Map<String, Object> arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    public static LlmResponse success(String content, Usage usage, String provider, String model) {
        return LlmResponse.builder()
                .content(content)
                .usage(usage)
                .provider(provider)
                .model(model)
                .success(true)
                .build();
    }

    public static LlmResponse toolCall(List<ToolCall> toolCalls, Usage usage, String provider, String model) {
        return LlmResponse.builder()
                .toolCalls(toolCalls)
                .usage(usage)
                .provider(provider)
                .model(model)
                .success(true)
                .build();
    }

    public static LlmResponse failure(String errorMessage) {
        return LlmResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
