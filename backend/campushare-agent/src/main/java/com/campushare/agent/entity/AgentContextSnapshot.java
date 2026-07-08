package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上下文快照实体（对应 agent_context_snapshots 表）。
 *
 * 每次 ContextAssembler 组装完上下文后，由 ContextSnapshotService 异步写入。
 * 用于复盘"为什么答错"——能看到当时给 LLM 的完整输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_context_snapshots")
public class AgentContextSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Integer turnId;

    private String messagesJson;

    private String layerTokens;

    private Integer totalInputTokens;

    private String usedMemoryIds;

    private Integer truncated;

    private String truncationReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
