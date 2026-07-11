package com.campushare.agent.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private final MeterRegistry meterRegistry;

    @Value("${app.llm.deepseek.circuit-breaker.sliding-window-size:10}")
    private int deepseekSlidingWindowSize;

    @Value("${app.llm.deepseek.circuit-breaker.minimum-number-of-calls:5}")
    private int deepseekMinimumCalls;

    @Value("${app.llm.deepseek.circuit-breaker.failure-rate-threshold:50.0}")
    private float deepseekFailureRate;

    @Value("${app.llm.deepseek.circuit-breaker.wait-duration-in-open-state:30s}")
    private Duration deepseekWaitDuration;

    @Value("${app.llm.deepseek.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int deepseekHalfOpenCalls;

    @Value("${app.llm.embedding.circuit-breaker.sliding-window-size:10}")
    private int embSlidingWindowSize;

    @Value("${app.llm.embedding.circuit-breaker.minimum-number-of-calls:5}")
    private int embMinimumCalls;

    @Value("${app.llm.embedding.circuit-breaker.failure-rate-threshold:50.0}")
    private float embFailureRate;

    @Value("${app.llm.embedding.circuit-breaker.wait-duration-in-open-state:30s}")
    private Duration embWaitDuration;

    @Value("${app.llm.embedding.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int embHalfOpenCalls;

    @Value("${app.post-sync.circuit-breaker.sliding-window-size:10}")
    private int syncSlidingWindowSize;

    @Value("${app.post-sync.circuit-breaker.minimum-number-of-calls:5}")
    private int syncMinimumCalls;

    @Value("${app.post-sync.circuit-breaker.failure-rate-threshold:50.0}")
    private float syncFailureRate;

    @Value("${app.post-sync.circuit-breaker.wait-duration-in-open-state:30s}")
    private Duration syncWaitDuration;

    @Value("${app.post-sync.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int syncHalfOpenCalls;

    @Value("${app.intent.classifier.circuit-breaker.sliding-window-size:10}")
    private int intentSlidingWindowSize;

    @Value("${app.intent.classifier.circuit-breaker.minimum-number-of-calls:5}")
    private int intentMinimumCalls;

    @Value("${app.intent.classifier.circuit-breaker.failure-rate-threshold:50.0}")
    private float intentFailureRate;

    @Value("${app.intent.classifier.circuit-breaker.wait-duration-in-open-state:30s}")
    private Duration intentWaitDuration;

    @Value("${app.intent.classifier.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int intentHalfOpenCalls;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        registry.circuitBreaker("deepseek", deepseekConfig());
        registry.circuitBreaker("embedding", embeddingConfig());
        registry.circuitBreaker("post-sync", postSyncConfig());
        registry.circuitBreaker("intent-classifier", intentClassifierConfig());

        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);

        return registry;
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();

        registry.rateLimiter("agent-global", RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build());

        TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry).bindTo(meterRegistry);

        return registry;
    }

    @Bean
    public CircuitBreaker deepSeekCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("deepseek");
    }

    @Bean
    public CircuitBreaker embeddingCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("embedding");
    }

    @Bean
    public CircuitBreaker postSyncCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("post-sync");
    }

    @Bean
    public CircuitBreaker intentClassifierCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("intent-classifier");
    }

    @Bean
    public RateLimiter agentGlobalRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("agent-global");
    }

    private CircuitBreakerConfig deepseekConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(deepseekSlidingWindowSize)
                .minimumNumberOfCalls(deepseekMinimumCalls)
                .failureRateThreshold(deepseekFailureRate)
                .waitDurationInOpenState(deepseekWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(deepseekHalfOpenCalls)
                .recordExceptions(
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.reactive.function.client.WebClientRequestException.class,
                        java.io.IOException.class,
                        com.campushare.agent.exception.LlmApiException.class
                )
                .ignoreExceptions(com.campushare.common.exception.BusinessException.class)
                .build();
    }

    private CircuitBreakerConfig embeddingConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(embSlidingWindowSize)
                .minimumNumberOfCalls(embMinimumCalls)
                .failureRateThreshold(embFailureRate)
                .waitDurationInOpenState(embWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(embHalfOpenCalls)
                .recordExceptions(
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.reactive.function.client.WebClientRequestException.class,
                        java.io.IOException.class
                )
                .ignoreExceptions(com.campushare.common.exception.BusinessException.class)
                .build();
    }

    private CircuitBreakerConfig postSyncConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(syncSlidingWindowSize)
                .minimumNumberOfCalls(syncMinimumCalls)
                .failureRateThreshold(syncFailureRate)
                .waitDurationInOpenState(syncWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(syncHalfOpenCalls)
                .recordExceptions(
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.reactive.function.client.WebClientRequestException.class,
                        java.io.IOException.class
                )
                .ignoreExceptions(com.campushare.common.exception.BusinessException.class)
                .build();
    }

    private CircuitBreakerConfig intentClassifierConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(intentSlidingWindowSize)
                .minimumNumberOfCalls(intentMinimumCalls)
                .failureRateThreshold(intentFailureRate)
                .waitDurationInOpenState(intentWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(intentHalfOpenCalls)
                .recordExceptions(
                        java.util.concurrent.TimeoutException.class,
                        java.io.IOException.class
                )
                .ignoreExceptions(com.campushare.common.exception.BusinessException.class)
                .build();
    }
}
