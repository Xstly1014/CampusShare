package com.campushare.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子向量化数据传输对象。
 *
 * 供 agent-service 拉取帖子数据做 embedding 使用。
 * contentExcerpt 为 content 截取前 500 字，避免 embedding 超长。
 * 字段需与 agent-service 侧 PostVectorDTO 严格对齐。
 */
@Data
public class PostVectorDTO {
    private String id;
    private String title;
    private String contentExcerpt;
    private String postType;
    private String categoryId;
    private String schoolId;
    private String authorId;
    private Integer likeCount;
    private Integer viewCount;
    private LocalDateTime createTime;
}
