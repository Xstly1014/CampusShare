package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.MessageDTO;
import com.campushare.user.entity.Follow;
import com.campushare.user.entity.Message;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.FollowMapper;
import com.campushare.user.mapper.MessageMapper;
import com.campushare.user.service.MessageService;
import com.campushare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final UserService userService;
    private final FollowMapper followMapper;

    @Override
    @Transactional
    public MessageDTO sendMessage(String senderId, String receiverId, String content) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(4001, "不能给自己发私信");
        }

        User receiver = userService.getUserById(receiverId);
        if (receiver == null) {
            throw new BusinessException(4040, "接收者不存在");
        }

        if (!canSendMessage(senderId, receiverId)) {
            throw new BusinessException(4001, "你已发送过消息，请等待对方回复后再发送");
        }

        // Reset hidden flags for both sides when a new message is sent
        LambdaUpdateWrapper<Message> resetHidden = new LambdaUpdateWrapper<Message>()
                .and(w -> w.eq(Message::getSenderId, senderId).eq(Message::getReceiverId, receiverId))
                .or(w -> w.eq(Message::getSenderId, receiverId).eq(Message::getReceiverId, senderId))
                .set(Message::getSenderHidden, 0)
                .set(Message::getReceiverHidden, 0);
        update(resetHidden);

        Message msg = Message.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content)
                .isRead(0)
                .senderHidden(0)
                .receiverHidden(0)
                .build();
        save(msg);

        return convertToDTO(msg);
    }

    @Override
    public List<MessageDTO> getConversation(String userId1, String userId2) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .nested(w -> w.eq(Message::getSenderId, userId1)
                        .eq(Message::getReceiverId, userId2)
                        .eq(Message::getSenderHidden, 0))
                .or()
                .nested(w -> w.eq(Message::getSenderId, userId2)
                        .eq(Message::getReceiverId, userId1)
                        .eq(Message::getReceiverHidden, 0))
                .orderByAsc(Message::getCreateTime);

        List<Message> msgs = list(wrapper);
        return msgs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<MessageDTO> getConversationList(String userId) {
        LambdaQueryWrapper<Message> sentWrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, userId)
                .eq(Message::getSenderHidden, 0);
        LambdaQueryWrapper<Message> receivedWrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getReceiverHidden, 0);

        List<Message> sent = list(sentWrapper);
        List<Message> received = list(receivedWrapper);

        Map<String, Message> latestMap = new HashMap<>();
        for (Message m : sent) {
            String otherId = m.getReceiverId();
            Message existing = latestMap.get(otherId);
            if (existing == null || m.getCreateTime().isAfter(existing.getCreateTime())) {
                latestMap.put(otherId, m);
            }
        }
        for (Message m : received) {
            String otherId = m.getSenderId();
            Message existing = latestMap.get(otherId);
            if (existing == null || m.getCreateTime().isAfter(existing.getCreateTime())) {
                latestMap.put(otherId, m);
            }
        }

        List<Message> result = new ArrayList<>(latestMap.values());
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        return result.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public boolean canSendMessage(String senderId, String receiverId) {
        Follow receiverFollowSender = followMapper.selectOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, receiverId)
                        .eq(Follow::getFollowingId, senderId)
                        .last("LIMIT 1"));
        if (receiverFollowSender != null) {
            return true;
        }

        long receiverReplied = count(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, receiverId)
                .eq(Message::getReceiverId, senderId));
        if (receiverReplied > 0) {
            return true;
        }

        long sentCount = count(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, senderId)
                .eq(Message::getReceiverId, receiverId));
        return sentCount == 0;
    }

    @Override
    @Transactional
    public void markAsRead(String userId, String otherUserId) {
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1);
        update(wrapper);
    }

    @Override
    @Transactional
    public void hideConversation(String userId, String otherUserId) {
        // Mark messages sent by userId as sender_hidden
        LambdaUpdateWrapper<Message> sentWrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getSenderId, userId)
                .eq(Message::getReceiverId, otherUserId)
                .set(Message::getSenderHidden, 1);
        update(sentWrapper);

        // Mark messages received by userId as receiver_hidden
        LambdaUpdateWrapper<Message> receivedWrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getReceiverId, userId)
                .set(Message::getReceiverHidden, 1);
        update(receivedWrapper);
    }

    private MessageDTO convertToDTO(Message msg) {
        MessageDTO dto = new MessageDTO();
        BeanUtils.copyProperties(msg, dto);
        dto.setIsMine(msg.getSenderId().equals(getCurrentUserId()));

        User sender = userService.getUserById(msg.getSenderId());
        if (sender != null) {
            dto.setSenderName(sender.getUsername());
            dto.setSenderAvatar(sender.getAvatarUrl());
        }
        User receiver = userService.getUserById(msg.getReceiverId());
        if (receiver != null) {
            dto.setReceiverName(receiver.getUsername());
            dto.setReceiverAvatar(receiver.getAvatarUrl());
        }
        return dto;
    }

    private String getCurrentUserId() {
        return (String) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
