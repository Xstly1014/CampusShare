package com.campushare.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitStatus {
    private String key;
    private int current;
    private int max;
    private int remaining;
    private long resetTime;
    private String strategy;
}