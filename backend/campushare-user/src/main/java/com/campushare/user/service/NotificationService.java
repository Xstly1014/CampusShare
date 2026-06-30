package com.campushare.user.service;

import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;
import com.campushare.user.dto.NotificationListDTO;

import java.util.List;

public interface NotificationService {

    void createNotification(String userId, String senderId, String type, String targetId, String targetTitle);

    void createNotification(String userId, String senderId, String type, String targetId, String targetTitle,
                            String schoolId, String categoryId, String commentId);

    void createSystemNotification(String userId, String title, String content);

    List<NotificationListDTO> getNotificationList(String userId);

    List<NotificationItemDTO> getNotificationFeed(String userId);

    List<NotificationDetailDTO> getNotificationDetail(String userId, String type);

    void markAsRead(String userId, String type);

    void markSingleAsRead(String userId, Integer notificationId);

    void markAllAsRead(String userId);

    void markAggregatedAsRead(String userId, String type, String targetId);

    void togglePin(String userId, String itemType, String targetId);

    int getUnreadCount(String userId);
}
