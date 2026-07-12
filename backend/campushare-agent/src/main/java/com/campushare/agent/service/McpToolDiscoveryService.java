package com.campushare.agent.service;

import com.campushare.agent.mcp.McpClientManager;
import com.campushare.agent.mcp.McpProtocol;
import com.campushare.agent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolDiscoveryService {

    private final McpClientManager mcpClientManager;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    private final Map<String, String> mcpToolServerMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!mcpClientManager.isEnabled()) {
            log.info("MCP is disabled, skipping tool discovery");
            return;
        }
        discoverAndRegisterTools()
                .doOnSuccess(count -> log.info("MCP tool discovery complete: {} tools registered from MCP servers", count))
                .doOnError(e -> log.error("MCP tool discovery failed", e))
                .subscribe();
    }

    public Mono<Integer> discoverAndRegisterTools() {
        Set<String> serverNames = mcpClientManager.getServerNames();
        if (serverNames.isEmpty()) {
            return Mono.just(0);
        }

        List<Mono<Integer>> discoveryMonos = new ArrayList<>();
        for (String serverName : serverNames) {
            discoveryMonos.add(discoverServerTools(serverName));
        }

        return Mono.zip(discoveryMonos, results -> {
            int total = 0;
            for (Object r : results) {
                total += (Integer) r;
            }
            return total;
        });
    }

    private Mono<Integer> discoverServerTools(String serverName) {
        return mcpClientManager.listTools(serverName)
                .map(tools -> {
                    int registered = 0;
                    for (McpProtocol.Tool mcpTool : tools) {
                        try {
                            registerMcpTool(serverName, mcpTool);
                            registered++;
                        } catch (Exception e) {
                            log.warn("Failed to register MCP tool: {}/{}", serverName, mcpTool.getName(), e);
                        }
                    }
                    return registered;
                });
    }

    @SuppressWarnings("unchecked")
    private void registerMcpTool(String serverName, McpProtocol.Tool mcpTool) throws Exception {
        String toolName = mcpTool.getName();
        mcpToolServerMap.put(toolName, serverName);

        McpToolAdapter adapter = new McpToolAdapter(serverName, mcpTool, mcpClientManager, objectMapper);

        Field toolMapField = ToolRegistry.class.getDeclaredField("toolMap");
        toolMapField.setAccessible(true);
        Map<String, Tool> toolMap = (Map<String, Tool>) toolMapField.get(toolRegistry);
        toolMap.put(toolName, adapter);

        Field definitionMapField = ToolRegistry.class.getDeclaredField("definitionMap");
        definitionMapField.setAccessible(true);
        Map<String, ToolRegistry.ToolDefinition> definitionMap =
                (Map<String, ToolRegistry.ToolDefinition>) definitionMapField.get(toolRegistry);

        ToolRegistry.ToolDefinition definition = new ToolRegistry.ToolDefinition(
                toolName,
                mcpTool.getDescription(),
                List.of(),
                true,
                10000,
                mcpTool.getInputSchema() != null ? mcpTool.getInputSchema() : Map.of()
        );
        definitionMap.put(toolName, definition);

        log.info("Registered MCP tool: {}/{}", serverName, toolName);
    }

    public String getServerForTool(String toolName) {
        return mcpToolServerMap.get(toolName);
    }

    public boolean isMcpTool(String toolName) {
        return mcpToolServerMap.containsKey(toolName);
    }
}
