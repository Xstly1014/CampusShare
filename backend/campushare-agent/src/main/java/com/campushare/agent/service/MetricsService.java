package com.campushare.agent.service;

import io.micrometer.core.instrument.Timer;

public interface MetricsService {

    void recordChatLatency(long latencyMs);
    void recordIntentLatency(long latencyMs);
    void recordRagLatency(long latencyMs);
    void recordLlmLatency(long latencyMs);
    void recordToolLatency(String toolName, long latencyMs);

    void recordPromptTokens(int tokens);
    void recordCompletionTokens(int tokens);

    void recordCost(double cost);
    void recordUserCost(String userId, double cost);

    void recordChatError();
    void recordIntentError();

    void recordToolCall(String toolName, boolean success);

    void recordCacheHit(String cacheName);
    void recordCacheMiss(String cacheName);
    void recordCacheEviction(String cacheName);

    Timer.Sample startTimer();
    void stopTimer(Timer.Sample sample, String metricName);
}