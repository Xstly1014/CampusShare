package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户长期记忆实体（ADR-059）。
 *
 * 跨会话沉淀用户偏好与行为模式，让 Agent 在新会话首轮就能个性化。
 * 按"显式 vs 隐式" + "稳定 vs 动态"分四象限：
 *   - EXPLICIT PREFERENCE/FACT — 用户明说，不衰减
 *   - INFERRED BEHAVIOR — 行为推断，周衰减 0.1
 *   - TASK — 当前任务，4 周未更新删除
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_memory")
public class UserMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 记忆类型：PREFERENCE / FACT / BEHAVIOR / TASK */
    private String memoryType;

    /** 记忆键（如 preferred_format / major / top_category） */
    private String memoryKey;

    /** 记忆值（JSON 或文本） */
    private String memoryValue;

    /** 置信度 0-1（隐式记忆会衰减） */
    private BigDecimal confidence;

    /** 来源：EXPLICIT（用户明说）/ INFERRED（行为推断） */
    private String source;

    /** 证据数量（隐式累积） */
    private Integer evidenceCount;

    /** 是否有冲突（隐式与显式冲突时标记） */
    private Integer conflictFlag;

    /** 是否易变（同 key 多次变更时降权） */
    private Integer volatileFlag;

    /** 最近一次被装载入上下文的时间 */
    private LocalDateTime lastUsedAt;

    /** 软删除时间（30 天回收站，TableLogic 自动过滤） */
    @TableLogic
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
