package com.campushare.agent.config;

import com.campushare.agent.service.PostVectorService;
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
public class PostVectorScheduler {

    private final PostVectorService postVectorService;

    @Value("${app.post-sync.scheduler.startup-delay-ms:60000}")
    private long startupDelayMs;

    @Value("${app.post-sync.scheduler.enabled:true}")
    private boolean enabled;

    private Disposable startupSubscription;

    @PostConstruct
    public void onStartup() {
        if (!enabled) {
            log.info("Post vector scheduler is disabled");
            return;
        }

        startupSubscription = Mono.delay(Duration.ofMillis(startupDelayMs))
                .flatMap(tick -> postVectorService.syncAll()
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(e -> log.error("Startup post vector sync failed", e))
                        .onErrorResume(e -> Mono.empty()))
                .subscribe(
                        result -> log.info("Startup post vector full sync completed"),
                        e -> log.error("Startup post vector sync error", e)
                );
    }

    @PreDestroy
    public void onShutdown() {
        if (startupSubscription != null && !startupSubscription.isDisposed()) {
            startupSubscription.dispose();
        }
    }

    @Scheduled(initialDelayString = "${app.post-sync.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${app.post-sync.scheduler.fixed-delay-ms:300000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Scheduled post vector sync triggered");
            postVectorService.syncAll()
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(Duration.ofMinutes(10));
        } catch (Exception e) {
            log.error("Scheduled post vector sync failed", e);
        }
    }
}
