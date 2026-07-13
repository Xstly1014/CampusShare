package com.campushare.agent.llm.gateway;

import com.campushare.agent.enums.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGateway {

    private final List<LlmClient> providers = new CopyOnWriteArrayList<>();
    private final Map<String, LlmClient> providerMap = new ConcurrentHashMap<>();
    private final LlmGatewayConfig config;

    public void registerProvider(LlmClient client) {
        providers.add(client);
        providerMap.put(client.getProvider().toLowerCase(), client);
        log.info("Registered LLM provider: {} (model: {})", client.getProvider(), client.getModel());
    }

    public Mono<LlmResponse> chatCompletion(List<Map<String, Object>> messages,
                                            Double temperature, Integer maxTokens,
                                            List<Map<String, Object>> tools) {
        return chatCompletion(messages, temperature, maxTokens, tools, null);
    }

    public Mono<LlmResponse> chatCompletion(List<Map<String, Object>> messages,
                                            Double temperature, Integer maxTokens,
                                            List<Map<String, Object>> tools,
                                            Intent intent) {
        List<LlmClient> availableProviders = getAvailableProviders(intent);

        if (availableProviders.isEmpty()) {
            log.error("No healthy LLM providers available");
            return Mono.just(LlmResponse.failure("所有LLM服务暂时不可用，请稍后重试"));
        }

        return attemptFallbackChain(messages, temperature, maxTokens, tools, availableProviders, 0);
    }

    public Flux<LlmStreamChunk> chatCompletionStream(List<Map<String, Object>> messages,
                                                      Double temperature, Integer maxTokens) {
        return chatCompletionStream(messages, temperature, maxTokens, null);
    }

    public Flux<LlmStreamChunk> chatCompletionStream(List<Map<String, Object>> messages,
                                                      Double temperature, Integer maxTokens,
                                                      Intent intent) {
        List<LlmClient> availableProviders = getAvailableProviders(intent);

        if (availableProviders.isEmpty()) {
            log.error("No healthy LLM providers available for streaming");
            return Flux.just(LlmStreamChunk.error("所有LLM服务暂时不可用，请稍后重试"));
        }

        return attemptFallbackChainStream(messages, temperature, maxTokens, availableProviders, 0);
    }

    private List<LlmClient> getAvailableProviders(Intent intent) {
        List<LlmClient> allProviders = providers.stream()
                .filter(LlmClient::isHealthy)
                .sorted((a, b) -> {
                    String aProvider = a.getProvider().toLowerCase();
                    String bProvider = b.getProvider().toLowerCase();
                    int priorityA = config.getProviders().getOrDefault(aProvider, new LlmGatewayConfig.ProviderConfig()).getPriority();
                    int priorityB = config.getProviders().getOrDefault(bProvider, new LlmGatewayConfig.ProviderConfig()).getPriority();
                    return Integer.compare(priorityA, priorityB);
                })
                .collect(Collectors.toList());

        if (intent != null) {
            allProviders = applyIntentRouting(intent, allProviders);
        }

        return allProviders;
    }

    private List<LlmClient> applyIntentRouting(Intent intent, List<LlmClient> providers) {
        List<LlmClient> filtered = new ArrayList<>(providers);

        switch (intent) {
            case NAVIGATE:
            case OUT_OF_SCOPE:
                filtered = providers.stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase("DEEPSEEK"))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) filtered = providers;
                break;
            case SEARCH:
            case HOW_TO:
                filtered = providers.stream()
                        .filter(p -> {
                            String provider = p.getProvider().toLowerCase();
                            return provider.equals("deepseek") || provider.equals("openai");
                        })
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) filtered = providers;
                break;
            case CLARIFY:
                filtered = providers.stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase("DEEPSEEK"))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) filtered = providers;
                break;
        }

        return filtered;
    }

    private Mono<LlmResponse> attemptFallbackChain(List<Map<String, Object>> messages,
                                                    Double temperature, Integer maxTokens,
                                                    List<Map<String, Object>> tools,
                                                    List<LlmClient> providers, int attemptIndex) {
        if (attemptIndex >= providers.size()) {
            log.error("All LLM providers failed after {} attempts", attemptIndex);
            return Mono.just(LlmResponse.failure("所有LLM服务暂时不可用，请稍后重试"));
        }

        LlmClient currentProvider = providers.get(attemptIndex);
        log.debug("Attempting LLM call with provider: {} (attempt {}/{})",
                currentProvider.getProvider(), attemptIndex + 1, providers.size());

        return currentProvider.chatCompletion(messages, temperature, maxTokens, tools)
                .flatMap(response -> {
                    if (response.isSuccess()) {
                        log.info("LLM call succeeded with provider: {}", currentProvider.getProvider());
                        return Mono.just(response);
                    } else {
                        log.warn("LLM call failed with provider: {} - {}",
                                currentProvider.getProvider(), response.getErrorMessage());
                        currentProvider.markUnhealthy(30000);
                        return attemptFallbackChain(messages, temperature, maxTokens, tools, providers, attemptIndex + 1);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("LLM call error with provider: {} - {}",
                            currentProvider.getProvider(), e.getMessage());
                    currentProvider.markUnhealthy(60000);
                    return attemptFallbackChain(messages, temperature, maxTokens, tools, providers, attemptIndex + 1);
                });
    }

    private Flux<LlmStreamChunk> attemptFallbackChainStream(List<Map<String, Object>> messages,
                                                              Double temperature, Integer maxTokens,
                                                              List<LlmClient> providers, int attemptIndex) {
        if (attemptIndex >= providers.size()) {
            log.error("All LLM providers failed for streaming after {} attempts", attemptIndex);
            return Flux.just(LlmStreamChunk.error("所有LLM服务暂时不可用，请稍后重试"));
        }

        LlmClient currentProvider = providers.get(attemptIndex);
        log.debug("Attempting streaming LLM call with provider: {} (attempt {}/{})",
                currentProvider.getProvider(), attemptIndex + 1, providers.size());

        return currentProvider.chatCompletionStream(messages, temperature, maxTokens)
                .flatMap(chunk -> {
                    if (chunk.getErrorMessage() != null) {
                        log.warn("Streaming error with provider: {} - {}",
                                currentProvider.getProvider(), chunk.getErrorMessage());
                        currentProvider.markUnhealthy(60000);
                        return attemptFallbackChainStream(messages, temperature, maxTokens, providers, attemptIndex + 1);
                    }
                    if (chunk.isDone()) {
                        log.info("Streaming completed with provider: {}", currentProvider.getProvider());
                    }
                    return Flux.just(chunk);
                })
                .onErrorResume(e -> {
                    log.warn("Streaming error with provider: {} - {}",
                            currentProvider.getProvider(), e.getMessage());
                    currentProvider.markUnhealthy(60000);
                    return attemptFallbackChainStream(messages, temperature, maxTokens, providers, attemptIndex + 1);
                });
    }

    public Map<String, ProviderStatus> getProviderStatuses() {
        Map<String, ProviderStatus> statuses = new ConcurrentHashMap<>();
        for (LlmClient provider : providers) {
            statuses.put(provider.getProvider(), ProviderStatus.builder()
                    .provider(provider.getProvider())
                    .model(provider.getModel())
                    .healthy(provider.isHealthy())
                    .lastHealthCheckTime(provider.getLastHealthCheckTime())
                    .build());
        }
        return statuses;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProviderStatus {
        private String provider;
        private String model;
        private boolean healthy;
        private long lastHealthCheckTime;
    }
}
