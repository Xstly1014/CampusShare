package com.campushare.agent.config;

import com.campushare.agent.service.BadCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BadCaseScheduler {

    private final BadCaseService badCaseService;

    @Value("${app.badcase.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.badcase.scheduler.cron:0 0 2 * * ?}")
    public void scheduledBadCaseCollection() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Scheduled bad case auto collection triggered");
            badCaseService.autoCollectBadCases();
            log.info("Scheduled bad case auto collection completed");
        } catch (Exception e) {
            log.error("Scheduled bad case auto collection failed", e);
        }
    }

    @Scheduled(cron = "${app.badcase.scheduler.hourly-cron:0 0 * * * ?}")
    public void hourlyBadCaseCollection() {
        if (!enabled) {
            return;
        }
        try {
            log.debug("Hourly bad case auto collection triggered");
            badCaseService.autoCollectBadCases();
        } catch (Exception e) {
            log.warn("Hourly bad case auto collection failed", e);
        }
    }
}