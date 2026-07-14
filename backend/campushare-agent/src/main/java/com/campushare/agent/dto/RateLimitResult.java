package com.campushare.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private String exceededKey;
    private int current;
    private int max;

    public static RateLimitResult allowed() {
        return RateLimitResult.builder()
                .allowed(true)
                .build();
    }

    public static RateLimitResult exceeded(String key, int current, int max) {
        return RateLimitResult.builder()
                .allowed(false)
                .exceededKey(key)
                .current(current)
                .max(max)
                .build();
    }
}