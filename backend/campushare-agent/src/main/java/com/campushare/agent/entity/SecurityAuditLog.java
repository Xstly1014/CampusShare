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
@TableName("agent_security_audit_log")
public class SecurityAuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;

    private String userId;

    private String turnId;

    @TableField("audit_type")
    private String auditType;

    @TableField("audit_level")
    private String auditLevel;

    @TableField("input_text")
    private String inputText;

    @TableField("output_text")
    private String outputText;

    @TableField("detected_threat")
    private String detectedThreat;

    @TableField("action_taken")
    private String actionTaken;

    @TableField("confidence")
    private Double confidence;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_parameters")
    private String toolParameters;

    @TableField("blocked")
    private Boolean blocked;

    @TableField("error_message")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
