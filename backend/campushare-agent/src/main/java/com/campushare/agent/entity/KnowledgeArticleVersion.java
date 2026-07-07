package com.campushare.agent.entity;

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
 * 知识库文档版本历史实体，对应 MySQL knowledge_article_versions 表。
 *
 * 每次 KnowledgeArticle 更新前由 KnowledgeVersionService.snapshot() 写入完整快照，
 * 用于版本追溯和一键回滚。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_article_versions")
public class KnowledgeArticleVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private String version;

    private String title;

    private String topic;

    private String content;

    private String contentMd5;

    private Integer chunkCount;

    private String tags;

    private String snapshotReason;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
