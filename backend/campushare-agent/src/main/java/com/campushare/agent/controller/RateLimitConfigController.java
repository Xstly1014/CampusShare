package com.campushare.agent.controller;

import com.campushare.agent.dto.RateLimitConfig;
import com.campushare.agent.dto.RateLimitStatus;
import com.campushare.agent.service.RateLimitService;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent/config/rate-limit")
@RequiredArgsConstructor
public class RateLimitConfigController {

    private final RateLimitService rateLimitService;

    @GetMapping
    public Mono<Result<List<RateLimitConfig>>> getRateLimitConfigs() {
        List<RateLimitConfig> configs = List.of(
                RateLimitConfig.builder()
                        .key("global")
                        .maxRequests(1000)
                        .windowSeconds(60)
                        .strategy("SLIDING_WINDOW")
                        .enabled(true)
                        .description("全局限流：每分钟最多1000次请求")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                RateLimitConfig.builder()
                        .key("user:{userId}")
                        .maxRequests(60)
                        .windowSeconds(60)
                        .strategy("SLIDING_WINDOW")
                        .enabled(true)
                        .description("用户级限流：每分钟最多60次请求")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                RateLimitConfig.builder()
                        .key("ip:{ip}")
                        .maxRequests(500)
                        .windowSeconds(60)
                        .strategy("SLIDING_WINDOW")
                        .enabled(true)
                        .description("IP级限流：每分钟最多500次请求")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        return Mono.just(Result.success(configs));
    }

    @GetMapping("/{key}")
    public Mono<Result<RateLimitConfig>> getRateLimitConfig(@PathVariable String key) {
        RateLimitConfig config = RateLimitConfig.builder()
                .key(key)
                .maxRequests(60)
                .windowSeconds(60)
                .strategy("SLIDING_WINDOW")
                .enabled(true)
                .description("限流配置")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return Mono.just(Result.success(config));
    }

    @PostMapping
    public Mono<Result<RateLimitConfig>> createRateLimitConfig(@RequestBody RateLimitConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        log.info("Created rate limit config: {}", config.getKey());
        return Mono.just(Result.success(config));
    }

    @PutMapping("/{key}")
    public Mono<Result<RateLimitConfig>> updateRateLimitConfig(
            @PathVariable String key,
            @RequestBody RateLimitConfig config) {
        config.setKey(key);
        config.setUpdatedAt(LocalDateTime.now());
        log.info("Updated rate limit config: {}", key);
        return Mono.just(Result.success(config));
    }

    @DeleteMapping("/{key}")
    public Mono<Result<Void>> deleteRateLimitConfig(@PathVariable String key) {
        log.info("Deleted rate limit config: {}", key);
        return Mono.just(Result.success(null));
    }

    @GetMapping("/status")
    public Mono<Result<Map<String, RateLimitStatus>>> getRateLimitStatus() {
        Map<String, RateLimitStatus> statusMap = new HashMap<>();
        statusMap.put("global", RateLimitStatus.builder()
                .key("global")
                .current(0)
                .max(1000)
                .remaining(1000)
                .resetTime(System.currentTimeMillis() + 60000)
                .strategy("SLIDING_WINDOW")
                .build());
        return Mono.just(Result.success(statusMap));
    }

    @GetMapping("/status/{key}")
    public Mono<Result<RateLimitStatus>> getRateLimitStatusByKey(@PathVariable String key) {
        RateLimitStatus status = RateLimitStatus.builder()
                .key(key)
                .current(0)
                .max(60)
                .remaining(60)
                .resetTime(System.currentTimeMillis() + 60000)
                .strategy("SLIDING_WINDOW")
                .build();
        return Mono.just(Result.success(status));
    }

    @PostMapping("/{key}/reset")
    public Mono<Result<Void>> resetRateLimit(@PathVariable String key) {
        return rateLimitService.resetRateLimit(key)
                .then(Mono.just(Result.success(null)));
    }
}