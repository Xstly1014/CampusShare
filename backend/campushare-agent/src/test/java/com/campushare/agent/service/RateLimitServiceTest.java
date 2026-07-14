package com.campushare.agent.service;

import com.campushare.agent.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RateLimitService 单元测试")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.keys("agent:rate:*").flatMap(redisTemplate::delete).blockLast();
    }

    @Nested
    @DisplayName("单键限流")
    class SingleRateLimit {

        @Test
        @DisplayName("未超过阈值允许访问")
        void checkSingleRateLimit_allowed() {
            Mono<Boolean> result = rateLimitService.checkSingleRateLimit("test-user-1", 10, 60);

            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("超过阈值拒绝访问")
        void checkSingleRateLimit_exceeded() {
            Flux.range(1, 11)
                    .flatMap(i -> rateLimitService.checkSingleRateLimit("test-user-exceed", 10, 60))
                    .blockLast();

            Mono<Boolean> result = rateLimitService.checkSingleRateLimit("test-user-exceed", 10, 60);

            StepVerifier.create(result)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("多键限流")
    class MultiRateLimit {

        @Test
        @DisplayName("所有键都未超过阈值")
        void checkMultiRateLimit_allAllowed() {
            List<String> keys = List.of("global", "user-1", "ip-192.168.1.1");
            List<Integer> maxRequests = List.of(100, 10, 50);

            Mono<RateLimitResult> result = rateLimitService.checkMultiRateLimit(keys, maxRequests, 60);

            StepVerifier.create(result)
                    .expectNextMatches(RateLimitResult::isAllowed)
                    .verifyComplete();
        }

        @Test
        @DisplayName("其中一个键超过阈值")
        void checkMultiRateLimit_oneExceeded() {
            List<String> keys = List.of("global", "user-1", "ip-192.168.1.1");
            List<Integer> maxRequests = List.of(100, 10, 50);

            Flux.range(1, 11)
                    .flatMap(i -> rateLimitService.checkMultiRateLimit(
                            List.of("user-1"), List.of(10), 60))
                    .blockLast();

            Mono<RateLimitResult> result = rateLimitService.checkMultiRateLimit(keys, maxRequests, 60);

            StepVerifier.create(result)
                    .expectNextMatches(r -> !r.isAllowed() && r.getExceededKey().contains("user-1"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("限流重置")
    class ResetRateLimit {

        @Test
        @DisplayName("重置后可重新访问")
        void resetRateLimit_success() {
            Flux.range(1, 11)
                    .flatMap(i -> rateLimitService.checkSingleRateLimit("test-reset", 10, 60))
                    .blockLast();

            Mono<Boolean> beforeReset = rateLimitService.checkSingleRateLimit("test-reset", 10, 60);
            StepVerifier.create(beforeReset).expectNext(false).verifyComplete();

            rateLimitService.resetRateLimit("test-reset").block();

            Mono<Boolean> afterReset = rateLimitService.checkSingleRateLimit("test-reset", 10, 60);
            StepVerifier.create(afterReset).expectNext(true).verifyComplete();
        }
    }
}
