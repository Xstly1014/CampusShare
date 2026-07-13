package com.campushare.agent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final MeterRegistry meterRegistry;

    @Bean(name = "intentCache")
    public Cache<String, Object> intentCache() {
        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "intent");
        return cache;
    }

    @Bean(name = "retrievalCache")
    public Cache<String, Object> retrievalCache() {
        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "retrieval");
        return cache;
    }

    @Bean(name = "semanticCache")
    public Cache<String, Object> semanticCache() {
        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "semantic");
        return cache;
    }

    @Bean(name = "embeddingCache")
    public Cache<String, float[]> embeddingCache() {
        Cache<String, float[]> cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .expireAfterAccess(12, TimeUnit.HOURS)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "embedding");
        return cache;
    }

    @Bean(name = "promptVersionCache")
    public Cache<String, String> promptVersionCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "promptVersion");
        return cache;
    }
}