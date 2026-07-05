package com.campushare.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * System Prompt 版本管理实体。
 *
 * 对应 prompt_versions 表。L1 platform_prompt 在灰度发布时字节级固定（ADR-SP-06），
 * 仅 L2/L3/L4 可灰度切换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("prompt_versions")
public class PromptVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 版本号（SemVer，如 v1.0.0）。 */
    private String version;

    /** L1 平台级 Prompt（灰度时字节级固定）。 */
    private String platformPrompt;

    /** L2 操作指引 Prompt。 */
    private String howToPrompt;

    /** L2 内容检索 Prompt。 */
    private String searchPrompt;

    /** L2 闲聊 Prompt。 */
    private String chatPrompt;

    /** L3 Few-shot 示例。 */
    private String fewShotPrompt;

    /** L4 安全护栏。 */
    private String guardrailPrompt;

    /** 本次变更说明。 */
    private String changelog;

    /** 状态：DRAFT / GRAY / RELEASED / ROLLBACK。 */
    private String status;

    /** 灰度比例（0-100）。 */
    private Integer grayRatio;

    /** 创建者。 */
    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime releasedAt;
}
