package com.campushare.agent.config;

import com.campushare.agent.enums.Intent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 意图识别监控指标。
 *
 * 4 个指标：
 *  1. agent.intent.classification.total{intent, sub_intent, layer, result} — 分类计数
 *  2. agent.intent.classification.duration{layer} — 分类耗时
 *  3. agent.intent.cache.total{result=HIT|MISS} — 缓存命中率
 *  4. agent.intent.route.total{path=SHORT_CIRCUIT|RAG, intent} — 路由决策计数
 */
@Component
@RequiredArgsConstructor
public class IntentMetricsConfig {

    private final MeterRegistry registry;

    /**
     * 记录意图分类结果。
     *
     * @param intent    L1 意图
     * @param subIntent L2 子意图
     * @param layer     分类层级：RULE / LLM / EMBEDDING / DEFAULT
     * @param result    结果：SUCCESS / FALLBACK / ERROR
     */
    public void recordClassification(Intent intent, String subIntent,
                                      String layer, String result) {
        Counter.builder("agent.intent.classification.total")
                .tag("intent", intent != null ? intent.name() : "unknown")
                .tag("sub_intent", subIntent != null ? subIntent : "unknown")
                .tag("layer", layer != null ? layer : "unknown")
                .tag("result", result != null ? result : "unknown")
                .register(registry)
                .increment();
    }

    /**
     * 开始计时。
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * 记录分类耗时。
     *
     * @param sample startTimer() 返回的样本
     * @param layer  分类层级
     */
    public void recordDuration(Timer.Sample sample, String layer) {
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder("agent.intent.classification.duration")
                .tag("layer", layer != null ? layer : "unknown")
                .register(registry));
    }

    /**
     * 记录缓存命中/未命中。
     */
    public void recordCacheHit(boolean hit) {
        Counter.builder("agent.intent.cache.total")
                .tag("result", hit ? "HIT" : "MISS")
                .register(registry)
                .increment();
    }

    /**
     * 记录路由决策。
     *
     * @param shortCircuit true=快路径（模板回复），false=慢路径（RAG）
     * @param intent       意图名称
     */
    public void recordRoute(boolean shortCircuit, String intent) {
        Counter.builder("agent.intent.route.total")
                .tag("path", shortCircuit ? "SHORT_CIRCUIT" : "RAG")
                .tag("intent", intent != null ? intent : "unknown")
                .register(registry)
                .increment();
    }
}
