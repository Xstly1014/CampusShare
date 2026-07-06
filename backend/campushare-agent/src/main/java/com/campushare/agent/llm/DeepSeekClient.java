package com.campushare.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final WebClient deepSeekWebClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker deepSeekCircuitBreaker;

    @Value("${app.llm.deepseek.model:deepseek-v4-flash}")
    private String defaultModel;

    @Value("${app.llm.deepseek.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${app.llm.deepseek.max-tokens:2048}")
    private Integer defaultMaxTokens;

    @Value("${app.llm.deepseek.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.llm.deepseek.retry.backoff:1000}")
    private long retryBackoffMs;

    public Mono<DeepSeekResponse> chatCompletion(List<DeepSeekRequest.Message> messages) {
        return chatCompletion(messages, defaultTemperature, defaultMaxTokens);
    }

    /**
     * 非流式 LLM 调用（支持自定义 temperature/maxTokens）。
     *
     * 用于意图分类等需要低温度（temperature=0）和短输出（max_tokens=200）的场景。
     *
     * @param messages     消息列表
     * @param temperature  温度参数（null 用默认值）
     * @param maxTokens    最大输出 token（null 用默认值）
     * @return DeepSeekResponse
     */
    public Mono<DeepSeekResponse> chatCompletion(List<DeepSeekRequest.Message> messages,
                                                  Double temperature, Integer maxTokens) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(defaultModel)
                .messages(messages)
                .stream(false)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        return deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .transform(CircuitBreakerOperator.of(deepSeekCircuitBreaker))
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(retryBackoffMs))
                        .filter(this::isRetryable)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying DeepSeek API call, attempt {}", retrySignal.totalRetries() + 1))
                )
                .doOnError(e -> log.error("DeepSeek API error after retries", e));
    }

    public Flux<StreamChunk> chatCompletionStream(List<DeepSeekRequest.Message> messages) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(defaultModel)
                .messages(messages)
                .stream(true)
                .temperature(defaultTemperature)
                .maxTokens(defaultMaxTokens)
                .streamOptions(DeepSeekRequest.StreamOptions.builder()
                        .includeUsage(true)
                        .build())
                .build();

        return deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null)
                .mapNotNull(this::parseStreamChunk)
                .takeUntil(chunk -> "[DONE]".equals(chunk.content()))
                .filter(chunk -> !"[DONE]".equals(chunk.content()))
                .transform(CircuitBreakerOperator.of(deepSeekCircuitBreaker))
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(retryBackoffMs))
                        .filter(this::isRetryable)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying DeepSeek stream, attempt {}", retrySignal.totalRetries() + 1))
                )
                .doOnError(e -> log.error("DeepSeek stream error after retries", e));
    }

    private StreamChunk parseStreamChunk(ServerSentEvent<String> sse) {
        String data = sse.data();

        if ("[DONE]".equals(data)) {
            return new StreamChunk("[DONE]", null);
        }

        try {
            DeepSeekResponse resp = objectMapper.readValue(data, DeepSeekResponse.class);

            if (resp.getUsage() != null) {
                return new StreamChunk(null, resp.getUsage());
            }

            if (resp.getChoices() != null && !resp.getChoices().isEmpty()) {
                DeepSeekResponse.Choice choice = resp.getChoices().get(0);
                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                    return new StreamChunk(choice.getDelta().getContent(), null);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE chunk: {}", data, e);
        }
        return null;
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof TimeoutException
                || throwable instanceof IOException;
    }

    public record StreamChunk(String content, DeepSeekResponse.Usage usage) {}
}
