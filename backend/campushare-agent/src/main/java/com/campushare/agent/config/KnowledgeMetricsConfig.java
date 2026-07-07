package com.campushare.agent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识库管理监控指标（6 项）。
 *
 *  1. agent.knowledge.ingest.total{result=SUCCESS|FAIL|SKIPPED|DUPLICATED} — 摄入计数（counter）
 *  2. agent.knowledge.ingest.duration{phase=TOTAL} — 摄入总耗时（timer）
 *  3. agent.knowledge.chunks.perDoc — 每文档分块数分布（histogram）
 *  4. agent.knowledge.embedding.batchSize — embedding 批大小（gauge）
 *  5. agent.knowledge.retrieval.recallCount — 召回次数（counter）
 *  6. agent.knowledge.duplicate.detected{level=DUPLICATE|SIMILAR|UNIQUE} — 重复检测分级计数（counter）
 */
@Component
@RequiredArgsConstructor
public class KnowledgeMetricsConfig {

    private final MeterRegistry registry;

    private final AtomicInteger lastEmbeddingBatchSize = new AtomicInteger(0);

    public void recordIngest(String result) {
        Counter.builder("agent.knowledge.ingest.total")
                .tag("result", result != null ? result : "unknown")
                .register(registry)
                .increment();
    }

    public Timer.Sample startIngestTimer() {
        return Timer.start(registry);
    }

    public void recordIngestDuration(Timer.Sample sample, String phase) {
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder("agent.knowledge.ingest.duration")
                .tag("phase", phase != null ? phase : "unknown")
                .register(registry));
    }

    public void recordChunksPerDoc(int count) {
        DistributionSummary.builder("agent.knowledge.chunks.perDoc")
                .register(registry)
                .record(count);
    }

    public void recordEmbeddingBatchSize(int size) {
        lastEmbeddingBatchSize.set(size);
        Gauge.builder("agent.knowledge.embedding.batchSize", lastEmbeddingBatchSize, AtomicInteger::doubleValue)
                .register(registry);
    }

    public void recordRecall() {
        Counter.builder("agent.knowledge.retrieval.recallCount")
                .register(registry)
                .increment();
    }

    public void recordDuplicate(String level) {
        Counter.builder("agent.knowledge.duplicate.detected")
                .tag("level", level != null ? level : "unknown")
                .register(registry)
                .increment();
    }
}
