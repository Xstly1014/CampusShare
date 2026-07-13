package com.campushare.agent.cache;

import com.campushare.agent.llm.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EmbeddingClient embeddingClient;

    private static final String SEMANTIC_CACHE_PREFIX = "agent:semantic:";
    private static final String EMBEDDING_CACHE_PREFIX = "agent:embedding:";
    private static final Duration SEMANTIC_CACHE_TTL = Duration.ofHours(24);
    private static final Duration EMBEDDING_CACHE_TTL = Duration.ofDays(7);

    private static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.85;

    public Mono<CachedResult> getSemanticCache(String query) {
        return Mono.fromCallable(() -> {
            String[] keys = getAllSemanticCacheKeys();
            if (keys.length == 0) {
                return null;
            }

            float[] queryEmbedding = embeddingClient.embed(query).block();
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return null;
            }

            double highestSimilarity = 0;
            String bestKey = null;
            Object bestValue = null;

            for (String key : keys) {
                try {
                    String embeddingKey = EMBEDDING_CACHE_PREFIX + key;
                    float[] cachedEmbedding = (float[]) redisTemplate.opsForValue().get(embeddingKey);
                    if (cachedEmbedding == null) continue;

                    double similarity = cosineSimilarity(queryEmbedding, cachedEmbedding);
                    if (similarity > highestSimilarity && similarity >= SEMANTIC_SIMILARITY_THRESHOLD) {
                        highestSimilarity = similarity;
                        bestKey = SEMANTIC_CACHE_PREFIX + key;
                    }
                } catch (Exception e) {
                    log.debug("Failed to check semantic cache entry: {}", key);
                }
            }

            if (bestKey != null) {
                bestValue = redisTemplate.opsForValue().get(bestKey);
                log.debug("Semantic cache hit: similarity={}", highestSimilarity);
                return new CachedResult(bestValue, highestSimilarity);
            }

            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> putSemanticCache(String query, Object result) {
        return Mono.fromCallable(() -> {
            String cacheKey = generateCacheKey(query);
            String fullKey = SEMANTIC_CACHE_PREFIX + cacheKey;

            redisTemplate.opsForValue().set(fullKey, result, SEMANTIC_CACHE_TTL);

            float[] embedding = embeddingClient.embed(query).block();
            if (embedding != null && embedding.length > 0) {
                String embeddingKey = EMBEDDING_CACHE_PREFIX + cacheKey;
                redisTemplate.opsForValue().set(embeddingKey, embedding, EMBEDDING_CACHE_TTL);
            }

            log.debug("Semantic cache stored: key={}", cacheKey);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<float[]> getEmbeddingCache(String text) {
        return Mono.fromCallable(() -> {
            String key = EMBEDDING_CACHE_PREFIX + generateCacheKey(text);
            return (float[]) redisTemplate.opsForValue().get(key);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> putEmbeddingCache(String text, float[] embedding) {
        return Mono.fromCallable(() -> {
            String key = EMBEDDING_CACHE_PREFIX + generateCacheKey(text);
            redisTemplate.opsForValue().set(key, embedding, EMBEDDING_CACHE_TTL);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String generateCacheKey(String text) {
        return String.format("%08x", text.hashCode());
    }

    private String[] getAllSemanticCacheKeys() {
        Set<String> keys = redisTemplate.keys(SEMANTIC_CACHE_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return new String[0];
        }
        return keys.stream()
                .map(k -> k.substring(SEMANTIC_CACHE_PREFIX.length()))
                .toArray(String[]::new);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @lombok.Data
    public static class CachedResult {
        private Object result;
        private double similarity;

        public CachedResult(Object result, double similarity) {
            this.result = result;
            this.similarity = similarity;
        }
    }
}
