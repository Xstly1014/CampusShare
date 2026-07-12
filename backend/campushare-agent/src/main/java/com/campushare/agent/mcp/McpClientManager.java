package com.campushare.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpClientConfig config;
    private final ObjectMapper objectMapper;

    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);

    @EventListener(ApplicationReadyEvent.class)
    public void connectAll() {
        if (!config.isEnabled()) {
            log.info("MCP is disabled, skipping connection");
            return;
        }
        for (McpClientConfig.ServerConfig server : config.getServers()) {
            if (!server.isEnabled()) continue;
            try {
                McpClient client = createClient(server);
                clients.put(server.getName(), client);
                log.info("MCP Server registered: {}", server.getName());
            } catch (Exception e) {
                log.error("Failed to register MCP Server: {}", server.getName(), e);
            }
        }
        log.info("MCP Client Manager initialized: {} servers", clients.size());
    }

    private McpClient createClient(McpClientConfig.ServerConfig serverConfig) {
        WebClient webClient = WebClient.builder()
                .baseUrl(serverConfig.getUrl())
                .build();

        return new McpClient(serverConfig.getName(), webClient, objectMapper, requestIdCounter);
    }

    public Mono<List<McpProtocol.Tool>> listTools(String serverName) {
        McpClient client = clients.get(serverName);
        if (client == null) {
            return Mono.just(List.of());
        }
        return client.listTools();
    }

    public Mono<McpProtocol.CallToolResult> callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpClient client = clients.get(serverName);
        if (client == null) {
            return Mono.just(McpProtocol.CallToolResult.builder()
                    .isError(true)
                    .content("MCP Server not found: " + serverName)
                    .build());
        }
        return client.callTool(toolName, arguments);
    }

    public Set<String> getServerNames() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    public boolean isEnabled() {
        return config.isEnabled() && !clients.isEmpty();
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class McpClient {
        private final String serverName;
        private final WebClient webClient;
        private final ObjectMapper objectMapper;
        private final AtomicInteger requestIdCounter;

        public Mono<McpProtocol.InitializeResult> initialize() {
            McpProtocol.Request request = McpProtocol.Request.builder()
                    .jsonrpc("2.0")
                    .id(generateId())
                    .method("initialize")
                    .params(Map.of(
                            "protocolVersion", "2024-11-05",
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "campushare-agent", "version", "1.0.0")
                    ))
                    .build();

            return sendRequest(request)
                    .map(response -> {
                        if (response.getError() != null) {
                            throw new RuntimeException("MCP initialize failed: " + response.getError().getMessage());
                        }
                        return objectMapper.convertValue(response.getResult(), McpProtocol.InitializeResult.class);
                    });
        }

        public Mono<List<McpProtocol.Tool>> listTools() {
            McpProtocol.Request request = McpProtocol.Request.builder()
                    .jsonrpc("2.0")
                    .id(generateId())
                    .method("tools/list")
                    .build();

            return sendRequest(request)
                    .map(response -> {
                        if (response.getError() != null) {
                            log.warn("MCP listTools failed for {}: {}", serverName, response.getError().getMessage());
                            return List.<McpProtocol.Tool>of();
                        }
                        McpProtocol.ListToolsResult result = objectMapper.convertValue(
                                response.getResult(), McpProtocol.ListToolsResult.class);
                        return result.getTools() != null ? result.getTools() : Collections.<McpProtocol.Tool>emptyList();
                    })
                    .onErrorResume(e -> {
                        log.error("MCP listTools error for {}", serverName, e);
                        return Mono.just(Collections.<McpProtocol.Tool>emptyList());
                    });
        }

        public Mono<McpProtocol.CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
            Map<String, Object> params = new HashMap<>();
            params.put("name", toolName);
            if (arguments != null) {
                params.put("arguments", arguments);
            }

            McpProtocol.Request request = McpProtocol.Request.builder()
                    .jsonrpc("2.0")
                    .id(generateId())
                    .method("tools/call")
                    .params(params)
                    .build();

            return sendRequest(request)
                    .map(response -> {
                        if (response.getError() != null) {
                            return McpProtocol.CallToolResult.builder()
                                    .isError(true)
                                    .content(response.getError().getMessage())
                                    .build();
                        }
                        return objectMapper.convertValue(response.getResult(), McpProtocol.CallToolResult.class);
                    })
                    .onErrorResume(e -> {
                        log.error("MCP callTool error: {}/{}", serverName, toolName, e);
                        return Mono.just(McpProtocol.CallToolResult.builder()
                                .isError(true)
                                .content(e.getMessage())
                                .build());
                    });
        }

        private Mono<McpProtocol.Response> sendRequest(McpProtocol.Request request) {
            return webClient.post()
                    .uri("/mcp")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(McpProtocol.Response.class)
                    .doOnError(e -> log.debug("MCP request error: {}/{}", serverName, request.getMethod(), e));
        }

        private String generateId() {
            return "req-" + requestIdCounter.incrementAndGet();
        }
    }
}
