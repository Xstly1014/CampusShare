package com.campushare.user.service;

import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;

import java.util.List;

public interface NotificationService {

    /** Create a notification (for likes/stars/follows) */
    void createNotification(String userId, String senderId, String type, String targetId, String targetTitle);

    /** Create a system notification */
    void createSystemNotification(String userId, String title, String content);

    /** Get unified notification feed (groups + conversations), sorted by pinned then latest time */
    List<NotificationItemDTO> getNotificationFeed(String userId);

    /** Get detail list for a specific notification type group */
    List<NotificationDetailDTO> getNotificationDetail(String userId, String type);

    /** Mark all notifications of a type as read */
    void markAsRead(String userId, String type);

    /** Toggle pin status for a notification group or conversation */
    void togglePin(String userId, String itemType, String targetId);

    /** Get total unread count (notifications + unread messages) */
    int getUnreadCount(String userId);
}
