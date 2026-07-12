package com.campushare.agent.tool;

import com.campushare.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initMetrics() {
        for (String toolName : toolRegistry.getToolNames()) {
            successCounters.put(toolName, Counter.builder("agent.tool.calls.success")
                    .tag("tool", toolName)
                    .description("Successful tool calls")
                    .register(meterRegistry));
            errorCounters.put(toolName, Counter.builder("agent.tool.calls.error")
                    .tag("tool", toolName)
                    .description("Failed tool calls")
                    .register(meterRegistry));
            timers.put(toolName, Timer.builder("agent.tool.latency")
                    .tag("tool", toolName)
                    .description("Tool call latency")
                    .register(meterRegistry));
        }
    }

    public Mono<ToolResult> execute(String toolName, Map<String, Object> arguments, String userId) {
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return Mono.just(ToolResult.error("TOOL_NOT_FOUND", "Unknown tool: " + toolName));
        }

        ToolRegistry.ToolDefinition def = toolRegistry.getDefinition(toolName);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tool-" + toolName);

        return Mono.fromCallable(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                ToolResult result = tool.execute(arguments, userId);
                sample.stop(timers.getOrDefault(toolName, timers.values().iterator().next()));
                if (result.getStatus() == ToolResult.Status.ERROR) {
                    errorCounters.getOrDefault(toolName, errorCounters.values().iterator().next()).increment();
                } else {
                    successCounters.getOrDefault(toolName, successCounters.values().iterator().next()).increment();
                }
                return result;
            } catch (Exception e) {
                errorCounters.getOrDefault(toolName, errorCounters.values().iterator().next()).increment();
                log.error("Tool execution failed: {}", toolName, e);
                return ToolResult.error("TOOL_EXECUTION_ERROR", e.getMessage());
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofMillis(def.timeoutMs()))
        .onErrorResume(TimeoutException.class, e -> {
            log.warn("Tool execution timeout: {}", toolName);
            return Mono.just(ToolResult.error("TOOL_TIMEOUT", "Tool execution timed out: " + toolName));
        })
        .onErrorResume(e -> {
            log.error("Tool execution error: {}", toolName, e);
            return Mono.just(ToolResult.error("TOOL_ERROR", e.getMessage()));
        })
        .transformDeferred(
                io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator.of(circuitBreaker)
        )
        .onErrorResume(
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                e -> Mono.just(ToolResult.error("TOOL_CIRCUIT_OPEN", "Service temporarily unavailable: " + toolName))
        );
    }

    public String resultToJson(ToolResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"error_code\":\"SERIALIZATION_ERROR\",\"error_message\":\"" + e.getMessage() + "\"}";
        }
    }
}
