package com.campushare.agent.controller;

import com.campushare.agent.dto.RateLimitConfig;
import com.campushare.agent.dto.RateLimitStatus;
import com.campushare.agent.service.RateLimitService;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
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
        return rateLimitService.getAllConfigs()
                .flatMap(configs -> {
                    if (configs.isEmpty()) {
                        // Initialize default configs on first access
                        List<RateLimitConfig> defaults = createDefaultConfigs();
                        return Flux.fromIterable(defaults)
                                .flatMap(rateLimitService::saveConfig)
                                .collectList()
                                .map(saved -> Result.success(defaults));
                    }
                    return Mono.just(Result.success(configs));
                });
    }

    @GetMapping("/{key}")
    public Mono<Result<RateLimitConfig>> getRateLimitConfig(@PathVariable String key) {
        return rateLimitService.getConfig(key)
                .map(Result::success)
                .switchIfEmpty(Mono.just(Result.error(4040, "Config not found: " + key)));
    }

    @PostMapping
    public Mono<Result<RateLimitConfig>> createRateLimitConfig(@RequestBody RateLimitConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return rateLimitService.saveConfig(config)
                .map(Result::success);
    }

    @PutMapping("/{key}")
    public Mono<Result<RateLimitConfig>> updateRateLimitConfig(
            @PathVariable String key,
            @RequestBody RateLimitConfig config) {
        config.setKey(key);
        config.setUpdatedAt(LocalDateTime.now());
        return rateLimitService.saveConfig(config)
                .map(Result::success);
    }

    @DeleteMapping("/{key}")
    public Mono<Result<Void>> deleteRateLimitConfig(@PathVariable String key) {
        return rateLimitService.deleteConfig(key)
                .then(Mono.just(Result.success(null)));
    }

    @GetMapping("/status")
    public Mono<Result<Map<String, RateLimitStatus>>> getRateLimitStatus() {
        return rateLimitService.getStatus("global")
                .map(status -> {
                    Map<String, RateLimitStatus> map = new HashMap<>();
                    map.put("global", status);
                    return Result.success(map);
                });
    }

    @GetMapping("/status/{key}")
    public Mono<Result<RateLimitStatus>> getRateLimitStatusByKey(@PathVariable String key) {
        return rateLimitService.getStatus(key)
                .map(Result::success);
    }

    @PostMapping("/{key}/reset")
    public Mono<Result<Void>> resetRateLimit(@PathVariable String key) {
        return rateLimitService.resetRateLimit(key)
                .then(Mono.just(Result.success(null)));
    }

    private List<RateLimitConfig> createDefaultConfigs() {
        return List.of(
                RateLimitConfig.builder()
                        .key("global").maxRequests(1000).windowSeconds(60)
                        .strategy("SLIDING_WINDOW").enabled(true)
                        .description("Global rate limit: max 1000 requests per minute")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build(),
                RateLimitConfig.builder()
                        .key("user:{userId}").maxRequests(60).windowSeconds(60)
                        .strategy("SLIDING_WINDOW").enabled(true)
                        .description("User rate limit: max 60 requests per minute")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build(),
                RateLimitConfig.builder()
                        .key("ip:{ip}").maxRequests(500).windowSeconds(60)
                        .strategy("SLIDING_WINDOW").enabled(true)
                        .description("IP rate limit: max 500 requests per minute")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build()
        );
    }
}