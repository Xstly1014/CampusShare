package com.campushare.agent.controller;

import com.campushare.common.result.Result;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/agent/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {

  private final CircuitBreakerRegistry circuitBreakerRegistry;

  @GetMapping("/status")
  public Mono<Result<List<Map<String, Object>>>> getAllStatus() {
    List<Map<String, Object>> statusList = circuitBreakerRegistry.getAllCircuitBreakers().stream()
        .map(this::toStatusMap)
        .collect(Collectors.toList());
    return Mono.just(Result.success(statusList));
  }

  @GetMapping("/status/{name}")
  public Mono<Result<Map<String, Object>>> getStatus(@PathVariable String name) {
    CircuitBreaker cb = circuitBreakerRegistry.find(name)
        .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + name));
    return Mono.just(Result.success(toStatusMap(cb)));
  }

  @PostMapping("/{name}/reset")
  public Mono<Result<Void>> resetCircuitBreaker(@PathVariable String name) {
    CircuitBreaker cb = circuitBreakerRegistry.find(name)
        .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + name));
    cb.reset();
    log.info("Circuit breaker reset: {}", name);
    return Mono.just(Result.success(null));
  }

  private Map<String, Object> toStatusMap(CircuitBreaker cb) {
    Map<String, Object> status = new HashMap<>();
    status.put("name", cb.getName());
    status.put("state", cb.getState().name());
    status.put("failureRate", cb.getMetrics().getFailureRate());
    status.put("slowCallRate", cb.getMetrics().getSlowCallRate());
    status.put("currentNumberOfCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
    status.put("lastTransition", LocalDateTime.now().toString());
    return status;
  }
}
