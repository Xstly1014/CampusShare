package com.campushare.agent.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.campushare.agent.cache.SemanticCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
public class CacheInvalidationService {

    private final StringRedisTemplate redisTemplate;
    private final SemanticCacheService semanticCacheService;
    private final Cache<String, Object> semanticCache;
    private final Cache<String, float[]> embeddingCache;
    private final Cache<String, Object> intentCache;

    public CacheInvalidationService(StringRedisTemplate redisTemplate,
                                    SemanticCacheService semanticCacheService,
                                    @Qualifier("semanticCache") Cache<String, Object> semanticCache,
                                    @Qualifier("embeddingCache") Cache<String, float[]> embeddingCache,
                                    @Qualifier("intentCache") Cache<String, Object> intentCache) {
        this.redisTemplate = redisTemplate;
        this.semanticCacheService = semanticCacheService;
        this.semanticCache = semanticCache;
        this.embeddingCache = embeddingCache;
        this.intentCache = intentCache;
    }

    private static final String SEMANTIC_CACHE_PREFIX = "agent:semantic:";
    private static final String EMBEDDING_CACHE_PREFIX = "agent:embedding:";
    private static final String INTENT_CACHE_PREFIX = "agent:intent:";

    public void onKnowledgeUpdated(String articleId) {
        log.info("Cache invalidation triggered: knowledge article updated, id={}", articleId);
        invalidateSemanticCache();
    }

    public void onKnowledgeDeleted(String articleId) {
        log.info("Cache invalidation triggered: knowledge article deleted, id={}", articleId);
        invalidateSemanticCache();
    }

    public void onKnowledgeBatchUpdated() {
        log.info("Cache invalidation triggered: knowledge batch update");
        invalidateSemanticCache();
    }

    public void onPostUpdated(Long postId) {
        log.info("Cache invalidation triggered: post updated, id={}", postId);
        invalidateSemanticCache();
    }

    public void onPostDeleted(Long postId) {
        log.info("Cache invalidation triggered: post deleted, id={}", postId);
        invalidateSemanticCache();
    }

    public void onMemoryUpdated(String userId) {
        log.info("Cache invalidation triggered: user memory updated, userId={}", userId);
        invalidateSemanticCache();
    }

    public void invalidateSemanticCache() {
        Mono.fromRunnable(() -> {
            semanticCache.invalidateAll();
            embeddingCache.invalidateAll();
            log.debug("Local semantic and embedding cache invalidated");
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        Mono.fromRunnable(() -> {
            try {
                Set<String> keys = redisTemplate.keys(SEMANTIC_CACHE_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.debug("Redis semantic cache invalidated: {} keys", keys.size());
                }
                Set<String> embedKeys = redisTemplate.keys(EMBEDDING_CACHE_PREFIX + "*");
                if (embedKeys != null && !embedKeys.isEmpty()) {
                    redisTemplate.delete(embedKeys);
                    log.debug("Redis embedding cache invalidated: {} keys", embedKeys.size());
                }
            } catch (Exception e) {
                log.warn("Failed to invalidate Redis semantic cache", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public void invalidateIntentCache() {
        Mono.fromRunnable(() -> {
            intentCache.invalidateAll();
            log.debug("Local intent cache invalidated");
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        Mono.fromRunnable(() -> {
            try {
                Set<String> keys = redisTemplate.keys(INTENT_CACHE_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.debug("Redis intent cache invalidated: {} keys", keys.size());
                }
            } catch (Exception e) {
                log.warn("Failed to invalidate Redis intent cache", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public void invalidateAllCaches() {
        log.info("Cache invalidation triggered: ALL caches");
        invalidateSemanticCache();
        invalidateIntentCache();
    }

    public void schedulePeriodicInvalidation() {
        log.info("Scheduled periodic cache invalidation at {}", LocalDateTime.now());
        invalidateSemanticCache();
    }
}