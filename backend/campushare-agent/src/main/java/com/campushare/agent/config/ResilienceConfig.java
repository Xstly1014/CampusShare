package com.campushare.agent.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Value("${app.llm.deepseek.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${app.llm.deepseek.circuit-breaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${app.llm.deepseek.circuit-breaker.failure-rate-threshold:50.0}")
    private float failureRateThreshold;

    @Value("${app.llm.deepseek.circuit-breaker.wait-duration-in-open-state:30s}")
    private Duration waitDurationInOpenState;

    @Value("${app.llm.deepseek.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int permittedNumberOfCallsInHalfOpenState;

    @Bean
    public CircuitBreaker deepSeekCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .build();
        return CircuitBreaker.of("deepseek", config);
    }

    @Bean
    public RateLimiter agentGlobalRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
        return RateLimiter.of("agent-global", config);
    }
}
