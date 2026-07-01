package com.campushare.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final WebClient deepSeekWebClient;
    private final ObjectMapper objectMapper;

    @Value("${app.llm.deepseek.model:deepseek-chat}")
    private String defaultModel;

    @Value("${app.llm.deepseek.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${app.llm.deepseek.max-tokens:2048}")
    private Integer defaultMaxTokens;

    public Mono<DeepSeekResponse> chatCompletion(List<DeepSeekRequest.Message> messages) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(defaultModel)
                .messages(messages)
                .stream(false)
                .temperature(defaultTemperature)
                .maxTokens(defaultMaxTokens)
                .build();

        return deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .doOnError(e -> log.error("DeepSeek API error", e));
    }

    public Flux<String> chatCompletionStream(List<DeepSeekRequest.Message> messages) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(defaultModel)
                .messages(messages)
                .stream(true)
                .temperature(defaultTemperature)
                .maxTokens(defaultMaxTokens)
                .build();

        return deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null)
                .takeUntil(sse -> "[DONE]".equals(sse.data()))
                .filter(sse -> !"[DONE]".equals(sse.data()))
                .mapNotNull(this::extractContent);
    }

    private String extractContent(ServerSentEvent<String> sse) {
        try {
            DeepSeekResponse resp = objectMapper.readValue(sse.data(), DeepSeekResponse.class);
            if (resp.getChoices() != null && !resp.getChoices().isEmpty()) {
                DeepSeekResponse.Choice choice = resp.getChoices().get(0);
                if (choice.getDelta() != null) {
                    return choice.getDelta().getContent();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE chunk: {}", sse.data(), e);
        }
        return null;
    }
}
