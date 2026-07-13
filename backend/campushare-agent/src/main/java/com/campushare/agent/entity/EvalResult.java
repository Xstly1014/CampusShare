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
@TableName("agent_eval_results")
public class EvalResult {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("test_case_id")
    private String testCaseId;

    @TableField("test_case_name")
    private String testCaseName;

    @TableField("run_id")
    private String runId;

    @TableField("actual_intent")
    private String actualIntent;

    @TableField("actual_sub_intent")
    private String actualSubIntent;

    @TableField("actual_answer")
    private String actualAnswer;

    @TableField("actual_refs")
    private String actualRefs;

    @TableField("actual_navigate_route")
    private String actualNavigateRoute;

    @TableField("intent_match")
    @Builder.Default
    private Boolean intentMatch = false;

    @TableField("sub_intent_match")
    @Builder.Default
    private Boolean subIntentMatch = false;

    @TableField("answer_match")
    @Builder.Default
    private Boolean answerMatch = false;

    @TableField("refs_match")
    @Builder.Default
    private Boolean refsMatch = false;

    @TableField("navigate_match")
    @Builder.Default
    private Boolean navigateMatch = false;

    @TableField("llm_judge_score")
    private Integer llmJudgeScore;

    @TableField("llm_judge_reason")
    private String llmJudgeReason;

    @TableField("overall_score")
    private Integer overallScore;

    @TableField("passed")
    @Builder.Default
    private Boolean passed = false;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_version")
    private String promptVersion;

    @TableField("response_time_ms")
    private Integer responseTimeMs;

    @TableField("tokens_used")
    private Integer tokensUsed;

    @TableField("cost_usd")
    private java.math.BigDecimal costUsd;

    @TableField("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}