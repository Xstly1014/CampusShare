package com.campushare.agent.mcp.server;

import com.campushare.agent.mcp.McpProtocol;
import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolRegistry;
import com.campushare.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mcp.server", name = "enabled", havingValue = "true")
public class McpServerController {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @PostMapping
    public Mono<McpProtocol.Response> handleRequest(@RequestBody McpProtocol.Request request) {
        log.debug("MCP request: method={}, id={}", request.getMethod(), request.getId());

        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request);
            case "tools/list" -> handleListTools(request);
            case "tools/call" -> handleCallTool(request);
            default -> handleUnknownMethod(request);
        };
    }

    private Mono<McpProtocol.Response> handleInitialize(McpProtocol.Request request) {
        McpProtocol.InitializeResult result = McpProtocol.InitializeResult.builder()
                .protocolVersion("2024-11-05")
                .serverInfo(McpProtocol.ServerInfo.builder()
                        .name("campushare-agent-mcp")
                        .version("1.0.0")
                        .build())
                .capabilities(McpProtocol.Capabilities.builder()
                        .tools(McpProtocol.ToolsCapability.builder()
                                .listChanged(false)
                                .build())
                        .build())
                .build();

        return Mono.just(McpProtocol.Response.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .result(result)
                .build());
    }

    private Mono<McpProtocol.Response> handleListTools(McpProtocol.Request request) {
        List<McpProtocol.Tool> tools = toolRegistry.getAllToolSchemas().stream()
                .map(schema -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> func = (Map<String, Object>) schema.get("function");
                    return McpProtocol.Tool.builder()
                            .name((String) func.get("name"))
                            .description((String) func.get("description"))
                            .inputSchema((Map<String, Object>) func.get("parameters"))
                            .build();
                })
                .collect(Collectors.toList());

        McpProtocol.ListToolsResult result = McpProtocol.ListToolsResult.builder()
                .tools(tools)
                .build();

        return Mono.just(McpProtocol.Response.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .result(result)
                .build());
    }

    @SuppressWarnings("unchecked")
    private Mono<McpProtocol.Response> handleCallTool(McpProtocol.Request request) {
        try {
            Map<String, Object> params = request.getParams();
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());

            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                return Mono.just(McpProtocol.Response.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .error(McpProtocol.ErrorObject.builder()
                                .code(-32602)
                                .message("Tool not found: " + toolName)
                                .build())
                        .build());
            }

            ToolResult toolResult = tool.execute(arguments, "mcp-user");

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", objectMapper.writeValueAsString(toolResult));
            content.add(textContent);

            McpProtocol.CallToolResult result = McpProtocol.CallToolResult.builder()
                    .content(content)
                    .isError(toolResult.getStatus() == ToolResult.Status.ERROR)
                    .build();

            return Mono.just(McpProtocol.Response.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .result(result)
                    .build());

        } catch (Exception e) {
            log.error("MCP tool call error", e);
            return Mono.just(McpProtocol.Response.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .error(McpProtocol.ErrorObject.builder()
                            .code(-32603)
                            .message("Internal error: " + e.getMessage())
                            .build())
                    .build());
        }
    }

    private Mono<McpProtocol.Response> handleUnknownMethod(McpProtocol.Request request) {
        return Mono.just(McpProtocol.Response.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .error(McpProtocol.ErrorObject.builder()
                        .code(-32601)
                        .message("Method not found: " + request.getMethod())
                        .build())
                .build());
    }
}
