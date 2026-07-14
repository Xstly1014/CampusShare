package com.campushare.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final StringRedisTemplate redisTemplate;

    private static final String QUOTA_PREFIX = "agent:quota:";
    private static final String COST_PREFIX = "agent:cost:";
    private static final String COST_ATTRIBUTION_PREFIX = "agent:cost_attr:";

    private static final Map<String, QuotaConfig> DEFAULT_QUOTAS = Map.of(
            "FREE", new QuotaConfig(100000, 1000000, 1000, 100),
            "PRO", new QuotaConfig(1000000, 10000000, 10000, 500),
            "ENTERPRISE", new QuotaConfig(10000000, 100000000, 100000, 10000)
    );

    public boolean checkQuota(String userId, int tokensToConsume) {
        String tier = getUserTier(userId);
        QuotaConfig config = DEFAULT_QUOTAS.getOrDefault(tier, DEFAULT_QUOTAS.get("FREE"));

        String dailyKey = QUOTA_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyKey = QUOTA_PREFIX + "monthly:" + userId + ":" + getMonthStr();

        long dailyUsed = getLong(dailyKey, 0);
        long monthlyUsed = getLong(monthlyKey, 0);

        if (dailyUsed + tokensToConsume > config.dailyTokenLimit) {
            log.warn("Daily quota exceeded: userId={}, used={}, limit={}",
                    userId, dailyUsed, config.dailyTokenLimit);
            return false;
        }

        if (monthlyUsed + tokensToConsume > config.monthlyTokenLimit) {
            log.warn("Monthly quota exceeded: userId={}, used={}, limit={}",
                    userId, monthlyUsed, config.monthlyTokenLimit);
            return false;
        }

        return true;
    }

    public void consumeQuota(String userId, int promptTokens, int completionTokens,
                             String model, String provider) {
        consumeQuota(userId, null, null, promptTokens, completionTokens, model, provider);
    }

    public void consumeQuota(String userId, String sessionId, String intent,
                             int promptTokens, int completionTokens,
                             String model, String provider) {
        int totalTokens = promptTokens + completionTokens;
        if (totalTokens <= 0) return;

        String dailyKey = QUOTA_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyKey = QUOTA_PREFIX + "monthly:" + userId + ":" + getMonthStr();

        redisTemplate.opsForValue().increment(dailyKey, totalTokens);
        redisTemplate.opsForValue().increment(monthlyKey, totalTokens);

        BigDecimal cost = calculateCost(promptTokens, completionTokens, model);
        recordCost(userId, cost, model, provider);
        recordCostAttribution(userId, sessionId, intent, model, cost, getTodayStr());

        log.debug("Quota consumed: userId={}, sessionId={}, intent={}, prompt={}, completion={}, cost={}",
                userId, sessionId, intent, promptTokens, completionTokens, cost);
    }

    public QuotaStatus getQuotaStatus(String userId) {
        String tier = getUserTier(userId);
        QuotaConfig config = DEFAULT_QUOTAS.getOrDefault(tier, DEFAULT_QUOTAS.get("FREE"));

        String dailyKey = QUOTA_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyKey = QUOTA_PREFIX + "monthly:" + userId + ":" + getMonthStr();

        long dailyUsed = getLong(dailyKey, 0);
        long monthlyUsed = getLong(monthlyKey, 0);

        return QuotaStatus.builder()
                .userId(userId)
                .tier(tier)
                .dailyUsed(dailyUsed)
                .dailyLimit(config.dailyTokenLimit)
                .monthlyUsed(monthlyUsed)
                .monthlyLimit(config.monthlyTokenLimit)
                .dailyRemaining(Math.max(0, config.dailyTokenLimit - dailyUsed))
                .monthlyRemaining(Math.max(0, config.monthlyTokenLimit - monthlyUsed))
                .build();
    }

    private BigDecimal calculateCost(int promptTokens, int completionTokens, String model) {
        double promptCostPerMillion = 0.001;
        double completionCostPerMillion = 0.002;

        if (model != null) {
            if (model.contains("pro") || model.contains("plus")) {
                promptCostPerMillion = 0.002;
                completionCostPerMillion = 0.004;
            } else if (model.contains("turbo")) {
                promptCostPerMillion = 0.0005;
                completionCostPerMillion = 0.0015;
            }
        }

        double cost = (promptTokens * promptCostPerMillion + completionTokens * completionCostPerMillion) / 1000000;
        return BigDecimal.valueOf(cost);
    }

    private void recordCost(String userId, BigDecimal cost, String model, String provider) {
        String dailyCostKey = COST_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyCostKey = COST_PREFIX + "monthly:" + userId + ":" + getMonthStr();
        String modelCostKey = COST_PREFIX + "model:" + userId + ":" + model + ":" + getTodayStr();

        redisTemplate.opsForValue().increment(dailyCostKey, cost.doubleValue());
        redisTemplate.opsForValue().increment(monthlyCostKey, cost.doubleValue());
        redisTemplate.opsForValue().increment(modelCostKey, cost.doubleValue());
    }

    private void recordCostAttribution(String userId, String sessionId, String intent,
                                       String model, BigDecimal cost, String dayStr) {
        double costValue = cost.doubleValue();

        String userDayKey = COST_ATTRIBUTION_PREFIX + "user:day:" + userId + ":" + dayStr;
        String sessionDayKey = sessionId != null ? COST_ATTRIBUTION_PREFIX + "session:day:" + sessionId + ":" + dayStr : null;
        String intentDayKey = intent != null ? COST_ATTRIBUTION_PREFIX + "intent:day:" + intent + ":" + dayStr : null;
        String modelDayKey = model != null ? COST_ATTRIBUTION_PREFIX + "model:day:" + model + ":" + dayStr : null;

        redisTemplate.opsForValue().increment(userDayKey, costValue);
        if (sessionDayKey != null) {
            redisTemplate.opsForValue().increment(sessionDayKey, costValue);
        }
        if (intentDayKey != null) {
            redisTemplate.opsForValue().increment(intentDayKey, costValue);
        }
        if (modelDayKey != null) {
            redisTemplate.opsForValue().increment(modelDayKey, costValue);
        }

        String userTotalKey = COST_ATTRIBUTION_PREFIX + "user:total:" + userId;
        redisTemplate.opsForValue().increment(userTotalKey, costValue);
    }

    public Map<String, Object> getCostAttribution(String userId, String dayStr) {
        Map<String, Object> result = new java.util.HashMap<>();

        String userDayKey = COST_ATTRIBUTION_PREFIX + "user:day:" + userId + ":" + dayStr;
        String userTotalKey = COST_ATTRIBUTION_PREFIX + "user:total:" + userId;

        result.put("userDailyCost", getDouble(userDayKey, 0.0));
        result.put("userTotalCost", getDouble(userTotalKey, 0.0));

        return result;
    }

    private double getDouble(String key, double defaultValue) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }

    private String getUserTier(String userId) {
        String tierKey = QUOTA_PREFIX + "tier:" + userId;
        String tier = redisTemplate.opsForValue().get(tierKey);
        return tier != null ? tier : "FREE";
    }

    private static final String ALERT_PREFIX = "agent:alert:cost:";
    private static final Map<String, CostAlertConfig> COST_ALERT_CONFIGS = Map.of(
            "FREE", new CostAlertConfig(5.0, 10.0, 15.0, 30),
            "PRO", new CostAlertConfig(50.0, 100.0, 200.0, 60),
            "ENTERPRISE", new CostAlertConfig(500.0, 1000.0, 2000.0, 120)
    );

    public Map<String, Object> checkCostAlerts(String userId) {
        String tier = getUserTier(userId);
        CostAlertConfig config = COST_ALERT_CONFIGS.getOrDefault(tier, COST_ALERT_CONFIGS.get("FREE"));

        String dailyCostKey = COST_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyCostKey = COST_PREFIX + "monthly:" + userId + ":" + getMonthStr();

        double dailyCost = getDouble(dailyCostKey, 0.0);
        double monthlyCost = getDouble(monthlyCostKey, 0.0);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("tier", tier);
        result.put("dailyCost", dailyCost);
        result.put("monthlyCost", monthlyCost);
        result.put("alerts", new java.util.ArrayList<Map<String, Object>>());

        checkAndTriggerAlert(userId, "daily", dailyCost, config.dailyWarningThreshold, "WARNING", result);
        checkAndTriggerAlert(userId, "daily", dailyCost, config.dailyCriticalThreshold, "CRITICAL", result);
        checkAndTriggerAlert(userId, "monthly", monthlyCost, config.monthlyWarningThreshold, "WARNING", result);
        checkAndTriggerAlert(userId, "monthly", monthlyCost, config.monthlyCriticalThreshold, "CRITICAL", result);

        return result;
    }

    private void checkAndTriggerAlert(String userId, String period, double cost,
                                       double threshold, String severity, Map<String, Object> result) {
        if (cost < threshold) return;

        String alertKey = ALERT_PREFIX + period + ":" + severity + ":" + userId;
        String lastAlertStr = redisTemplate.opsForValue().get(alertKey);

        long cooldownMinutes = COST_ALERT_CONFIGS.getOrDefault(getUserTier(userId),
                COST_ALERT_CONFIGS.get("FREE")).alertCooldownMinutes;

        if (lastAlertStr != null) {
            try {
                LocalDateTime lastAlert = LocalDateTime.parse(lastAlertStr);
                if (LocalDateTime.now().isBefore(lastAlert.plusMinutes(cooldownMinutes))) {
                    return;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        log.error("[COST ALERT] userId={}, period={}, severity={}, cost={}, threshold={}",
                userId, period, severity, cost, threshold);

        redisTemplate.opsForValue().set(alertKey, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Map<String, Object> alert = new HashMap<>();
        alert.put("period", period);
        alert.put("severity", severity);
        alert.put("cost", cost);
        alert.put("threshold", threshold);
        alert.put("timestamp", LocalDateTime.now());
        ((java.util.List<Map<String, Object>>) result.get("alerts")).add(alert);
    }

    public Map<String, Object> getSystemCostOverview() {
        Map<String, Object> result = new HashMap<>();

        double totalDailyCost = 0;
        double totalMonthlyCost = 0;
        long activeUsersToday = 0;

        String todayStr = getTodayStr();
        String monthStr = getMonthStr();

        try {
            var dailyKeys = redisTemplate.keys(COST_PREFIX + "daily:*:" + todayStr);
            var monthlyKeys = redisTemplate.keys(COST_PREFIX + "monthly:*:" + monthStr);

            if (dailyKeys != null) {
                for (String key : dailyKeys) {
                    try {
                        double cost = getDouble(key, 0.0);
                        totalDailyCost += cost;
                        activeUsersToday++;
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

            if (monthlyKeys != null) {
                for (String key : monthlyKeys) {
                    try {
                        totalMonthlyCost += getDouble(key, 0.0);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get system cost overview", e);
        }

        result.put("totalDailyCost", totalDailyCost);
        result.put("totalMonthlyCost", totalMonthlyCost);
        result.put("activeUsersToday", activeUsersToday);
        result.put("timestamp", LocalDateTime.now());

        return result;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CostAlertConfig {
        private double dailyWarningThreshold;
        private double dailyCriticalThreshold;
        private double monthlyWarningThreshold;
        private double monthlyCriticalThreshold;
        private int alertCooldownMinutes;

        public CostAlertConfig(double dailyWarningThreshold, double dailyCriticalThreshold,
                               double monthlyWarningThreshold, int alertCooldownMinutes) {
            this.dailyWarningThreshold = dailyWarningThreshold;
            this.dailyCriticalThreshold = dailyCriticalThreshold;
            this.monthlyWarningThreshold = monthlyWarningThreshold;
            this.monthlyCriticalThreshold = monthlyWarningThreshold * 2;
            this.alertCooldownMinutes = alertCooldownMinutes;
        }
    }

    private long getLong(String key, long defaultValue) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    private String getTodayStr() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String getMonthStr() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QuotaConfig {
        private long dailyTokenLimit;
        private long monthlyTokenLimit;
        private int dailyRequestLimit;
        private int concurrentRequestLimit;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QuotaStatus {
        private String userId;
        private String tier;
        private long dailyUsed;
        private long dailyLimit;
        private long monthlyUsed;
        private long monthlyLimit;
        private long dailyRemaining;
        private long monthlyRemaining;
    }
}
