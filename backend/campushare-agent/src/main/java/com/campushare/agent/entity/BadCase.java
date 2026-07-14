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
@TableName("agent_bad_cases")
public class BadCase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("session_id")
    private String sessionId;

    @TableField("turn_id")
    private String turnId;

    @TableField("user_id")
    private String userId;

    @TableField("trace_id")
    private String traceId;

    @TableField("user_query")
    private String userQuery;

    @TableField("actual_intent")
    private String actualIntent;

    @TableField("actual_answer")
    private String actualAnswer;

    @TableField("actual_refs")
    private String actualRefs;

    @TableField("expected_intent")
    private String expectedIntent;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("bad_case_type")
    private String badCaseType;

    @TableField("severity")
    @Builder.Default
    private Integer severity = 1;

    @TableField("tags")
    private String tags;

    @TableField("status")
    @Builder.Default
    private String status = "NEW";

    @TableField("assignee")
    private String assignee;

    @TableField("note")
    private String note;

    @TableField("similar_case_count")
    @Builder.Default
    private Integer similarCaseCount = 0;

    @TableField("converted_to_test_case")
    @Builder.Default
    private Boolean convertedToTestCase = false;

    @TableField("first_seen_at")
    private LocalDateTime firstSeenAt;

    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    @TableField("occurrence_count")
    @Builder.Default
    private Integer occurrenceCount = 1;

    @TableField("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}