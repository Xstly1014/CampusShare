package com.campushare.agent.cache;

import com.campushare.agent.llm.EmbeddingClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final StringRedisTemplate redisTemplate;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    private static final String SEMANTIC_CACHE_PREFIX = "agent:semantic:";
    private static final String EMBEDDING_CACHE_PREFIX = "agent:embedding:";
    private static final Duration SEMANTIC_CACHE_TTL = Duration.ofHours(24);
    private static final Duration EMBEDDING_CACHE_TTL = Duration.ofDays(7);

    private static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.85;

    public Mono<CachedResult> getSemanticCache(String query) {
        return Mono.fromCallable(() -> {
            Set<String> keys = redisTemplate.keys(SEMANTIC_CACHE_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
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
                    String cacheKey = key.substring(SEMANTIC_CACHE_PREFIX.length());
                    String embeddingKey = EMBEDDING_CACHE_PREFIX + cacheKey;
                    String embeddingJson = redisTemplate.opsForValue().get(embeddingKey);
                    if (embeddingJson == null) continue;

                    float[] cachedEmbedding = parseEmbedding(embeddingJson);
                    if (cachedEmbedding == null) continue;

                    double similarity = cosineSimilarity(queryEmbedding, cachedEmbedding);
                    if (similarity > highestSimilarity && similarity >= SEMANTIC_SIMILARITY_THRESHOLD) {
                        highestSimilarity = similarity;
                        bestKey = key;
                    }
                } catch (Exception e) {
                    log.debug("Failed to check semantic cache entry: {}", key);
                }
            }

            if (bestKey != null) {
                String valueJson = redisTemplate.opsForValue().get(bestKey);
                if (valueJson != null) {
                    try {
                        bestValue = objectMapper.readValue(valueJson, Object.class);
                    } catch (JsonProcessingException e) {
                        bestValue = valueJson;
                    }
                }
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

            String resultJson;
            try {
                resultJson = objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                resultJson = String.valueOf(result);
            }
            redisTemplate.opsForValue().set(fullKey, resultJson, SEMANTIC_CACHE_TTL);

            float[] embedding = embeddingClient.embed(query).block();
            if (embedding != null && embedding.length > 0) {
                String embeddingKey = EMBEDDING_CACHE_PREFIX + cacheKey;
                String embeddingJson = Arrays.toString(embedding);
                redisTemplate.opsForValue().set(embeddingKey, embeddingJson, EMBEDDING_CACHE_TTL);
            }

            log.debug("Semantic cache stored: key={}", cacheKey);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<float[]> getEmbeddingCache(String text) {
        return Mono.fromCallable(() -> {
            String key = EMBEDDING_CACHE_PREFIX + generateCacheKey(text);
            String embeddingJson = redisTemplate.opsForValue().get(key);
            return parseEmbedding(embeddingJson);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> putEmbeddingCache(String text, float[] embedding) {
        return Mono.fromCallable(() -> {
            String key = EMBEDDING_CACHE_PREFIX + generateCacheKey(text);
            String embeddingJson = Arrays.toString(embedding);
            redisTemplate.opsForValue().set(key, embeddingJson, EMBEDDING_CACHE_TTL);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String generateCacheKey(String text) {
        return String.format("%08x", text.hashCode());
    }

    private float[] parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isEmpty()) {
            return null;
        }
        try {
            String trimmed = embeddingJson.trim().replace("[", "").replace("]", "");
            String[] parts = trimmed.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            return null;
        }
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
