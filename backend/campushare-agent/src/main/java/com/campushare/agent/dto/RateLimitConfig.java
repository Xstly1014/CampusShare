package com.campushare.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {
    private String key;
    private int maxRequests;
    private int windowSeconds;
    private String strategy;
    private long burstCapacity;
    private long refillTokens;
    private boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}