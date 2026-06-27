package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String receiverId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
