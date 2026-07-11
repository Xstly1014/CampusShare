package com.campushare.agent.config;

import com.campushare.agent.service.KnowledgeIngestionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeScheduler {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @Value("${app.knowledge.scheduler.startup-delay-ms:30000}")
    private long startupDelayMs;

    @Value("${app.knowledge.scheduler.enabled:true}")
    private boolean enabled;

    private Disposable startupSubscription;

    @PostConstruct
    public void onStartup() {
        if (!enabled) {
            log.info("Knowledge scheduler is disabled");
            return;
        }

        startupSubscription = Mono.delay(Duration.ofMillis(startupDelayMs))
                .flatMap(tick -> Mono.fromRunnable(() -> {
                    log.info("Startup knowledge ingestion triggered");
                    try {
                        knowledgeIngestionService.ingestAll();
                    } catch (Exception e) {
                        log.error("Startup knowledge ingestion failed", e);
                    }
                }).subscribeOn(Schedulers.boundedElastic()))
                .subscribe();
    }

    @PreDestroy
    public void onShutdown() {
        if (startupSubscription != null && !startupSubscription.isDisposed()) {
            startupSubscription.dispose();
        }
    }

    @Scheduled(fixedDelayString = "${app.knowledge.scheduler.fixed-delay-ms:3600000}")
    public void scheduledIngestion() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Scheduled knowledge ingestion triggered");
            knowledgeIngestionService.ingestAll();
        } catch (Exception e) {
            log.error("Scheduled knowledge ingestion failed", e);
        }
    }
}
