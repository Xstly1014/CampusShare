package com.campushare.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.slo")
public class SloConfig {

    private boolean enabled = true;

    private Map<String, ServiceLevelObjective> objectives = new HashMap<>();

    @Data
    public static class ServiceLevelObjective {
        private String name;
        private String description;
        private double availability = 99.9;
        private double latencyP50Ms = 500;
        private double latencyP95Ms = 2000;
        private double latencyP99Ms = 5000;
        private double errorRate = 0.01;
        private int burnRateThreshold = 5;
        private int windowMinutes = 5;
    }

    public ServiceLevelObjective getObjective(String name) {
        return objectives.getOrDefault(name, new ServiceLevelObjective());
    }
}