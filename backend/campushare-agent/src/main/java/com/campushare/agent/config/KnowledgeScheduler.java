package com.campushare.agent.config;

import com.campushare.agent.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库定时摄入调度器。
 *
 * - 启动后 30 秒首次摄入（等待其他 Bean 初始化完成）
 * - 每小时检查一次文档变更（MD5 diff，未变更的跳过）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeScheduler {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        new Thread(() -> {
            try {
                Thread.sleep(30000);
                log.info("Startup knowledge ingestion triggered");
                knowledgeIngestionService.ingestAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Startup knowledge ingestion failed", e);
            }
        }, "knowledge-startup-ingestion").start();
    }

    @Scheduled(fixedDelay = 3600000)
    public void scheduledIngestion() {
        try {
            log.info("Scheduled knowledge ingestion triggered");
            knowledgeIngestionService.ingestAll();
        } catch (Exception e) {
            log.error("Scheduled knowledge ingestion failed", e);
        }
    }
}
