package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;
import com.campushare.user.dto.NotificationListDTO;
import com.campushare.user.entity.Message;
import com.campushare.user.entity.Notification;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.FollowMapper;
import com.campushare.user.mapper.MessageMapper;
import com.campushare.user.mapper.NotificationMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;

    private static final Set<String> AGGREGATABLE_TYPES = Set.of("LIKE", "STAR", "COMMENT_LIKE");

    @Override
    public void createNotification(String userId, String senderId, String type, String targetId, String targetTitle) {
        createNotification(userId, senderId, type, targetId, targetTitle, null, null, null);
    }

    @Override
    public void createNotification(String userId, String senderId, String type, String targetId, String targetTitle,
                                   String schoolId, String categoryId, String commentId) {
        if (userId.equals(senderId)) return;

        try {
            Notification notif = Notification.builder()
                    .userId(userId)
                    .senderId(senderId)
                    .type(type)
                    .targetId(targetId)
                    .targetTitle(targetTitle)
                    .schoolId(schoolId)
                    .categoryId(categoryId)
                    .commentId(commentId)
                    .isRead(0)
                    .build();
            notificationMapper.insert(notif);
        } catch (Exception e) {
            log.warn("Failed to create notification: {}", e.getMessage());
        }
    }

    @Override
    public void createSystemNotification(String userId, String title, String content) {
        try {
            Notification notif = Notification.builder()
                    .userId(userId)
                    .senderId("system")
                    .type("SYSTEM")
                    .targetId(title)
                    .targetTitle(content)
                    .isRead(0)
                    .build();
            notificationMapper.insert(notif);
            log.info("系统通知已发送给用户 {}: {} - {}", userId, title, content);
        } catch (Exception e) {
            log.error("创建系统通知失败: userId={}, title={}, error={}", userId, title, e.getMessage(), e);
        }
    }

    @Override
    public List<NotificationListDTO> getNotificationList(String userId) {
        List<NotificationListDTO> result = new ArrayList<>();

        try {
            List<Notification> allNotifs = notificationMapper.selectList(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, userId)
                            .orderByDesc(Notification::getCreateTime)
                            .last("LIMIT 200"));

            Map<String, List<Notification>> groups = new LinkedHashMap<>();

            for (Notification n : allNotifs) {
                String key;
                if (AGGREGATABLE_TYPES.contains(n.getType()) && n.getTargetId() != null) {
                    key = "AGG:" + n.getType() + ":" + n.getTargetId();
                } else {
                    key = "SINGLE:" + n.getId();
                }
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
            }

            for (Map.Entry<String, List<Notification>> entry : groups.entrySet()) {
                List<Notification> groupNotifs = entry.getValue();
                Notification latest = groupNotifs.get(0);
                String type = latest.getType();
                boolean isSystem = "SYSTEM".equals(type) || "system".equals(latest.getSenderId());

                long unreadCount = groupNotifs.stream().filter(n -> n.getIsRead() == 0).count();
                boolean isAggregated = AGGREGATABLE_TYPES.contains(type) && groupNotifs.size() > 1;

                User latestSender = (!isSystem && latest.getSenderId() != null)
                        ? userMapper.selectById(latest.getSenderId()) : null;

                String senderName = isSystem ? "系统通知" :
                        (latestSender != null ? latestSender.getUsername() : "未知用户");
                String senderAvatar = isSystem ? null :
                        (latestSender != null ? latestSender.getAvatarUrl() : null);

                String content = buildListContent(groupNotifs, type, isAggregated);

                NotificationListDTO dto = NotificationListDTO.builder()
                        .id(String.valueOf(latest.getId()))
                        .type(type)
                        .senderId(latest.getSenderId())
                        .senderName(senderName)
                        .senderAvatar(senderAvatar)
                        .targetId(latest.getTargetId())
                        .targetTitle(latest.getTargetTitle())
                        .commentId(latest.getCommentId())
                        .schoolId(latest.getSchoolId())
                        .categoryId(latest.getCategoryId())
                        .content(content)
                        .isRead(unreadCount > 0 ? 0 : 1)
                        .createTime(latest.getCreateTime())
                        .aggregatedCount(isAggregated ? groupNotifs.size() : 1)
                        .build();

                if (isAggregated) {
                    List<NotificationListDTO.SenderInfo> senders = new ArrayList<>();
                    Set<String> addedSenderIds = new HashSet<>();
                    for (Notification n : groupNotifs) {
                        if (n.getSenderId() != null && addedSenderIds.add(n.getSenderId())) {
                            User u = userMapper.selectById(n.getSenderId());
                            if (u != null) {
                                senders.add(NotificationListDTO.SenderInfo.builder()
                                        .userId(u.getId())
                                        .username(u.getUsername())
                                        .avatarUrl(u.getAvatarUrl())
                                        .build());
                            }
                            if (senders.size() >= 5) break;
                        }
                    }
                    dto.setAggregatedSenders(senders);
                }

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Failed to load notification list: {}", e.getMessage());
        }

        return result;
    }

    private String buildListContent(List<Notification> notifs, String type, boolean isAggregated) {
        Notification latest = notifs.get(0);
        int count = notifs.size();

        if ("SYSTEM".equals(type)) {
            return latest.getTargetTitle() != null ? latest.getTargetTitle() : "";
        }

        User sender = userMapper.selectById(latest.getSenderId());
        String name = (sender != null) ? sender.getUsername() : "未知用户";

        if ("FOLLOW".equals(type)) {
            return name + " 关注了你";
        }

        String title = latest.getTargetTitle() != null ? "《" + latest.getTargetTitle() + "》" : "";

        switch (type) {
            case "LIKE":
                if (isAggregated) return name + " 等" + count + "人赞了你的帖子" + title;
                return name + " 赞了你的帖子" + title;
            case "STAR":
                if (isAggregated) return name + " 等" + count + "人收藏了你的帖子" + title;
                return name + " 收藏了你的帖子" + title;
            case "COMMENT_LIKE":
                if (isAggregated) return name + " 等" + count + "人赞了你的评论" + title;
                return name + " 赞了你的评论" + title;
            case "COMMENT":
                return name + " 评论了你的帖子" + title;
            case "REPLY":
                return name + " 回复了你的评论" + title;
            default:
                return name + " " + type;
        }
    }

    @Override
    public List<NotificationItemDTO> getNotificationFeed(String userId) {
        List<NotificationItemDTO> items = new ArrayList<>();

        try {
            for (String type : Arrays.asList("SYSTEM", "LIKE", "COMMENT_LIKE", "STAR", "FOLLOW", "COMMENT", "REPLY")) {
                List<Notification> notifs = notificationMapper.selectList(
                        new LambdaQueryWrapper<Notification>()
                                .eq(Notification::getUserId, userId)
                                .eq(Notification::getType, type)
                                .orderByDesc(Notification::getCreateTime));

                if (!notifs.isEmpty()) {
                    Notification latest = notifs.get(0);
                    int unread = (int) notifs.stream().filter(n -> n.getIsRead() == 0).count();

                    String title;
                    switch (type) {
                        case "SYSTEM": title = "系统通知"; break;
                        case "LIKE": title = "赞"; break;
                        case "COMMENT_LIKE": title = "评论获赞"; break;
                        case "STAR": title = "收藏"; break;
                        case "FOLLOW": title = "新增粉丝"; break;
                        case "COMMENT": title = "评论"; break;
                        case "REPLY": title = "回复"; break;
                        default: title = type;
                    }
                    String preview = buildPreview(notifs, type);

                    items.add(NotificationItemDTO.builder()
                            .itemType(type)
                            .title(title)
                            .preview(preview)
                            .unreadCount(unread)
                            .totalCount(notifs.size())
                            .latestTime(latest.getCreateTime())
                            .isPinned(false)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load notification groups: {}", e.getMessage());
        }

        try {
            List<Message> allMessages = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .and(w -> w.eq(Message::getReceiverId, userId).eq(Message::getReceiverHidden, 0)
                                    .or(w2 -> w2.eq(Message::getSenderId, userId).eq(Message::getSenderHidden, 0)))
                            .orderByDesc(Message::getCreateTime));

            Map<String, List<Message>> conversationMap = new LinkedHashMap<>();
            for (Message m : allMessages) {
                String otherId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
                conversationMap.computeIfAbsent(otherId, k -> new ArrayList<>()).add(m);
            }

            List<String> strangerConvs = new ArrayList<>();
            List<String> ongoingConvs = new ArrayList<>();

            for (Map.Entry<String, List<Message>> entry : conversationMap.entrySet()) {
                String otherId = entry.getKey();
                List<Message> msgs = entry.getValue();
                boolean hasSentToMe = msgs.stream().anyMatch(m -> m.getReceiverId().equals(userId));
                boolean hasSentFromMe = msgs.stream().anyMatch(m -> m.getSenderId().equals(userId));

                if (hasSentToMe && hasSentFromMe) {
                    ongoingConvs.add(otherId);
                } else {
                    strangerConvs.add(otherId);
                }
            }

            if (!strangerConvs.isEmpty()) {
                List<Message> strangerMsgs = allMessages.stream()
                        .filter(m -> strangerConvs
                                .contains(m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId()))
                        .filter(m -> m.getReceiverId().equals(userId))
                        .collect(Collectors.toList());

                if (!strangerMsgs.isEmpty()) {
                    int unread = (int) strangerMsgs.stream().filter(m -> m.getIsRead() == 0).count();
                    Message latest = strangerMsgs.get(0);
                    User sender = userMapper.selectById(latest.getSenderId());

                    items.add(NotificationItemDTO.builder()
                            .itemType("STRANGER_MSG")
                            .title("陌生人私信")
                            .preview(sender != null ? sender.getUsername() + ": " + latest.getContent() : latest.getContent())
                            .unreadCount(unread)
                            .totalCount(strangerMsgs.size())
                            .latestTime(latest.getCreateTime())
                            .isPinned(false)
                            .build());
                }
            }

            for (String otherId : ongoingConvs) {
                List<Message> msgs = conversationMap.get(otherId);
                Message latest = msgs.get(0);
                int unread = (int) msgs.stream().filter(m -> m.getReceiverId().equals(userId) && m.getIsRead() == 0).count();
                User other = userMapper.selectById(otherId);

                items.add(NotificationItemDTO.builder()
                        .itemType("CONVERSATION")
                        .title(other != null ? other.getUsername() : "未知用户")
                        .preview((latest.getSenderId().equals(userId) ? "我: " : "") + latest.getContent())
                        .unreadCount(unread)
                        .totalCount(msgs.size())
                        .latestTime(latest.getCreateTime())
                        .isPinned(false)
                        .otherUserId(otherId)
                        .otherUserName(other != null ? other.getUsername() : null)
                        .otherUserAvatar(other != null ? other.getAvatarUrl() : null)
                        .build());
            }

        } catch (Exception e) {
            log.warn("Failed to load message conversations: {}", e.getMessage());
        }

        items.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            return b.getLatestTime().compareTo(a.getLatestTime());
        });

        return items;
    }

    @Override
    public List<NotificationDetailDTO> getNotificationDetail(String userId, String type) {
        try {
            List<Notification> notifs = notificationMapper.selectList(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, userId)
                            .eq(Notification::getType, type)
                            .orderByDesc(Notification::getCreateTime));

            List<NotificationDetailDTO> result = new ArrayList<>();
            for (Notification n : notifs) {
                boolean isSystem = "SYSTEM".equals(type) || "system".equals(n.getSenderId());
                User sender = (!isSystem && n.getSenderId() != null) ? userMapper.selectById(n.getSenderId()) : null;
                String senderName;
                String senderAvatar;
                if (isSystem) {
                    senderName = "系统通知";
                    senderAvatar = null;
                } else {
                    senderName = sender != null ? sender.getUsername() : "系统通知";
                    senderAvatar = sender != null ? sender.getAvatarUrl() : null;
                }
                result.add(NotificationDetailDTO.builder()
                        .id(String.valueOf(n.getId()))
                        .senderId(n.getSenderId())
                        .senderName(senderName)
                        .senderAvatar(senderAvatar)
                        .type(n.getType())
                        .targetId(n.getTargetId())
                        .targetTitle(n.getTargetTitle())
                        .schoolId(n.getSchoolId())
                        .categoryId(n.getCategoryId())
                        .commentId(n.getCommentId())
                        .isRead(n.getIsRead())
                        .createTime(n.getCreateTime())
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load notification detail: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void markAsRead(String userId, String type) {
        try {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getUserId, userId)
                    .eq(Notification::getType, type)
                    .eq(Notification::getIsRead, 0)
                    .set(Notification::getIsRead, 1));

            if ("STRANGER_MSG".equals(type) || "CONVERSATION".equals(type)) {
                messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                        .eq(Message::getReceiverId, userId)
                        .eq(Message::getIsRead, 0)
                        .set(Message::getIsRead, 1));
            }
        } catch (Exception e) {
            log.warn("Failed to mark notifications as read: {}", e.getMessage());
        }
    }

    @Override
    public void markSingleAsRead(String userId, Integer notificationId) {
        try {
            Notification notif = notificationMapper.selectById(notificationId);
            if (notif == null || !notif.getUserId().equals(userId)) return;

            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, notificationId)
                    .eq(Notification::getIsRead, 0)
                    .set(Notification::getIsRead, 1));
        } catch (Exception e) {
            log.warn("Failed to mark notification as read: {}", e.getMessage());
        }
    }

    @Override
    public void markAggregatedAsRead(String userId, String type, String targetId) {
        try {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getUserId, userId)
                    .eq(Notification::getType, type)
                    .eq(Notification::getTargetId, targetId)
                    .eq(Notification::getIsRead, 0)
                    .set(Notification::getIsRead, 1));
        } catch (Exception e) {
            log.warn("Failed to mark aggregated notifications as read: {}", e.getMessage());
        }
    }

    @Override
    public void markAllAsRead(String userId) {
        try {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getUserId, userId)
                    .eq(Notification::getIsRead, 0)
                    .set(Notification::getIsRead, 1));

            messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .eq(Message::getReceiverId, userId)
                    .eq(Message::getIsRead, 0)
                    .set(Message::getIsRead, 1));
        } catch (Exception e) {
            log.warn("Failed to mark all as read: {}", e.getMessage());
        }
    }

    @Override
    public void togglePin(String userId, String itemType, String targetId) {
        log.info("Toggle pin: user={}, type={}, target={}", userId, itemType, targetId);
    }

    @Override
    public int getUnreadCount(String userId) {
        try {
            long notifUnread = notificationMapper.selectCount(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, userId)
                            .eq(Notification::getIsRead, 0));
            long msgUnread = messageMapper.selectCount(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getReceiverId, userId)
                            .eq(Message::getReceiverHidden, 0)
                            .eq(Message::getIsRead, 0));
            return (int) (notifUnread + msgUnread);
        } catch (Exception e) {
            log.warn("Failed to get unread count: {}", e.getMessage());
            return 0;
        }
    }

    private String buildPreview(List<Notification> notifs, String type) {
        Notification latest = notifs.get(0);

        if ("SYSTEM".equals(type)) {
            String title = latest.getTargetId() != null ? latest.getTargetId() : "系统通知";
            String content = latest.getTargetTitle() != null ? latest.getTargetTitle() : "";
            if (notifs.size() == 1) {
                return title + "：" + content;
            } else {
                return title + "：" + content + " 等" + notifs.size() + "条通知";
            }
        }

        User sender = userMapper.selectById(latest.getSenderId());
        String name = (sender != null) ? sender.getUsername() : "系统通知";

        if (notifs.size() == 1) {
            if ("LIKE".equals(type)) return name + " 赞了你的帖子";
            if ("COMMENT_LIKE".equals(type)) return name + " 赞了你的评论";
            if ("STAR".equals(type)) return name + " 收藏了你的帖子";
            if ("FOLLOW".equals(type)) return name + " 关注了你";
            if ("COMMENT".equals(type)) return name + " 评论了你的帖子";
            if ("REPLY".equals(type)) return name + " 回复了你的评论";
        } else {
            if ("LIKE".equals(type)) return name + " 等" + notifs.size() + "人赞了你的帖子";
            if ("COMMENT_LIKE".equals(type)) return name + " 等" + notifs.size() + "人赞了你的评论";
            if ("STAR".equals(type)) return name + " 等" + notifs.size() + "人收藏了你的帖子";
            if ("FOLLOW".equals(type)) return name + " 等" + notifs.size() + "人关注了你";
            if ("COMMENT".equals(type)) return name + " 等" + notifs.size() + "人评论了你的帖子";
            if ("REPLY".equals(type)) return name + " 等" + notifs.size() + "人回复了你的评论";
        }
        return "";
    }
}
