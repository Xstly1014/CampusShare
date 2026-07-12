package com.campushare.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepSeekResponse {

    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        private Message delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    @Data
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }

    @Data
    public static class FunctionCall {
        private String name;
        private String arguments;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    public boolean hasToolCalls() {
        if (choices == null || choices.isEmpty()) return false;
        Message msg = choices.get(0).getMessage();
        return msg != null && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();
    }

    public List<ToolCall> getToolCalls() {
        if (!hasToolCalls()) return List.of();
        return choices.get(0).getMessage().getToolCalls();
    }

    public String getContent() {
        if (choices == null || choices.isEmpty()) return null;
        Message msg = choices.get(0).getMessage();
        return msg != null ? msg.getContent() : null;
    }
}
