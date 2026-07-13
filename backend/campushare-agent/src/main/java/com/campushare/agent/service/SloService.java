package com.campushare.agent.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SloService {

    void recordLatency(String objectiveName, long latencyMs, boolean success);

    void recordError(String objectiveName);

    Map<String, Object> getSloStatus(String objectiveName);

    List<Map<String, Object>> getAllSloStatus();

    Map<String, Object> getSloSummary(String objectiveName, LocalDateTime startTime, LocalDateTime endTime);

    boolean isBreaching(String objectiveName);

    double calculateBurnRate(String objectiveName, LocalDateTime startTime, LocalDateTime endTime);

    Map<String, Object> getLatencyPercentiles(String objectiveName, LocalDateTime startTime, LocalDateTime endTime);

    double getErrorRate(String objectiveName, LocalDateTime startTime, LocalDateTime endTime);
}