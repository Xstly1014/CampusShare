package com.campushare.agent.service.impl;

import com.campushare.agent.config.SloConfig;
import com.campushare.agent.service.SloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SloServiceImpl implements SloService {

    private final SloConfig sloConfig;
    private final StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Duration RETENTION_DURATION = Duration.ofDays(7);

    private final Map<String, List<Long>> latencyWindow = new ConcurrentHashMap<>();
    private final Map<String, AtomicCounter> errorCounter = new ConcurrentHashMap<>();
    private final Map<String, AtomicCounter> totalCounter = new ConcurrentHashMap<>();

    private static final int[] WINDOW_MINUTES = {1, 5, 15};
    private static final double[] BURN_RATE_THRESHOLDS = {1.0, 5.0, 14.4};
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();

    @Override
    public void recordLatency(String objectiveName, long latencyMs, boolean success) {
        if (!sloConfig.isEnabled()) {
            return;
        }

        latencyWindow.computeIfAbsent(objectiveName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(latencyMs);

        totalCounter.computeIfAbsent(objectiveName, k -> new AtomicCounter()).increment();
        if (!success) {
            errorCounter.computeIfAbsent(objectiveName, k -> new AtomicCounter()).increment();
        }

        String minuteKey = "slo:" + objectiveName + ":latency:" + LocalDateTime.now().format(MINUTE_FORMATTER);
        redisTemplate.opsForList().rightPush(minuteKey, String.valueOf(latencyMs));
        redisTemplate.expire(minuteKey, RETENTION_DURATION);

        String dayKey = "slo:" + objectiveName + ":daily:" + LocalDateTime.now().format(DAY_FORMATTER);
        redisTemplate.opsForHash().increment(dayKey, success ? "success" : "error", 1);
        redisTemplate.expire(dayKey, RETENTION_DURATION);
    }

    @Override
    public void recordError(String objectiveName) {
        errorCounter.computeIfAbsent(objectiveName, k -> new AtomicCounter()).increment();
        totalCounter.computeIfAbsent(objectiveName, k -> new AtomicCounter()).increment();

        String dayKey = "slo:" + objectiveName + ":daily:" + LocalDateTime.now().format(DAY_FORMATTER);
        redisTemplate.opsForHash().increment(dayKey, "error", 1);
        redisTemplate.expire(dayKey, RETENTION_DURATION);
    }

    @Override
    public Map<String, Object> getSloStatus(String objectiveName) {
        Map<String, Object> status = new HashMap<>();
        SloConfig.ServiceLevelObjective objective = sloConfig.getObjective(objectiveName);

        List<Long> latencies = latencyWindow.getOrDefault(objectiveName, Collections.emptyList());
        long errors = errorCounter.getOrDefault(objectiveName, new AtomicCounter()).get();
        long total = totalCounter.getOrDefault(objectiveName, new AtomicCounter()).get();

        status.put("objective", objectiveName);
        status.put("availability", objective.getAvailability());
        status.put("latencyP50Ms", objective.getLatencyP50Ms());
        status.put("latencyP95Ms", objective.getLatencyP95Ms());
        status.put("latencyP99Ms", objective.getLatencyP99Ms());
        status.put("errorRateThreshold", objective.getErrorRate());

        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            status.put("currentP50", latencies.get((int) (latencies.size() * 0.5)));
            status.put("currentP95", latencies.get((int) (latencies.size() * 0.95)));
            status.put("currentP99", latencies.get((int) (latencies.size() * 0.99)));
        }

        double currentErrorRate = total > 0 ? (double) errors / total : 0;
        status.put("currentErrorRate", currentErrorRate);
        status.put("breaching", isBreaching(objectiveName));

        return status;
    }

    @Override
    public List<Map<String, Object>> getAllSloStatus() {
        List<Map<String, Object>> allStatus = new ArrayList<>();
        Set<String> objectives = new HashSet<>();
        objectives.addAll(sloConfig.getObjectives().keySet());
        objectives.addAll(latencyWindow.keySet());

        for (String objective : objectives) {
            allStatus.add(getSloStatus(objective));
        }
        return allStatus;
    }

    @Override
    public Map<String, Object> getSloSummary(String objectiveName, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("objective", objectiveName);
        summary.put("startTime", startTime);
        summary.put("endTime", endTime);
        summary.put("latencyPercentiles", getLatencyPercentiles(objectiveName, startTime, endTime));
        summary.put("errorRate", getErrorRate(objectiveName, startTime, endTime));
        summary.put("burnRate", calculateBurnRate(objectiveName, startTime, endTime));
        return summary;
    }

    @Override
    public boolean isBreaching(String objectiveName) {
        SloConfig.ServiceLevelObjective objective = sloConfig.getObjective(objectiveName);
        List<Long> latencies = latencyWindow.getOrDefault(objectiveName, Collections.emptyList());

        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long p99 = latencies.get((int) (latencies.size() * 0.99));
            if (p99 > objective.getLatencyP99Ms()) {
                return true;
            }
        }

        long errors = errorCounter.getOrDefault(objectiveName, new AtomicCounter()).get();
        long total = totalCounter.getOrDefault(objectiveName, new AtomicCounter()).get();
        if (total > 0) {
            double errorRate = (double) errors / total;
            if (errorRate > objective.getErrorRate()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double calculateBurnRate(String objectiveName, LocalDateTime startTime, LocalDateTime endTime) {
        SloConfig.ServiceLevelObjective objective = sloConfig.getObjective(objectiveName);
        double errorRate = getErrorRate(objectiveName, startTime, endTime);
        double allowedErrorRate = 1 - objective.getAvailability() / 100;

        if (allowedErrorRate == 0) {
            return 0;
        }

        return errorRate / allowedErrorRate;
    }

    @Override
    public Map<String, Object> getLatencyPercentiles(String objectiveName, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> percentiles = new HashMap<>();
        List<Long> allLatencies = new ArrayList<>();

        LocalDateTime current = startTime;
        while (!current.isAfter(endTime)) {
            String key = "slo:" + objectiveName + ":latency:" + current.format(MINUTE_FORMATTER);
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            if (values != null) {
                values.forEach(v -> allLatencies.add(Long.parseLong(v)));
            }
            current = current.plusMinutes(1);
        }

        if (allLatencies.isEmpty()) {
            percentiles.put("p50", 0);
            percentiles.put("p95", 0);
            percentiles.put("p99", 0);
            percentiles.put("count", 0);
            return percentiles;
        }

        Collections.sort(allLatencies);
        percentiles.put("p50", allLatencies.get((int) (allLatencies.size() * 0.5)));
        percentiles.put("p95", allLatencies.get((int) (allLatencies.size() * 0.95)));
        percentiles.put("p99", allLatencies.get((int) (allLatencies.size() * 0.99)));
        percentiles.put("count", allLatencies.size());
        percentiles.put("min", allLatencies.get(0));
        percentiles.put("max", allLatencies.get(allLatencies.size() - 1));
        percentiles.put("avg", allLatencies.stream().mapToLong(Long::longValue).average().orElse(0));

        return percentiles;
    }

    @Override
    public double getErrorRate(String objectiveName, LocalDateTime startTime, LocalDateTime endTime) {
        long errors = 0;
        long success = 0;

        LocalDateTime current = startTime;
        while (!current.isAfter(endTime)) {
            String key = "slo:" + objectiveName + ":daily:" + current.format(DAY_FORMATTER);
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
            errors += hash.containsKey("error") ? Long.parseLong(String.valueOf(hash.get("error"))) : 0;
            success += hash.containsKey("success") ? Long.parseLong(String.valueOf(hash.get("success"))) : 0;
            current = current.plusDays(1);
        }

        long total = errors + success;
        return total > 0 ? (double) errors / total : 0;
    }

    public Map<String, Object> checkBurnRateAlerts(String objectiveName) {
        Map<String, Object> alertResult = new HashMap<>();
        alertResult.put("objective", objectiveName);
        alertResult.put("alerts", new ArrayList<Map<String, Object>>());
        alertResult.put("ok", true);

        SloConfig.ServiceLevelObjective objective = sloConfig.getObjective(objectiveName);
        if (objective == null) {
            return alertResult;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean hasAlert = false;

        for (int i = 0; i < WINDOW_MINUTES.length; i++) {
            int windowMinutes = WINDOW_MINUTES[i];
            double threshold = BURN_RATE_THRESHOLDS[i];

            LocalDateTime windowStart = now.minusMinutes(windowMinutes);
            double burnRate = calculateBurnRate(objectiveName, windowStart, now);

            Map<String, Object> windowResult = new HashMap<>();
            windowResult.put("windowMinutes", windowMinutes);
            windowResult.put("burnRate", burnRate);
            windowResult.put("threshold", threshold);
            windowResult.put("breaching", burnRate >= threshold);

            if (burnRate >= threshold) {
                hasAlert = true;
                windowResult.put("alert", true);

                String alertKey = objectiveName + ":" + windowMinutes + "m";
                LocalDateTime lastAlert = lastAlertTime.get(alertKey);
                if (lastAlert == null || lastAlert.isBefore(now.minusMinutes(5))) {
                    triggerAlert(objectiveName, windowMinutes, burnRate, threshold);
                    lastAlertTime.put(alertKey, now);
                    windowResult.put("alertTriggered", true);
                } else {
                    windowResult.put("alertTriggered", false);
                    windowResult.put("alertCooldown", true);
                }
            } else {
                windowResult.put("alert", false);
                windowResult.put("alertTriggered", false);
            }

            ((List<Map<String, Object>>) alertResult.get("alerts")).add(windowResult);
        }

        alertResult.put("ok", !hasAlert);
        return alertResult;
    }

    private void triggerAlert(String objectiveName, int windowMinutes, double burnRate, double threshold) {
        String alertMessage = String.format(
                "[SLO ALERT] Objective '%s' breaching burn rate threshold in %d-minute window. " +
                        "Burn rate: %.2fx, Threshold: %.2fx",
                objectiveName, windowMinutes, burnRate, threshold);

        log.error(alertMessage);

        String alertKey = "slo:alert:" + objectiveName + ":" + LocalDateTime.now().format(HOUR_FORMATTER);
        String alertData = String.format("{\"timestamp\":\"%s\",\"objective\":\"%s\",\"windowMinutes\":%d," +
                        "\"burnRate\":%.2f,\"threshold\":%.2f}",
                LocalDateTime.now(), objectiveName, windowMinutes, burnRate, threshold);
        redisTemplate.opsForList().rightPush(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofHours(24));
    }

    public List<Map<String, Object>> getRecentAlerts(String objectiveName, int limit) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        String alertKeyPattern = "slo:alert:" + objectiveName + ":*";

        Set<String> keys = redisTemplate.keys(alertKeyPattern);
        if (keys == null || keys.isEmpty()) {
            return alerts;
        }

        List<String> allAlertData = new ArrayList<>();
        for (String key : keys) {
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            if (values != null) {
                allAlertData.addAll(values);
            }
        }

        allAlertData.sort(Comparator.reverseOrder());

        int count = 0;
        for (String alertData : allAlertData) {
            if (count >= limit) break;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> alert = new java.util.LinkedHashMap<>();
                String[] parts = alertData.replace("{", "").replace("}", "").split(",");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        String k = kv[0].trim().replace("\"", "");
                        String v = kv[1].trim().replace("\"", "");
                        alert.put(k, v);
                    }
                }
                alerts.add(alert);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse alert data: {}", alertData);
            }
        }

        return alerts;
    }

    private static class AtomicCounter {
        private volatile long count = 0;

        public long get() {
            return count;
        }

        public void increment() {
            count++;
        }

        public void reset() {
            count = 0;
        }
    }
}