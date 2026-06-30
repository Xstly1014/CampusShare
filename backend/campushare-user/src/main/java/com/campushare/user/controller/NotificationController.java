package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.NotificationDetailDTO;
import com.campushare.user.dto.NotificationItemDTO;
import com.campushare.user.dto.NotificationListDTO;
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

    @GetMapping("/list")
    public Result<List<NotificationListDTO>> getList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getNotificationList(userId));
    }

    @GetMapping("/feed")
    public Result<List<NotificationItemDTO>> getFeed(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getNotificationFeed(userId));
    }

    @GetMapping("/detail/{type}")
    public Result<List<NotificationDetailDTO>> getDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String type) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getNotificationDetail(userId, type));
    }

    @PostMapping("/read/{type}")
    public Result<Void> markAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable String type) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.markAsRead(userId, type);
        return Result.success(null);
    }

    @PostMapping("/read-single/{id}")
    public Result<Void> markSingleAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable Integer id) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.markSingleAsRead(userId, id);
        return Result.success(null);
    }

    @PostMapping("/read-aggregated")
    public Result<Void> markAggregatedAsRead(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.markAggregatedAsRead(userId, body.get("type"), body.get("targetId"));
        return Result.success(null);
    }

    @PostMapping("/read-all")
    public Result<Void> markAllAsRead(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    @PostMapping("/pin")
    public Result<Void> togglePin(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        notificationService.togglePin(userId, body.get("itemType"), body.get("targetId"));
        return Result.success(null);
    }

    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(notificationService.getUnreadCount(userId));
    }
}
