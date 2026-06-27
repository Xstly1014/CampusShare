package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.MessageDTO;
import com.campushare.user.entity.Follow;
import com.campushare.user.entity.Message;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.FollowMapper;
import com.campushare.user.mapper.MessageMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;

    @Override
    public MessageDTO sendMessage(String senderId, String receiverId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(4001, "消息内容不能为空");
        }
        if (senderId.equals(receiverId)) {
            throw new BusinessException(4001, "不能给自己发私信");
        }

        // Check restriction
        if (!canSendMessage(senderId, receiverId)) {
            throw new BusinessException(4001, "对方未关注你或未回复你，暂时只能发送一条消息");
        }

        Message msg = Message.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content.trim())
                .isRead(0)
                .build();
        messageMapper.insert(msg);

        return buildMessageDTO(msg);
    }

    @Override
    public boolean canSendMessage(String senderId, String receiverId) {
        // If receiver follows sender, no restriction
        boolean receiverFollowsSender = followMapper.exists(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, receiverId)
                        .eq(Follow::getFollowingId, senderId));
        if (receiverFollowsSender) return true;

        // If receiver has ever replied to sender, no restriction
        boolean receiverReplied = messageMapper.exists(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSenderId, receiverId)
                        .eq(Message::getReceiverId, senderId));
        if (receiverReplied) return true;

        // Otherwise, check if sender already sent a message (only 1 allowed)
        long sentCount = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSenderId, senderId)
                        .eq(Message::getReceiverId, receiverId));
        return sentCount == 0;
    }

    @Override
    public List<MessageDTO> getConversation(String userId1, String userId2) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .and(w -> w
                                .and(w1 -> w1.eq(Message::getSenderId, userId1).eq(Message::getReceiverId, userId2))
                                .or(w2 -> w2.eq(Message::getSenderId, userId2).eq(Message::getReceiverId, userId1)))
                        .orderByAsc(Message::getCreateTime));

        List<MessageDTO> result = new ArrayList<>();
        for (Message m : messages) {
            result.add(buildMessageDTO(m));
        }
        return result;
    }

    @Override
    public List<MessageDTO> getConversationList(String userId) {
        // Get all messages involving this user
        List<Message> allMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .and(w -> w.eq(Message::getSenderId, userId).or().eq(Message::getReceiverId, userId))
                        .orderByDesc(Message::getCreateTime));

        // Group by the other user, keep latest message
        Map<String, Message> latestMap = new HashMap<>();
        for (Message m : allMessages) {
            String otherUserId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            if (!latestMap.containsKey(otherUserId)) {
                latestMap.put(otherUserId, m);
            }
        }

        List<MessageDTO> result = new ArrayList<>();
        for (Message m : latestMap.values()) {
            result.add(buildMessageDTO(m));
        }
        result.sort(Comparator.comparing(MessageDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public void markAsRead(String userId, String otherUserId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }

    private MessageDTO buildMessageDTO(Message msg) {
        User sender = userMapper.selectById(msg.getSenderId());
        return MessageDTO.builder()
                .id(msg.getId())
                .senderId(msg.getSenderId())
                .senderName(sender != null ? sender.getUsername() : "未知用户")
                .senderAvatar(sender != null && sender.getAvatarUrl() != null ? sender.getAvatarUrl() : null)
                .receiverId(msg.getReceiverId())
                .content(msg.getContent())
                .isRead(msg.getIsRead())
                .createTime(msg.getCreateTime())
                .build();
    }
}
