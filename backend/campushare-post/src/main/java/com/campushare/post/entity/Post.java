package com.campushare.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("posts")
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String schoolId;

    private String categoryId;

    private String subCategoryId;

    private String authorId;

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

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;
}
