package com.campushare.agent.config;

import com.campushare.agent.enums.Intent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class IntentMetricsConfig {

    private final MeterRegistry registry;

    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private final ConcurrentHashMap<String, Counter> classificationCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> classificationTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> routeCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        cacheHitCounter = Counter.builder("agent.intent.cache.total")
                .tag("result", "HIT")
                .description("Intent cache hit count")
                .register(registry);
        cacheMissCounter = Counter.builder("agent.intent.cache.total")
                .tag("result", "MISS")
                .description("Intent cache miss count")
                .register(registry);
    }

    public void recordClassification(Intent intent, String subIntent,
                                      String layer, String result) {
        String intentName = intent != null ? intent.name() : "unknown";
        String sub = subIntent != null ? subIntent : "unknown";
        String layerName = layer != null ? layer : "unknown";
        String resultName = result != null ? result : "unknown";

        String key = String.format("%s|%s|%s|%s", intentName, sub, layerName, resultName);
        classificationCounters.computeIfAbsent(key, k ->
                Counter.builder("agent.intent.classification.total")
                        .tag("intent", intentName)
                        .tag("sub_intent", sub)
                        .tag("layer", layerName)
                        .tag("result", resultName)
                        .description("Intent classification count")
                        .register(registry)
        ).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordDuration(Timer.Sample sample, String layer) {
        if (sample == null) {
            return;
        }
        String layerName = layer != null ? layer : "unknown";
        Timer timer = classificationTimers.computeIfAbsent(layerName, k ->
                Timer.builder("agent.intent.classification.duration")
                        .tag("layer", layerName)
                        .description("Intent classification duration")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(registry)
        );
        sample.stop(timer);
    }

    public void recordCacheHit(boolean hit) {
        if (hit) {
            cacheHitCounter.increment();
        } else {
            cacheMissCounter.increment();
        }
    }

    public void recordRoute(boolean shortCircuit, String intent) {
        String path = shortCircuit ? "SHORT_CIRCUIT" : "RAG";
        String intentName = intent != null ? intent : "unknown";
        String key = path + "|" + intentName;
        routeCounters.computeIfAbsent(key, k ->
                Counter.builder("agent.intent.route.total")
                        .tag("path", path)
                        .tag("intent", intentName)
                        .description("Intent route decision count")
                        .register(registry)
        ).increment();
    }
}
