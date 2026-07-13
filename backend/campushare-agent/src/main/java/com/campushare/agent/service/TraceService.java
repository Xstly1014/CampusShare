package com.campushare.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campushare.agent.entity.TraceSpan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TraceService extends IService<TraceSpan> {

    String generateTraceId();

    TraceSpan startSpan(String traceId, String spanName, String spanType);

    TraceSpan startSpan(String traceId, String parentSpanId, String spanName, String spanType);

    void endSpan(TraceSpan span);

    void endSpanWithError(TraceSpan span, String errorMessage);

    void recordLlmUsage(TraceSpan span, String modelName, Integer promptTokens,
                        Integer completionTokens, Integer totalTokens);

    void recordIntent(TraceSpan span, String intent);

    void recordToolCall(TraceSpan span, String toolName);

    void addExtraData(TraceSpan span, String key, Object value);

    List<TraceSpan> getTrace(String traceId);

    List<TraceSpan> getSessionTraces(String sessionId);

    Map<String, Object> getTraceSummary(String traceId);

    List<Map<String, Object>> getPerformanceMetrics(LocalDateTime startTime, LocalDateTime endTime);
}
