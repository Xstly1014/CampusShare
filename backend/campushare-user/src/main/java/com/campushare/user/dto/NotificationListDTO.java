package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListDTO {
    private String id;
    private String type;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private List<SenderInfo> aggregatedSenders;
    private int aggregatedCount;
    private String targetId;
    private String targetTitle;
    private String commentId;
    private String schoolId;
    private String categoryId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderInfo {
        private String userId;
        private String username;
        private String avatarUrl;
    }
}
