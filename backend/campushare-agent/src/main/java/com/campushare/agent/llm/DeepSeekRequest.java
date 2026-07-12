package com.campushare.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DeepSeekRequest {

    private String model;
    private List<Message> messages;
    private Boolean stream;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    private List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private Boolean includeUsage;
    }
}
