package com.campushare.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUOTA_PREFIX = "agent:quota:";
    private static final String COST_PREFIX = "agent:cost:";

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
        int totalTokens = promptTokens + completionTokens;
        if (totalTokens <= 0) return;

        String dailyKey = QUOTA_PREFIX + "daily:" + userId + ":" + getTodayStr();
        String monthlyKey = QUOTA_PREFIX + "monthly:" + userId + ":" + getMonthStr();

        redisTemplate.opsForValue().increment(dailyKey, totalTokens);
        redisTemplate.opsForValue().increment(monthlyKey, totalTokens);

        BigDecimal cost = calculateCost(promptTokens, completionTokens, model);
        recordCost(userId, cost, model, provider);

        log.debug("Quota consumed: userId={}, prompt={}, completion={}, cost={}",
                userId, promptTokens, completionTokens, cost);
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

    private String getUserTier(String userId) {
        String tierKey = QUOTA_PREFIX + "tier:" + userId;
        Object tier = redisTemplate.opsForValue().get(tierKey);
        return tier != null ? tier.toString() : "FREE";
    }

    private long getLong(String key, long defaultValue) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? ((Number) value).longValue() : defaultValue;
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
