package com.campushare.post.dto;

import lombok.Data;

@Data
public class CreatePostRequest {
    private String schoolId;
    private String categoryId;
    private String subCategoryId;
    private String postType;
    private String title;
    private String content;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private Long fileSize;
}
