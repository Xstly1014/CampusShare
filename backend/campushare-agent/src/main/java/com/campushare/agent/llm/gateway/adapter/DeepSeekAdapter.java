package com.campushare.agent.llm.gateway.adapter;

import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.llm.gateway.LlmClient;
import com.campushare.agent.llm.gateway.LlmProvider;
import com.campushare.agent.llm.gateway.LlmResponse;
import com.campushare.agent.llm.gateway.LlmStreamChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekAdapter implements LlmClient {

    private final DeepSeekClient deepSeekClient;

    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicLong lastHealthCheckTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong coolDownUntil = new AtomicLong(0);

    @Override
    public String getProvider() {
        return LlmProvider.DEEPSEEK.name();
    }

    @Override
    public String getModel() {
        return deepSeekClient.getDefaultModel();
    }

    @Override
    public Mono<LlmResponse> chatCompletion(List<Map<String, Object>> messages,
                                            Double temperature, Integer maxTokens,
                                            List<Map<String, Object>> tools) {
        if (!isHealthy()) {
            return Mono.just(LlmResponse.failure("DeepSeek provider is unhealthy"));
        }

        List<DeepSeekRequest.Message> deepSeekMessages = convertMessages(messages);
        List<Map<String, Object>> deepSeekTools = tools;

        return deepSeekClient.chatCompletion(deepSeekMessages, temperature, maxTokens, deepSeekTools)
                .map(this::convertResponse)
                .onErrorResume(e -> {
                    log.error("DeepSeek adapter error", e);
                    markUnhealthy(60000);
                    return Mono.just(LlmResponse.failure(e.getMessage()));
                });
    }

    @Override
    public Flux<LlmStreamChunk> chatCompletionStream(List<Map<String, Object>> messages,
                                                      Double temperature, Integer maxTokens) {
        if (!isHealthy()) {
            return Flux.just(LlmStreamChunk.error("DeepSeek provider is unhealthy"));
        }

        List<DeepSeekRequest.Message> deepSeekMessages = convertMessages(messages);

        return deepSeekClient.chatCompletionStream(deepSeekMessages)
                .map(chunk -> {
                    if (chunk.content() != null && "[响应超时，请重试]".equals(chunk.content())) {
                        markUnhealthy(60000);
                        return LlmStreamChunk.error(chunk.content());
                    }
                    if (chunk.content() != null && "[DONE]".equals(chunk.content())) {
                        return LlmStreamChunk.done();
                    }
                    if (chunk.content() != null) {
                        return LlmStreamChunk.content(chunk.content());
                    }
                    if (chunk.usage() != null) {
                        return LlmStreamChunk.usage(convertUsage(chunk.usage()));
                    }
                    return null;
                })
                .filter(chunk -> chunk != null)
                .onErrorResume(e -> {
                    log.error("DeepSeek stream adapter error", e);
                    markUnhealthy(60000);
                    return Flux.just(LlmStreamChunk.error(e.getMessage()));
                });
    }

    @Override
    public Mono<LlmResponse> embedding(List<String> texts) {
        return Mono.just(LlmResponse.failure("Embedding not supported via LlmClient, use EmbeddingClient directly"));
    }

    @Override
    public boolean isHealthy() {
        if (coolDownUntil.get() > System.currentTimeMillis()) {
            return false;
        }
        return healthy.get();
    }

    @Override
    public void markUnhealthy(long coolDownMs) {
        healthy.set(false);
        coolDownUntil.set(System.currentTimeMillis() + coolDownMs);
        lastHealthCheckTime.set(System.currentTimeMillis());
        log.warn("DeepSeek provider marked unhealthy, cooldown until {}ms", coolDownUntil.get());
    }

    @Override
    public long getLastHealthCheckTime() {
        return lastHealthCheckTime.get();
    }

    private List<DeepSeekRequest.Message> convertMessages(List<Map<String, Object>> messages) {
        List<DeepSeekRequest.Message> result = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");

            DeepSeekRequest.Message message = DeepSeekRequest.Message.builder()
                    .role(role)
                    .content(content)
                    .build();

            if (msg.containsKey("tool_calls")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
                List<DeepSeekRequest.ToolCall> deepSeekToolCalls = new ArrayList<>();
                for (Map<String, Object> tc : toolCalls) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> func = (Map<String, Object>) tc.get("function");
                    DeepSeekRequest.FunctionCall functionCall = DeepSeekRequest.FunctionCall.builder()
                            .name((String) func.get("name"))
                            .arguments((String) func.get("arguments"))
                            .build();
                    deepSeekToolCalls.add(DeepSeekRequest.ToolCall.builder()
                            .id((String) tc.get("id"))
                            .type((String) tc.get("type"))
                            .function(functionCall)
                            .build());
                }
                message.setToolCalls(deepSeekToolCalls);
            }

            if (msg.containsKey("tool_call_id")) {
                message.setToolCallId((String) msg.get("tool_call_id"));
            }

            if (msg.containsKey("name")) {
                message.setName((String) msg.get("name"));
            }

            result.add(message);
        }
        return result;
    }

    private LlmResponse convertResponse(DeepSeekResponse response) {
        if (response.hasToolCalls()) {
            List<LlmResponse.ToolCall> toolCalls = new ArrayList<>();
            for (DeepSeekResponse.ToolCall tc : response.getToolCalls()) {
                Map<String, Object> arguments = new HashMap<>();
                if (tc.getFunction() != null && tc.getFunction().getArguments() != null) {
                    try {
                        arguments = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(tc.getFunction().getArguments(), Map.class);
                    } catch (Exception e) {
                        arguments.put("raw", tc.getFunction().getArguments());
                    }
                }
                toolCalls.add(LlmResponse.ToolCall.builder()
                        .id(tc.getId())
                        .toolName(tc.getFunction() != null ? tc.getFunction().getName() : "")
                        .arguments(arguments)
                        .build());
            }
            return LlmResponse.toolCall(toolCalls, convertUsage(response.getUsage()),
                    getProvider(), getModel());
        }

        String content = response.getContent();
        return LlmResponse.success(content, convertUsage(response.getUsage()),
                getProvider(), getModel());
    }

    private LlmResponse.Usage convertUsage(DeepSeekResponse.Usage usage) {
        if (usage == null) return null;
        return LlmResponse.Usage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }
}
