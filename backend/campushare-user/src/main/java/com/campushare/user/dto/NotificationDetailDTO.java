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
public class NotificationDetailDTO {
    private String id;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String type;
    private String targetId;
    private String targetTitle;
    private Integer isRead;
    private LocalDateTime createTime;
}
