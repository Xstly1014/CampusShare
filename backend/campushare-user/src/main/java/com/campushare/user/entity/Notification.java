package com.campushare.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notifications")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Recipient user ID (post author or person being followed) */
    private String userId;

    /** Sender user ID (who performed the action) */
    private String senderId;

    /** Notification type: LIKE, STAR, FOLLOW */
    private String type;

    /** Target post ID (for LIKE/STAR), null for FOLLOW */
    private String targetId;

    /** Target post title (for display) */
    private String targetTitle;

    /** Target post school id (for navigation) */
    private String schoolId;

    /** Target comment id (for COMMENT/REPLY/COMMENT_LIKE navigation) */
    private String commentId;

    /** Whether the notification has been read */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
