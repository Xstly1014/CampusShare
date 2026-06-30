package com.campushare.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private String id;
    private String postId;
    private String schoolId;
    private String userId;
    private String username;
    private String avatarUrl;
    private String parentId;
    private String replyToUserId;
    private String replyToUsername;
    private String content;
    private Integer likeCount;
    private Boolean liked;
    private Boolean isAuthor;
    private LocalDateTime createTime;
}
