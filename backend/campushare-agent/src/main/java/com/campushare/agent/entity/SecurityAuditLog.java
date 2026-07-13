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

    @Column("audit_type")
    private String auditType;

    @Column("audit_level")
    private String auditLevel;

    @Column("input_text")
    private String inputText;

    @Column("output_text")
    private String outputText;

    @Column("detected_threat")
    private String detectedThreat;

    @Column("action_taken")
    private String actionTaken;

    @Column("confidence")
    private Double confidence;

    @Column("tool_name")
    private String toolName;

    @Column("tool_parameters")
    private String toolParameters;

    @Column("blocked")
    private Boolean blocked;

    @Column("error_message")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
