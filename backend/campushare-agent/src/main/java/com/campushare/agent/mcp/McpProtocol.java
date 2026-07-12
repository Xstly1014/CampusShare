package com.campushare.agent.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class McpProtocol {

    private McpProtocol() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String jsonrpc = "2.0";
        private String id;
        private String method;
        private Map<String, Object> params;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String jsonrpc = "2.0";
        private String id;
        private Object result;
        private ErrorObject error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorObject {
        private int code;
        private String message;
        private Object data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notification {
        private String jsonrpc = "2.0";
        private String method;
        private Map<String, Object> params;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private String name;
        private String description;
        @JsonProperty("inputSchema")
        private Map<String, Object> inputSchema;
        @JsonProperty("outputSchema")
        private Map<String, Object> outputSchema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListToolsResult {
        private List<Tool> tools;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallToolResult {
        private Object content;
        private boolean isError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitializeResult {
        private ServerInfo serverInfo;
        private String protocolVersion;
        private Capabilities capabilities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerInfo {
        private String name;
        private String version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capabilities {
        private ToolsCapability tools;
        private ResourcesCapability resources;
        private PromptsCapability prompts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsCapability {
        private boolean listChanged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourcesCapability {
        private boolean subscribe;
        private boolean listChanged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptsCapability {
        private boolean listChanged;
    }
}
