package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.user.entity.Notification;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.NotificationMapper;
import com.campushare.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;

    @GetMapping("/batch")
    public List<Map<String, String>> getBatchUserInfo(@RequestParam("ids") List<String> ids) {
        List<Map<String, String>> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        for (User user : users) {
            Map<String, String> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("avatarUrl", user.getAvatarUrl());
            result.add(map);
        }
        return result;
    }

    @PostMapping("/notifications")
    public Result<Void> createNotification(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String senderId = request.get("senderId");
        String type = request.get("type");
        String targetId = request.get("targetId");
        String targetTitle = request.get("targetTitle");

        Notification notification = Notification.builder()
                .userId(userId)
                .senderId(senderId)
                .type(type)
                .targetId(targetId)
                .targetTitle(targetTitle)
                .isRead(0)
                .build();
        notificationMapper.insert(notification);

        return Result.success(null);
    }
}
