package com.campushare.agent.config;

import com.campushare.agent.service.PostVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 帖子向量定时同步调度器。
 *
 * - 启动后 60 秒首次全量同步（等待其他服务就绪）
 * - 每 5 分钟全量同步一次（兜底机制，保证向量库一致性）
 *
 * 即使 post-service 通知丢失，本调度器也能定期补齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostVectorScheduler {

    private final PostVectorService postVectorService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        new Thread(() -> {
            try {
                Thread.sleep(60000);
                log.info("Startup post vector full sync triggered");
                postVectorService.syncAll().block();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Startup post vector sync failed", e);
            }
        }, "post-vector-startup-sync").start();
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 300000)
    public void scheduledSync() {
        try {
            log.info("Scheduled post vector sync triggered");
            postVectorService.syncAll().block();
        } catch (Exception e) {
            log.error("Scheduled post vector sync failed", e);
        }
    }
}
