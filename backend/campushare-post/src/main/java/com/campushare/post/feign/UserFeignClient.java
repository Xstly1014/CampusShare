package com.campushare.post.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service", url = "${service.user.url:http://localhost:8081}")
public interface UserFeignClient {

    @GetMapping("/internal/users/batch")
    List<UserSimpleInfo> getBatchUserInfo(@RequestParam("ids") List<String> ids);

    @PostMapping("/internal/users/notifications")
    void createNotification(@RequestBody NotificationRequest request);

    @PostMapping("/internal/users/batch-create-test")
    Map<String, Object> batchCreateTestUsers(@RequestBody Map<String, Object> request);

    @lombok.Data
    class UserSimpleInfo {
        private String id;
        private String username;
        private String avatarUrl;
        private String role;
    }

    @lombok.Data
    class NotificationRequest {
        private String userId;
        private String senderId;
        private String type;
        private String targetId;
        private String targetTitle;
    }
}
