package com.campushare.agent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class KnowledgeMetricsConfig {

    private final MeterRegistry registry;

    private final AtomicInteger lastEmbeddingBatchSize = new AtomicInteger(0);

    private Counter ingestSuccessCounter;
    private Counter ingestFailCounter;
    private Counter ingestSkippedCounter;
    private Counter ingestDuplicatedCounter;
    private Timer ingestTotalTimer;
    private DistributionSummary chunksPerDocSummary;
    private Counter recallCounter;
    private final ConcurrentHashMap<String, Counter> duplicateCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ingestSuccessCounter = Counter.builder("agent.knowledge.ingest.total")
                .tag("result", "SUCCESS")
                .description("Total knowledge ingestion success count")
                .register(registry);
        ingestFailCounter = Counter.builder("agent.knowledge.ingest.total")
                .tag("result", "FAIL")
                .description("Total knowledge ingestion failure count")
                .register(registry);
        ingestSkippedCounter = Counter.builder("agent.knowledge.ingest.total")
                .tag("result", "SKIPPED")
                .description("Total knowledge ingestion skipped count")
                .register(registry);
        ingestDuplicatedCounter = Counter.builder("agent.knowledge.ingest.total")
                .tag("result", "DUPLICATED")
                .description("Total knowledge ingestion duplicated count")
                .register(registry);

        ingestTotalTimer = Timer.builder("agent.knowledge.ingest.duration")
                .tag("phase", "TOTAL")
                .description("Knowledge ingestion total duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        chunksPerDocSummary = DistributionSummary.builder("agent.knowledge.chunks.perDoc")
                .description("Chunks per document distribution")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        recallCounter = Counter.builder("agent.knowledge.retrieval.recallCount")
                .description("Knowledge retrieval recall count")
                .register(registry);

        Gauge.builder("agent.knowledge.embedding.batchSize", lastEmbeddingBatchSize, AtomicInteger::doubleValue)
                .description("Last embedding batch size")
                .register(registry);
    }

    public void recordIngest(String result) {
        if ("SUCCESS".equals(result)) {
            ingestSuccessCounter.increment();
        } else if ("FAIL".equals(result)) {
            ingestFailCounter.increment();
        } else if ("SKIPPED".equals(result)) {
            ingestSkippedCounter.increment();
        } else if ("DUPLICATED".equals(result)) {
            ingestDuplicatedCounter.increment();
        }
    }

    public Timer.Sample startIngestTimer() {
        return Timer.start(registry);
    }

    public void recordIngestDuration(Timer.Sample sample, String phase) {
        if (sample == null) {
            return;
        }
        if ("TOTAL".equals(phase)) {
            sample.stop(ingestTotalTimer);
        } else {
            Timer timer = Timer.builder("agent.knowledge.ingest.duration")
                    .tag("phase", phase != null ? phase : "unknown")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);
            sample.stop(timer);
        }
    }

    public void recordChunksPerDoc(int count) {
        chunksPerDocSummary.record(count);
    }

    public void recordEmbeddingBatchSize(int size) {
        lastEmbeddingBatchSize.set(size);
    }

    public void recordRecall() {
        recallCounter.increment();
    }

    public void recordDuplicate(String level) {
        String tag = level != null ? level : "unknown";
        duplicateCounters.computeIfAbsent(tag, k ->
                Counter.builder("agent.knowledge.duplicate.detected")
                        .tag("level", k)
                        .description("Duplicate detection result count")
                        .register(registry)
        ).increment();
    }
}
