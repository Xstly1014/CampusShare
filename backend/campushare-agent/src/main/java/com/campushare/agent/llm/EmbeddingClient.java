package com.campushare.agent.llm;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 硅基流动 Embedding 客户端。
 *
 * 参考 DeepSeekClient 模式：
 * - WebClient + CircuitBreakerOperator + Retry.backoff
 * - 熔断打开时返回 Mono.empty()（调用方降级为空检索上下文）
 *
 * 核心方法：
 * - embed(String text): 单条文本 → 1024 维向量
 * - embedBatch(List<String> texts): 批量 embedding（batch-size=32）
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final WebClient embeddingWebClient;
    private final CircuitBreaker embeddingCircuitBreaker;

    @Value("${app.llm.embedding.model:BAAI/bge-m3}")
    private String model;

    @Value("${app.llm.embedding.batch-size:32}")
    private int batchSize;

    @Value("${app.llm.embedding.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.llm.embedding.retry.backoff:1000}")
    private long retryBackoffMs;

    public EmbeddingClient(@Qualifier("embeddingWebClient") WebClient embeddingWebClient,
                           CircuitBreaker embeddingCircuitBreaker) {
        this.embeddingWebClient = embeddingWebClient;
        this.embeddingCircuitBreaker = embeddingCircuitBreaker;
    }

    /**
     * 单条文本 embedding。
     * 失败时返回 Mono.empty()，调用方降级处理。
     */
    public Mono<float[]> embed(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(model)
                .input(text)
                .build();

        return embeddingWebClient.post()
                .uri("/v1/embeddings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .map(resp -> {
                    if (resp.getData() == null || resp.getData().isEmpty()) {
                        log.warn("Embedding response has no data");
                        return new float[0];
                    }
                    return resp.getData().get(0).getEmbedding();
                })
                .transform(CircuitBreakerOperator.of(embeddingCircuitBreaker))
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(retryBackoffMs))
                        .filter(this::isRetryable)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying embedding API call, attempt {}", retrySignal.totalRetries() + 1))
                )
                .doOnError(e -> log.error("Embedding API error after retries", e))
                .onErrorResume(e -> {
                    log.warn("Embedding failed, degrading to empty vector", e);
                    return Mono.just(new float[0]);
                });
    }

    /**
     * 批量文本 embedding。
     * 按 batch-size 分片调用，合并结果。
     * 单批失败返回空列表，不影响其他批次。
     */
    public Mono<List<float[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        List<List<String>> batches = partition(texts, batchSize);
        List<Mono<List<float[]>>> monos = new ArrayList<>(batches.size());

        for (List<String> batch : batches) {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(model)
                    .input(batch)
                    .build();

            Mono<List<float[]>> batchMono = embeddingWebClient.post()
                    .uri("/v1/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .map(resp -> {
                        if (resp.getData() == null) {
                            return Collections.<float[]>emptyList();
                        }
                        return resp.getData().stream()
                                .sorted(Comparator.comparingInt(EmbeddingResponse.EmbeddingItem::getIndex))
                                .map(EmbeddingResponse.EmbeddingItem::getEmbedding)
                                .collect(Collectors.toList());
                    })
                    .transform(CircuitBreakerOperator.of(embeddingCircuitBreaker))
                    .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(retryBackoffMs))
                            .filter(this::isRetryable)
                            .doBeforeRetry(retrySignal ->
                                    log.warn("Retrying embedding batch, attempt {}", retrySignal.totalRetries() + 1))
                    )
                    .onErrorResume(e -> {
                        log.warn("Embedding batch failed, skipping", e);
                        return Mono.just(Collections.emptyList());
                    });

            monos.add(batchMono);
        }

        return Mono.zip(monos, results -> {
            List<float[]> merged = new ArrayList<>(texts.size());
            for (Object result : results) {
                @SuppressWarnings("unchecked")
                List<float[]> batchResult = (List<float[]>) result;
                merged.addAll(batchResult);
            }
            return merged;
        }).defaultIfEmpty(Collections.emptyList());
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof TimeoutException
                || throwable instanceof IOException;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
