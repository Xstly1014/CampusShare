package com.campushare.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campushare.agent.entity.TraceSpan;
import com.campushare.agent.mapper.TraceSpanMapper;
import com.campushare.agent.service.TraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceServiceImpl extends ServiceImpl<TraceSpanMapper, TraceSpan> implements TraceService {

    private final ObjectMapper objectMapper;

    @Override
    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public TraceSpan startSpan(String traceId, String spanName, String spanType) {
        return startSpan(traceId, null, spanName, spanType);
    }

    @Override
    public TraceSpan startSpan(String traceId, String parentSpanId, String spanName, String spanType) {
        TraceSpan span = TraceSpan.builder()
                .traceId(traceId)
                .spanId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .parentSpanId(parentSpanId)
                .spanName(spanName)
                .spanType(spanType)
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .build();
        save(span);
        log.debug("Started span: trace={}, span={}, name={}", traceId, span.getSpanId(), spanName);
        return span;
    }

    @Override
    @Transactional
    public void endSpan(TraceSpan span) {
        span.setEndTime(LocalDateTime.now());
        span.setDurationMs(Duration.between(span.getStartTime(), span.getEndTime()).toMillis());
        span.setStatus("COMPLETED");
        updateById(span);
        log.debug("Ended span: trace={}, span={}, duration={}ms",
                span.getTraceId(), span.getSpanId(), span.getDurationMs());
    }

    @Override
    @Transactional
    public void endSpanWithError(TraceSpan span, String errorMessage) {
        span.setEndTime(LocalDateTime.now());
        span.setDurationMs(Duration.between(span.getStartTime(), span.getEndTime()).toMillis());
        span.setStatus("FAILED");
        span.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) + "..." : errorMessage);
        updateById(span);
        log.warn("Span failed: trace={}, span={}, error={}",
                span.getTraceId(), span.getSpanId(), errorMessage);
    }

    @Override
    @Transactional
    public void recordLlmUsage(TraceSpan span, String modelName, Integer promptTokens,
                               Integer completionTokens, Integer totalTokens) {
        span.setModelName(modelName);
        span.setPromptTokens(promptTokens);
        span.setCompletionTokens(completionTokens);
        span.setTotalTokens(totalTokens);
        updateById(span);
    }

    @Override
    @Transactional
    public void recordIntent(TraceSpan span, String intent) {
        span.setIntent(intent);
        updateById(span);
    }

    @Override
    @Transactional
    public void recordToolCall(TraceSpan span, String toolName) {
        span.setToolName(toolName);
        updateById(span);
    }

    @Override
    @Transactional
    public void addExtraData(TraceSpan span, String key, Object value) {
        try {
            Map<String, Object> extraData = span.getExtraData() != null
                    ? objectMapper.readValue(span.getExtraData(), Map.class)
                    : new HashMap<>();
            extraData.put(key, value);
            span.setExtraData(objectMapper.writeValueAsString(extraData));
            updateById(span);
        } catch (Exception e) {
            log.warn("Failed to add extra data to span", e);
        }
    }

    @Override
    public List<TraceSpan> getTrace(String traceId) {
        return ((TraceSpanMapper) getBaseMapper()).findByTraceId(traceId);
    }

    @Override
    public List<TraceSpan> getSessionTraces(String sessionId) {
        return ((TraceSpanMapper) getBaseMapper()).findBySessionId(sessionId);
    }

    @Override
    public Map<String, Object> getTraceSummary(String traceId) {
        List<TraceSpan> spans = getTrace(traceId);
        if (spans.isEmpty()) {
            return Map.of("traceId", traceId, "error", "not found");
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("traceId", traceId);

        long totalDuration = spans.stream()
                .filter(s -> s.getDurationMs() != null)
                .mapToLong(TraceSpan::getDurationMs)
                .sum();
        summary.put("totalDurationMs", totalDuration);

        long successCount = spans.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedCount = spans.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        summary.put("successCount", successCount);
        summary.put("failedCount", failedCount);

        int totalTokens = spans.stream()
                .filter(s -> s.getTotalTokens() != null)
                .mapToInt(TraceSpan::getTotalTokens)
                .sum();
        summary.put("totalTokens", totalTokens);

        Map<String, Long> durationByType = new HashMap<>();
        spans.forEach(span -> {
            String type = span.getSpanType();
            durationByType.merge(type, span.getDurationMs() != null ? span.getDurationMs() : 0L, Long::sum);
        });
        summary.put("durationByType", durationByType);

        return summary;
    }

    @Override
    public List<Map<String, Object>> getPerformanceMetrics(LocalDateTime startTime, LocalDateTime endTime) {
        return ((TraceSpanMapper) getBaseMapper()).getAvgDurationBySpanType(startTime, endTime);
    }
}
