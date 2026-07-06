package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 意图分类 Redis 缓存（ADR-014）。
 *
 * 相同 query 不重复调 LLM，预计 15% 缓存命中率。
 *
 * key 格式：agent:intent:{md5(query.trim().toLowerCase())}
 * TTL：1 小时（短期记忆，避免长期缓存过时意图）
 *
 * 降级策略：Redis 故障时返回 Mono.empty()，不阻塞主流程（IntentClassifier 会继续调 LLM）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntentCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "agent:intent:";
    private static final Duration TTL = Duration.ofHours(1);

    /**
     * 查询缓存。
     *
     * @param query 用户原始查询
     * @return Mono<IntentResult>，命中时返回结果，未命中/故障返回 Mono.empty()
     */
    public Mono<IntentResult> get(String query) {
        if (query == null || query.isBlank()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    String key = buildKey(query);
                    String json = redis.opsForValue().get(key);
                    if (json == null) {
                        return null;
                    }
                    return objectMapper.readValue(json, IntentResult.class);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("Intent cache get failed, degrading: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 写入缓存（异步，不阻塞主流程）。
     *
     * @param query  用户原始查询
     * @param result 分类结果
     * @return Mono<Void>
     */
    public Mono<Void> put(String query, IntentResult result) {
        if (query == null || query.isBlank() || result == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                    try {
                        String key = buildKey(query);
                        String json = objectMapper.writeValueAsString(result);
                        redis.opsForValue().set(key, json, TTL);
                    } catch (Exception e) {
                        log.warn("Intent cache put failed: {}", e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 生成缓存 key：agent:intent:{md5(normalized_query)}。
     *
     * 归一化：trim + toLowerCase，确保 "求卷子 " 和 "求卷子" 命中同一 key。
     */
    private String buildKey(String query) {
        String normalized = query.trim().toLowerCase();
        String md5 = DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + md5;
    }
}
