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
@TableName("agent_eval_test_cases")
public class EvalTestCase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("user_query")
    private String userQuery;

    @TableField("expected_intent")
    private String expectedIntent;

    @TableField("expected_sub_intent")
    private String expectedSubIntent;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("expected_refs")
    private String expectedRefs;

    @TableField("expected_navigate_route")
    private String expectedNavigateRoute;

    @TableField("is_golden")
    @Builder.Default
    private Boolean isGolden = false;

    @TableField("priority")
    @Builder.Default
    private Integer priority = 1;

    @TableField("tags")
    private String tags;

    @TableField("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}