package com.campushare.agent.tool;

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
public class ToolResult {
    private Status status;
    private String summary;
    private Object data;
    private List<Ref> refs;
    private String errorCode;
    private String errorMessage;
    private boolean degraded;

    public enum Status {
        SUCCESS, ERROR, EMPTY
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ref {
        private String type;
        private String id;
        private String title;
        private String url;
    }

    public static ToolResult success(Object data) {
        return ToolResult.builder()
                .status(Status.SUCCESS)
                .data(data)
                .build();
    }

    public static ToolResult success(String summary, Object data) {
        return ToolResult.builder()
                .status(Status.SUCCESS)
                .summary(summary)
                .data(data)
                .build();
    }

    public static ToolResult empty(String summary) {
        return ToolResult.builder()
                .status(Status.EMPTY)
                .summary(summary)
                .build();
    }

    public static ToolResult error(String errorCode, String errorMessage) {
        return ToolResult.builder()
                .status(Status.ERROR)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
