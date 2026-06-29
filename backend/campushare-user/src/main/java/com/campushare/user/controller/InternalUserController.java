package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.user.entity.Notification;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.NotificationMapper;
import com.campushare.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final PasswordEncoder passwordEncoder;

    private static final String[] SURNAMES = {
            "王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴",
            "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗",
            "梁", "宋", "郑", "谢", "韩", "唐", "冯", "于", "董", "萧",
            "程", "曹", "袁", "邓", "许", "傅", "沈", "曾", "彭", "吕"
    };

    private static final String[] GIVEN_NAMES = {
            "伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军",
            "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞",
            "平", "刚", "桂英", "文", "华", "玲", "辉", "鑫", "斌", "波",
            "宇", "浩", "凯", "健", "俊", "帆", "鹏", "博", "婷", "雪",
            "倩", "琳", "欣", "悦", "璐", "瑶", "晨", "阳", "雨", "萱"
    };

    private static final String[] SCHOOL_IDS = {"1", "2", "3", "4", "5", "6", "7", "8"};

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
            map.put("role", user.getRole());
            map.put("creatorLevel", user.getCreatorLevel() != null ? user.getCreatorLevel() : "NONE");
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

    @PostMapping("/batch-create-test")
    public Result<List<Map<String, String>>> batchCreateTestUsers(@RequestBody Map<String, Object> request) {
        int count = (int) request.getOrDefault("count", 100);
        String fixedSchoolId = (String) request.get("schoolId");
        Random random = new Random();
        List<Map<String, String>> users = new ArrayList<>();
        String defaultPasswordHash = passwordEncoder.encode("123456");

        log.info("开始批量创建 {} 个测试用户, schoolId={}", count, fixedSchoolId != null ? fixedSchoolId : "random");

        for (int i = 0; i < count; i++) {
            String surname = SURNAMES[random.nextInt(SURNAMES.length)];
            String givenName = GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)];
            String username = surname + givenName + (random.nextInt(9000) + 1000);
            String schoolId = fixedSchoolId != null ? fixedSchoolId : SCHOOL_IDS[random.nextInt(SCHOOL_IDS.length)];
            String avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + UUID.randomUUID();

            String[] bios = {
                    "好好学习，天天向上",
                    "努力成为更好的自己",
                    "分享是一种快乐",
                    "大学生活精彩无限",
                    "求知若渴，虚心若愚",
                    "代码改变世界",
                    "热爱生活，热爱学习",
                    null, null, null
            };
            String bio = bios[random.nextInt(bios.length)];

            User user = User.builder()
                    .username(username)
                    .passwordHash(defaultPasswordHash)
                    .avatarUrl(avatarUrl)
                    .schoolId(schoolId)
                    .bio(bio)
                    .status(1)
                    .deleted(false)
                    .build();

            try {
                userMapper.insert(user);
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("schoolId", schoolId);
                users.add(userInfo);
            } catch (Exception e) {
                log.warn("创建用户失败: {}, 错误: {}", username, e.getMessage());
            }

            if ((i + 1) % 200 == 0) {
                log.info("已创建 {}/{} 个用户", i + 1, count);
            }
        }

        log.info("测试用户创建完成，成功 {} 个", users.size());
        return Result.success(users);
    }
}
