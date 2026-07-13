package com.campushare.agent.llm.gateway;

import com.campushare.agent.llm.DeepSeekResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    String getProvider();

    String getModel();

    Mono<LlmResponse> chatCompletion(List<Map<String, Object>> messages,
                                     Double temperature, Integer maxTokens,
                                     List<Map<String, Object>> tools);

    Flux<LlmStreamChunk> chatCompletionStream(List<Map<String, Object>> messages,
                                               Double temperature, Integer maxTokens);

    Mono<LlmResponse> embedding(List<String> texts);

    boolean isHealthy();

    void markUnhealthy(long coolDownMs);

    long getLastHealthCheckTime();
}
