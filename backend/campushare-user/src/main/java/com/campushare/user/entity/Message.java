package com.campushare.user.entity;

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
@TableName("messages")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String senderId;

    private String receiverId;

    private String content;

    private Integer isRead;

    private Integer senderHidden;

    private Integer receiverHidden;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
