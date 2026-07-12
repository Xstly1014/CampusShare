package com.campushare.agent.service;

import com.campushare.agent.mcp.McpClientManager;
import com.campushare.agent.mcp.McpProtocol;
import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class McpToolAdapter implements Tool {

    private final String serverName;
    private final McpProtocol.Tool toolDefinition;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    @Override
    public ToolResult execute(Map<String, Object> arguments, String userId) {
        try {
            McpProtocol.CallToolResult result = mcpClientManager.callTool(
                    serverName, toolDefinition.getName(), arguments).block();

            if (result == null) {
                return ToolResult.error("MCP_EMPTY_RESPONSE", "Empty response from MCP server");
            }

            if (result.isError()) {
                return ToolResult.error("MCP_TOOL_ERROR",
                        result.getContent() != null ? result.getContent().toString() : "Unknown MCP error");
            }

            Object data = result.getContent();

            String summary = "MCP tool " + toolDefinition.getName() + " executed successfully";
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .summary(summary)
                    .data(data)
                    .refs(List.of())
                    .build();

        } catch (Exception e) {
            log.error("MCP tool execution failed: {}/{}", serverName, toolDefinition.getName(), e);
            return ToolResult.error("MCP_EXECUTION_ERROR", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return toolDefinition.getName();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Class<?> getParameterClass() {
        return null;
    }

    public Mono<ToolResult> executeAsync(Map<String, Object> arguments, String userId) {
        return mcpClientManager.callTool(serverName, toolDefinition.getName(), arguments)
                .map(result -> {
                    if (result.isError()) {
                        return ToolResult.error("MCP_TOOL_ERROR",
                                result.getContent() != null ? result.getContent().toString() : "Unknown MCP error");
                    }
                    return ToolResult.builder()
                            .status(ToolResult.Status.SUCCESS)
                            .summary("MCP tool " + toolDefinition.getName() + " executed successfully")
                            .data(result.getContent())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("MCP tool execution failed: {}/{}", serverName, toolDefinition.getName(), e);
                    return Mono.just(ToolResult.error("MCP_EXECUTION_ERROR", e.getMessage()));
                });
    }
}
