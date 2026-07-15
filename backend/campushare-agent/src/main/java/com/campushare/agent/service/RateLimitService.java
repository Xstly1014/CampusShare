package com.campushare.agent.service;

import com.campushare.agent.dto.RateLimitConfig;
import com.campushare.agent.dto.RateLimitResult;
import com.campushare.agent.dto.RateLimitStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String CONFIG_PREFIX = "agent:rate:config:";

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

    // Get a single config from Redis Hash
    public Mono<RateLimitConfig> getConfig(String key) {
        return redisTemplate.opsForHash().entries(CONFIG_PREFIX + key)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(map -> {
                    if (map.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.just(mapToConfig(key, map));
                });
    }

    // Get all configs by scanning for keys with the prefix
    public Mono<List<RateLimitConfig>> getAllConfigs() {
        return redisTemplate.keys(CONFIG_PREFIX + "*")
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(List.of());
                    }
                    return Flux.fromIterable(keys)
                            .flatMap(key -> {
                                String configKey = key.substring(CONFIG_PREFIX.length());
                                return getConfig(configKey);
                            })
                            .collectList();
                });
    }

    // Save a config to Redis Hash
    public Mono<RateLimitConfig> saveConfig(RateLimitConfig config) {
        Map<String, String> map = new HashMap<>();
        map.put("maxRequests", String.valueOf(config.getMaxRequests()));
        map.put("windowSeconds", String.valueOf(config.getWindowSeconds()));
        map.put("strategy", config.getStrategy() != null ? config.getStrategy() : "SLIDING_WINDOW");
        map.put("burstCapacity", String.valueOf(config.getBurstCapacity()));
        map.put("refillTokens", String.valueOf(config.getRefillTokens()));
        map.put("enabled", String.valueOf(config.isEnabled()));
        map.put("description", config.getDescription() != null ? config.getDescription() : "");
        map.put("createdAt", config.getCreatedAt() != null ? config.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
        map.put("updatedAt", java.time.LocalDateTime.now().toString());

        return redisTemplate.opsForHash().putAll(CONFIG_PREFIX + config.getKey(), map)
                .thenReturn(config);
    }

    // Delete a config
    public Mono<Boolean> deleteConfig(String key) {
        return redisTemplate.delete(CONFIG_PREFIX + key)
                .map(count -> count > 0);
    }

    // Get the current count for a rate limit key by scanning Redis
    public Mono<RateLimitStatus> getStatus(String key) {
        return redisTemplate.keys(KEY_PREFIX + key + ":*")
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(RateLimitStatus.builder()
                                .key(key)
                                .current(0)
                                .max(0)
                                .remaining(0)
                                .resetTime(System.currentTimeMillis() + 60000)
                                .strategy("SLIDING_WINDOW")
                                .build());
                    }
                    // Get the count from the first key
                    return redisTemplate.opsForValue().get(keys.get(0))
                            .map(val -> {
                                int current = Integer.parseInt(val);
                                return RateLimitStatus.builder()
                                        .key(key)
                                        .current(current)
                                        .max(0)
                                        .remaining(Math.max(0, 0 - current))
                                        .resetTime(System.currentTimeMillis() + 60000)
                                        .strategy("SLIDING_WINDOW")
                                        .build();
                            })
                            .defaultIfEmpty(RateLimitStatus.builder()
                                    .key(key)
                                    .current(0)
                                    .max(0)
                                    .remaining(0)
                                    .resetTime(System.currentTimeMillis() + 60000)
                                    .strategy("SLIDING_WINDOW")
                                    .build());
                });
    }

    private RateLimitConfig mapToConfig(String key, Map<Object, Object> map) {
        return RateLimitConfig.builder()
                .key(key)
                .maxRequests(parseIntSafe(map.get("maxRequests"), 0))
                .windowSeconds(parseIntSafe(map.get("windowSeconds"), 60))
                .strategy(map.get("strategy") != null ? map.get("strategy").toString() : "SLIDING_WINDOW")
                .burstCapacity(parseLongSafe(map.get("burstCapacity"), 0))
                .refillTokens(parseLongSafe(map.get("refillTokens"), 0))
                .enabled(parseBooleanSafe(map.get("enabled"), true))
                .description(map.get("description") != null ? map.get("description").toString() : "")
                .createdAt(parseDateTimeSafe(map.get("createdAt")))
                .updatedAt(parseDateTimeSafe(map.get("updatedAt")))
                .build();
    }

    private int parseIntSafe(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private long parseLongSafe(Object val, long defaultVal) {
        if (val == null) return defaultVal;
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private boolean parseBooleanSafe(Object val, boolean defaultVal) {
        if (val == null) return defaultVal;
        return Boolean.parseBoolean(val.toString());
    }

    private java.time.LocalDateTime parseDateTimeSafe(Object val) {
        if (val == null) return null;
        try { return java.time.LocalDateTime.parse(val.toString()); } catch (Exception e) { return null; }
    }
}