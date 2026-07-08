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
@TableName("agent_turns")
public class AgentTurn implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;

    private Integer turnNumber;

    private String userMessage;

    private String assistantMessage;

    private String messageRole;

    private Integer tokensUsed;

    private String modelName;

    private String retrievalContext;

    private String toolsUsed;

    private Integer responseTimeMs;

    private String status;

    private String errorMessage;

    /** L1 意图（HOW_TO/SEARCH/NAVIGATE/CLARIFY/OUT_OF_SCOPE） */
    private String intent;

    /** 意图置信度（0-1，<0.6 触发 SEARCH 兜底） */
    private BigDecimal intentConfidence;

    /** 本轮输入 token 数（含 system/history/retrieval/user） */
    private Integer inputTokens;

    /** 本轮输出 token 数 */
    private Integer outputTokens;

    /** 用户反馈（LIKE/DISLIKE/NONE） */
    private String feedback;

    /** 关联上下文快照ID（agent_context_snapshots.id） */
    private Long contextSnapshotId;

    /** 是否被中断（0-否，1-是） */
    private Integer interrupted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
