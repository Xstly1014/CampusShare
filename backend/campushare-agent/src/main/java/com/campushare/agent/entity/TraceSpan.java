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

    @Column("trace_id")
    private String traceId;

    @Column("span_id")
    private String spanId;

    @Column("parent_span_id")
    private String parentSpanId;

    @Column("session_id")
    private String sessionId;

    @Column("turn_id")
    private String turnId;

    @Column("user_id")
    private String userId;

    @Column("span_name")
    private String spanName;

    @Column("span_type")
    private String spanType;

    @Column("start_time")
    private LocalDateTime startTime;

    @Column("end_time")
    private LocalDateTime endTime;

    @Column("duration_ms")
    private Long durationMs;

    @Column("status")
    private String status;

    @Column("error_message")
    private String errorMessage;

    @Column("model_name")
    private String modelName;

    @Column("prompt_tokens")
    private Integer promptTokens;

    @Column("completion_tokens")
    private Integer completionTokens;

    @Column("total_tokens")
    private Integer totalTokens;

    @Column("intent")
    private String intent;

    @Column("tool_name")
    private String toolName;

    @Column("extra_data")
    private String extraData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
