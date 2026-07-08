package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_sessions")
public class AgentSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String title;

    private String status;

    private Integer messageCount;

    private Integer totalTokens;

    private BigDecimal totalCost;

    private LocalDateTime lastMessageAt;

    private String metadata;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String categoryId;

    /** 使用的 System Prompt 版本（SemVer，如 v1.0.0） */
    private String promptVersion;

    /** 使用的 LLM 模型名（如 deepseek-chat） */
    private String llmModel;

    /** 会话意图汇总（JSON，如 {"HOW_TO":3,"SEARCH":2}） */
    private String intentSummary;

    /** 累计输入 token 数（上下文工程） */
    private Integer totalInputTokens;

    /** 累计输出 token 数 */
    private Integer totalOutputTokens;

    /** 会话质量评分（0-1，由反馈和长度加权） */
    private BigDecimal qualityScore;

    /** 会话失败原因（status=ERROR 时） */
    private String errorReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
