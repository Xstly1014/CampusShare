package com.campushare.agent.llm.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.llm")
public class LlmGatewayConfig {

    private Map<String, ProviderConfig> providers = new HashMap<>();

    private String defaultProvider = "deepseek";

    private String defaultModel = "deepseek-v4-flash";

    private int maxRetryAttempts = 3;

    private long retryBackoffMs = 1000;

    private long healthCheckIntervalMs = 30000;

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private long timeoutMs = 60000;
        private long streamTimeoutSeconds = 120;
        private boolean enabled = true;
        private int priority = 100;
        private double costPerMillionTokens = 0.0;
    }
}
