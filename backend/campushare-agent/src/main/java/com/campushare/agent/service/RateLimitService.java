package com.campushare.agent.service;

import com.campushare.agent.dto.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
        local function checkSlidingWindow(key, now, windowMs, max)
            local windowStart = now - (now % windowMs)
            local windowKey = key .. ":" .. windowStart
            redis.call("INCR", windowKey)
            redis.call("EXPIRE", windowKey, windowMs / 1000 + 1)
            local current = tonumber(redis.call("GET", windowKey))
            return current > max, current
        end
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        for i = 1, #KEYS do
            local key = KEYS[i]
            local max = tonumber(ARGV[i + 2])
            local exceeded, current = checkSlidingWindow(key, now, windowMs, max)
            if exceeded then
                return {1, key, current, max}
            end
        end
        return {0, "", 0, 0}
        """;

    private static final String KEY_PREFIX = "agent:rate:";

    public Mono<RateLimitResult> checkMultiRateLimit(List<String> keys, List<Integer> maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        List<String> redisKeys = keys.stream()
                .map(k -> KEY_PREFIX + k)
                .collect(Collectors.toList());

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(now));
        args.add(String.valueOf(windowMs));
        maxRequests.forEach(m -> args.add(String.valueOf(m)));

        return redisTemplate.execute(new DefaultRedisScript<>(LUA_SCRIPT, List.class), redisKeys, args)
                .next()
                .map(result -> {
                    List<?> list = (List<?>) result;
                    if (list.size() >= 4 && ((Number) list.get(0)).intValue() == 1) {
                        return RateLimitResult.exceeded(
                                String.valueOf(list.get(1)),
                                ((Number) list.get(2)).intValue(),
                                ((Number) list.get(3)).intValue()
                        );
                    }
                    return RateLimitResult.allowed();
                })
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed, allowing request", e);
                    return Mono.just(RateLimitResult.allowed());
                });
    }

    public Mono<Boolean> checkSingleRateLimit(String key, int maxRequests, int windowSeconds) {
        return checkMultiRateLimit(List.of(key), List.of(maxRequests), windowSeconds)
                .map(RateLimitResult::isAllowed);
    }

    public Mono<Void> resetRateLimit(String key) {
        return redisTemplate.keys(KEY_PREFIX + key + ":*")
                .flatMap(redisTemplate::delete)
                .then();
    }
}