package com.campushare.user.service;

import com.campushare.user.dto.MessageDTO;

import java.util.List;

public interface MessageService {

    /**
     * Send a message. Restriction: if receiver hasn't followed sender and hasn't
     * replied, only 1 message allowed.
     */
    MessageDTO sendMessage(String senderId, String receiverId, String content);

    /** Get conversation between two users */
    List<MessageDTO> getConversation(String userId1, String userId2);

    /** Get conversation list (latest message per user) */
    List<MessageDTO> getConversationList(String userId);

    /** Check if sender can send message to receiver (not blocked by restriction) */
    boolean canSendMessage(String senderId, String receiverId);

    /** Mark all messages from otherUserId as read */
    void markAsRead(String userId, String otherUserId);

    /**
     * Hide conversation with otherUserId (messages remain but are hidden from this
     * user's list)
     */
    void hideConversation(String userId, String otherUserId);
}
