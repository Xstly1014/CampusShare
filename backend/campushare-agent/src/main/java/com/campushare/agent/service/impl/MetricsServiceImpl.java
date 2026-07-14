package com.campushare.agent.service.impl;

import com.campushare.agent.service.MetricsService;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final MeterRegistry meterRegistry;

    private Timer chatLatencyTimer;
    private Timer intentLatencyTimer;
    private Timer ragLatencyTimer;
    private Timer llmLatencyTimer;
    private final ConcurrentHashMap<String, Timer> toolLatencyTimers = new ConcurrentHashMap<>();

    private Counter promptTokensCounter;
    private Counter completionTokensCounter;

    private DistributionSummary costSummary;
    private DistributionSummary userCostSummary;

    private Counter chatErrorCounter;
    private Counter intentErrorCounter;

    private final ConcurrentHashMap<String, Counter> toolSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> toolErrorCounters = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Counter> cacheHitCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> cacheMissCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> cacheEvictionCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void initMetrics() {
        chatLatencyTimer = Timer.builder("agent.latency.chat")
                .description("Chat request total latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        intentLatencyTimer = Timer.builder("agent.latency.intent")
                .description("Intent recognition latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        ragLatencyTimer = Timer.builder("agent.latency.rag")
                .description("RAG retrieval latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        llmLatencyTimer = Timer.builder("agent.latency.llm")
                .description("LLM API call latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        promptTokensCounter = Counter.builder("agent.tokens.prompt")
                .description("Total prompt tokens sent to LLM")
                .register(meterRegistry);

        completionTokensCounter = Counter.builder("agent.tokens.completion")
                .description("Total completion tokens received from LLM")
                .register(meterRegistry);

        costSummary = DistributionSummary.builder("agent.cost.total")
                .description("Total LLM API cost")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        userCostSummary = DistributionSummary.builder("agent.cost.user")
                .description("Per-user LLM API cost")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        chatErrorCounter = Counter.builder("agent.errors.chat")
                .description("Total chat errors")
                .register(meterRegistry);

        intentErrorCounter = Counter.builder("agent.errors.intent")
                .description("Total intent recognition errors")
                .register(meterRegistry);

        cacheHitCounters.put("intent", Counter.builder("agent.cache.hit")
                .tag("cache", "intent")
                .description("Intent cache hits")
                .register(meterRegistry));
        cacheMissCounters.put("intent", Counter.builder("agent.cache.miss")
                .tag("cache", "intent")
                .description("Intent cache misses")
                .register(meterRegistry));
        cacheEvictionCounters.put("intent", Counter.builder("agent.cache.eviction")
                .tag("cache", "intent")
                .description("Intent cache evictions")
                .register(meterRegistry));

        cacheHitCounters.put("semantic", Counter.builder("agent.cache.hit")
                .tag("cache", "semantic")
                .description("Semantic cache hits")
                .register(meterRegistry));
        cacheMissCounters.put("semantic", Counter.builder("agent.cache.miss")
                .tag("cache", "semantic")
                .description("Semantic cache misses")
                .register(meterRegistry));
        cacheEvictionCounters.put("semantic", Counter.builder("agent.cache.eviction")
                .tag("cache", "semantic")
                .description("Semantic cache evictions")
                .register(meterRegistry));

        cacheHitCounters.put("embedding", Counter.builder("agent.cache.hit")
                .tag("cache", "embedding")
                .description("Embedding cache hits")
                .register(meterRegistry));
        cacheMissCounters.put("embedding", Counter.builder("agent.cache.miss")
                .tag("cache", "embedding")
                .description("Embedding cache misses")
                .register(meterRegistry));
        cacheEvictionCounters.put("embedding", Counter.builder("agent.cache.eviction")
                .tag("cache", "embedding")
                .description("Embedding cache evictions")
                .register(meterRegistry));

        log.info("All metrics pre-initialized: 14 core metrics registered");
    }

    @Override
    public void recordChatLatency(long latencyMs) {
        chatLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordIntentLatency(long latencyMs) {
        intentLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordRagLatency(long latencyMs) {
        ragLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordLlmLatency(long latencyMs) {
        llmLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordToolLatency(String toolName, long latencyMs) {
        String name = toolName != null ? toolName : "unknown";
        Timer timer = toolLatencyTimers.computeIfAbsent(name, n ->
                Timer.builder("agent.latency.tool")
                        .tag("tool", n)
                        .description("Tool call latency")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
        timer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordPromptTokens(int tokens) {
        promptTokensCounter.increment(tokens);
    }

    @Override
    public void recordCompletionTokens(int tokens) {
        completionTokensCounter.increment(tokens);
    }

    @Override
    public void recordCost(double cost) {
        costSummary.record(cost);
    }

    @Override
    public void recordUserCost(String userId, double cost) {
        userCostSummary.record(cost);
    }

    @Override
    public void recordChatError() {
        chatErrorCounter.increment();
    }

    @Override
    public void recordIntentError() {
        intentErrorCounter.increment();
    }

    @Override
    public void recordToolCall(String toolName, boolean success) {
        String name = toolName != null ? toolName : "unknown";
        if (success) {
            toolSuccessCounters.computeIfAbsent(name, n ->
                    Counter.builder("agent.tool.calls.success")
                            .tag("tool", n)
                            .description("Successful tool calls")
                            .register(meterRegistry)).increment();
        } else {
            toolErrorCounters.computeIfAbsent(name, n ->
                    Counter.builder("agent.tool.calls.error")
                            .tag("tool", n)
                            .description("Failed tool calls")
                            .register(meterRegistry)).increment();
        }
    }

    @Override
    public void recordCacheHit(String cacheName) {
        String name = cacheName != null ? cacheName : "unknown";
        cacheHitCounters.computeIfAbsent(name, n ->
                Counter.builder("agent.cache.hit")
                        .tag("cache", n)
                        .description("Cache hits")
                        .register(meterRegistry)).increment();
    }

    @Override
    public void recordCacheMiss(String cacheName) {
        String name = cacheName != null ? cacheName : "unknown";
        cacheMissCounters.computeIfAbsent(name, n ->
                Counter.builder("agent.cache.miss")
                        .tag("cache", n)
                        .description("Cache misses")
                        .register(meterRegistry)).increment();
    }

    @Override
    public void recordCacheEviction(String cacheName) {
        String name = cacheName != null ? cacheName : "unknown";
        cacheEvictionCounters.computeIfAbsent(name, n ->
                Counter.builder("agent.cache.eviction")
                        .tag("cache", n)
                        .description("Cache evictions")
                        .register(meterRegistry)).increment();
    }

    @Override
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    @Override
    public void stopTimer(Timer.Sample sample, String metricName) {
        if (sample == null) return;
        Timer timer = meterRegistry.find(metricName).timer();
        if (timer != null) {
            sample.stop(timer);
        }
    }
}