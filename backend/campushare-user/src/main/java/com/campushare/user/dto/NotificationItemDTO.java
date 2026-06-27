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
public class NotificationItemDTO {
    /** Item type: LIKE, STAR, FOLLOW, STRANGER_MSG, CONVERSATION */
    private String itemType;
    /** Display title */
    private String title;
    /** Latest content preview */
    private String preview;
    /** Count of unread items in this group */
    private int unreadCount;
    /** Total count of items in this group */
    private int totalCount;
    /** Latest activity time */
    private LocalDateTime latestTime;
    /** Whether this item is pinned */
    private boolean isPinned;
    /** For CONVERSATION: the other user's ID */
    private String otherUserId;
    /** For CONVERSATION: the other user's name */
    private String otherUserName;
    /** For CONVERSATION: the other user's avatar */
    private String otherUserAvatar;
}
