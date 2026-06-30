package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;
import com.campushare.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtils jwtUtils;

    /** Get unified notification feed (groups + conversations) */
    @GetMapping("/feed")
    public Result<List<NotificationItemDTO>> getFeed(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getNotificationFeed(userId));
    }

    /** Get detail list for a notification type group */
    @GetMapping("/detail/{type}")
    public Result<List<NotificationDetailDTO>> getDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String type) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getNotificationDetail(userId, type));
    }

    /** Mark all notifications of a type as read */
    @PostMapping("/read/{type}")
    public Result<Void> markAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable String type) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.markAsRead(userId, type);
        return Result.success(null);
    }

    /** Toggle pin status */
    @PostMapping("/pin")
    public Result<Void> togglePin(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.togglePin(userId, body.get("itemType"), body.get("targetId"));
        return Result.success(null);
    }

    /** Get total unread count */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getUnreadCount(userId));
    }
}
