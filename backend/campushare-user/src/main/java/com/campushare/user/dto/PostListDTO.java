package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListDTO {
    private String id;
    private String schoolId;
    private String categoryId;
    private String subCategoryId;
    private String categoryName;
    private String subCategoryName;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private String postType;
    private String title;
    private String content;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer viewCount;
    private Integer starCount;
    private Integer likeCount;
    private Integer commentCount;
    private String createTime;
}
