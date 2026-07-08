package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话状态转移事件实体（ADR-068）。
 *
 * 所有状态转移写入此表，保留 90 天用于审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_session_events")
public class AgentSessionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private LocalDateTime createdAt;
}
