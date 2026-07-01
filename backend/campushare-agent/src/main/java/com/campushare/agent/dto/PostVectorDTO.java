package com.campushare.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子向量化数据传输对象（agent-service 侧）。
 *
 * 字段需与 post-service 侧 PostVectorDTO 严格对齐，否则 Feign 反序列化失败。
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
