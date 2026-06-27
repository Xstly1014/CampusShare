package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;
import com.campushare.user.entity.Follow;
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

    @Override
    public void createNotification(String userId, String senderId, String type, String targetId, String targetTitle) {
        // Don't notify self
        if (userId.equals(senderId)) return;

        Notification notif = Notification.builder()
                .userId(userId)
                .senderId(senderId)
                .type(type)
                .targetId(targetId)
                .targetTitle(targetTitle)
                .isRead(0)
                .build();
        notificationMapper.insert(notif);
    }

    @Override
    public List<NotificationItemDTO> getNotificationFeed(String userId) {
        List<NotificationItemDTO> items = new ArrayList<>();

        // 1. Notification groups (LIKE, STAR, FOLLOW)
        for (String type : Arrays.asList("LIKE", "STAR", "FOLLOW")) {
            List<Notification> notifs = notificationMapper.selectList(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, userId)
                            .eq(Notification::getType, type)
                            .orderByDesc(Notification::getCreateTime));

            if (!notifs.isEmpty()) {
                Notification latest = notifs.get(0);
                int unread = (int) notifs.stream().filter(n -> n.getIsRead() == 0).count();

                String title = type.equals("LIKE") ? "赞" : type.equals("STAR") ? "收藏" : "新增粉丝";
                String preview = buildPreview(notifs, type);

                items.add(NotificationItemDTO.builder()
                        .itemType(type)
                        .title(title)
                        .preview(preview)
                        .unreadCount(unread)
                        .totalCount(notifs.size())
                        .latestTime(latest.getCreateTime())
                        .isPinned(false) // TODO: check pins table
                        .build());
            }
        }

        // 2. Message conversations
        List<Message> allMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .and(w -> w.eq(Message::getReceiverId, userId).or().eq(Message::getSenderId, userId))
                        .orderByDesc(Message::getCreateTime));

        // Group by other user
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

        // Stranger messages group
        if (!strangerConvs.isEmpty()) {
            List<Message> strangerMsgs = allMessages.stream()
                    .filter(m -> strangerConvs.contains(m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId()))
                    .filter(m -> m.getReceiverId().equals(userId)) // messages sent TO me
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

        // Ongoing conversations
        for (String otherId : ongoingConvs) {
            List<Message> msgs = conversationMap.get(otherId);
            Message latest = msgs.get(0); // already sorted desc
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

        // Sort: pinned first, then by latest time desc
        items.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            return b.getLatestTime().compareTo(a.getLatestTime());
        });

        return items;
    }

    @Override
    public List<NotificationDetailDTO> getNotificationDetail(String userId, String type) {
        List<Notification> notifs = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getType, type)
                        .orderByDesc(Notification::getCreateTime));

        List<NotificationDetailDTO> result = new ArrayList<>();
        for (Notification n : notifs) {
            User sender = userMapper.selectById(n.getSenderId());
            result.add(NotificationDetailDTO.builder()
                    .id(String.valueOf(n.getId()))
                    .senderId(n.getSenderId())
                    .senderName(sender != null ? sender.getUsername() : "未知用户")
                    .senderAvatar(sender != null ? sender.getAvatarUrl() : null)
                    .type(n.getType())
                    .targetId(n.getTargetId())
                    .targetTitle(n.getTargetTitle())
                    .isRead(n.getIsRead())
                    .createTime(n.getCreateTime())
                    .build());
        }
        return result;
    }

    @Override
    public void markAsRead(String userId, String type) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));

        // For STRANGER_MSG and CONVERSATION, also mark messages as read
        if ("STRANGER_MSG".equals(type) || "CONVERSATION".equals(type)) {
            // Mark all unread messages where I'm the receiver as read
            messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .eq(Message::getReceiverId, userId)
                    .eq(Message::getIsRead, 0)
                    .set(Message::getIsRead, 1));
        }
    }

    @Override
    public void togglePin(String userId, String itemType, String targetId) {
        // TODO: Implement with a pins table. For now, this is a no-op stub.
        log.info("Toggle pin: user={}, type={}, target={}", userId, itemType, targetId);
    }

    @Override
    public int getUnreadCount(String userId) {
        long notifUnread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        long msgUnread = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getReceiverId, userId)
                        .eq(Message::getIsRead, 0));
        return (int) (notifUnread + msgUnread);
    }

    private String buildPreview(List<Notification> notifs, String type) {
        Notification latest = notifs.get(0);
        User sender = userMapper.selectById(latest.getSenderId());
        String name = sender != null ? sender.getUsername() : "未知用户";

        if (notifs.size() == 1) {
            if ("LIKE".equals(type)) return name + " 赞了你的帖子";
            if ("STAR".equals(type)) return name + " 收藏了你的帖子";
            if ("FOLLOW".equals(type)) return name + " 关注了你";
        } else {
            if ("LIKE".equals(type)) return name + " 等" + notifs.size() + "人赞了你的帖子";
            if ("STAR".equals(type)) return name + " 等" + notifs.size() + "人收藏了你的帖子";
            if ("FOLLOW".equals(type)) return name + " 等" + notifs.size() + "人关注了你";
        }
        return "";
    }
}
