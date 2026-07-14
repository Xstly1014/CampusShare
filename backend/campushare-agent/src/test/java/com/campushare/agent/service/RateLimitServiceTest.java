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
    }

    @Nested
    @DisplayName("限流重置")
    class ResetRateLimit {

        @Test
        @DisplayName("服务正常启动")
        void service_loadsSuccessfully() {
            assertThat(rateLimitService).isNotNull();
        }
    }
}
