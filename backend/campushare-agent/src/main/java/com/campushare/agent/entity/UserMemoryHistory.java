package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("user_memory_history")
public class UserMemoryHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    private String memoryType;

    private String memoryKey;

    private String memoryValue;

    private BigDecimal confidence;

    private String source;

    /** 操作：INSERT/UPDATE/DELETE/DECAY/CONFLICT_RESOLVED */
    private String action;

    private String reason;

    private LocalDateTime createdAt;
}
