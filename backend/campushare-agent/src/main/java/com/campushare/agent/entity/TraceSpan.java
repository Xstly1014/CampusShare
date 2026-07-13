package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_trace_spans")
public class TraceSpan {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("trace_id")
    private String traceId;

    @TableField("span_id")
    private String spanId;

    @TableField("parent_span_id")
    private String parentSpanId;

    @TableField("session_id")
    private String sessionId;

    @TableField("turn_id")
    private String turnId;

    @TableField("user_id")
    private String userId;

    @TableField("span_name")
    private String spanName;

    @TableField("span_type")
    private String spanType;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("intent")
    private String intent;

    @TableField("tool_name")
    private String toolName;

    @TableField("extra_data")
    private String extraData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
