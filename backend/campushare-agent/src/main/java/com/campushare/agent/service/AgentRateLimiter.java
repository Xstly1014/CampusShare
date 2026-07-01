package com.campushare.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "agent:rate_limit:";

    public Mono<Boolean> checkRateLimit(String userId) {
        String key = KEY_PREFIX + userId;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, WINDOW).thenReturn(true);
                    }
                    return Mono.just(count <= MAX_REQUESTS_PER_MINUTE);
                })
                .defaultIfEmpty(true)
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed for user {}, allowing request", userId, e);
                    return Mono.just(true);
                });
    }
}
