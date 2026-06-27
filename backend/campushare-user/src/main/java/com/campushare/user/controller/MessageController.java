package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.MessageDTO;
import com.campushare.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final JwtUtils jwtUtils;

    @PostMapping("/send")
    public Result<MessageDTO> sendMessage(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String senderId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        MessageDTO msg = messageService.sendMessage(senderId, body.get("receiverId"), body.get("content"));
        return Result.success(msg);
    }

    @GetMapping("/conversation/{otherUserId}")
    public Result<List<MessageDTO>> getConversation(
            @RequestHeader("Authorization") String token,
            @PathVariable String otherUserId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<MessageDTO> msgs = messageService.getConversation(userId, otherUserId);
        // Mark as read
        messageService.markAsRead(userId, otherUserId);
        return Result.success(msgs);
    }

    @GetMapping("/list")
    public Result<List<MessageDTO>> getConversationList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<MessageDTO> msgs = messageService.getConversationList(userId);
        return Result.success(msgs);
    }

    @GetMapping("/can-send/{otherUserId}")
    public Result<Boolean> canSend(
            @RequestHeader("Authorization") String token,
            @PathVariable String otherUserId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(messageService.canSendMessage(userId, otherUserId));
    }
}
