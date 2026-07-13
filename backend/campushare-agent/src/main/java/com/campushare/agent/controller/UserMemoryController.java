package com.campushare.agent.controller;

import com.campushare.common.result.Result;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.service.InferredBehaviorService;
import com.campushare.agent.service.LongTermMemoryService;
import com.campushare.agent.service.MemoryRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/memory")
@RequiredArgsConstructor
public class UserMemoryController {

    private final LongTermMemoryService longTermMemoryService;
    private final MemoryRetrievalService memoryRetrievalService;
    private final InferredBehaviorService inferredBehaviorService;

    @GetMapping("/user/{userId}")
    public Result<List<UserMemory>> getUserMemories(@PathVariable String userId) {
        try {
            List<UserMemory> memories = longTermMemoryService.getAllMemories(userId);
            return Result.success(memories);
        } catch (Exception e) {
            log.error("Failed to get user memories: userId={}", userId, e);
            return Result.error("获取记忆失败");
        }
    }

    @GetMapping("/user/{userId}/active")
    public Result<List<UserMemory>> getActiveMemories(@PathVariable String userId) {
        try {
            List<UserMemory> memories = longTermMemoryService.getActiveMemories(userId);
            return Result.success(memories);
        } catch (Exception e) {
            log.error("Failed to get active memories: userId={}", userId, e);
            return Result.error("获取活跃记忆失败");
        }
    }

    @GetMapping("/user/{userId}/search")
    public Result<List<UserMemory>> searchMemories(
            @PathVariable String userId,
            @RequestParam String query) {
        try {
            List<UserMemory> memories = longTermMemoryService.searchMemories(userId, query);
            return Result.success(memories);
        } catch (Exception e) {
            log.error("Failed to search memories: userId={}, query={}", userId, query, e);
            return Result.error("搜索记忆失败");
        }
    }

    @PostMapping("/user/{userId}")
    public Result<UserMemory> createMemory(
            @PathVariable String userId,
            @RequestBody MemoryCreateRequest request) {
        try {
            UserMemory memory = longTermMemoryService.upsertMemory(
                    userId,
                    request.getType(),
                    request.getKey(),
                    request.getValue(),
                    "EXPLICIT",
                    BigDecimal.ONE,
                    request.getEvidence()
            );
            if (memory != null) {
                return Result.success(memory);
            }
            return Result.error("创建记忆失败");
        } catch (Exception e) {
            log.error("Failed to create memory: userId={}, key={}", userId, request.getKey(), e);
            return Result.error("创建记忆失败");
        }
    }

    @PutMapping("/user/{userId}/{key}")
    public Result<UserMemory> updateMemory(
            @PathVariable String userId,
            @PathVariable String key,
            @RequestBody MemoryUpdateRequest request) {
        try {
            UserMemory memory = longTermMemoryService.upsertMemory(
                    userId,
                    request.getType() != null ? request.getType() : "PREFERENCE",
                    key,
                    request.getValue(),
                    "EXPLICIT",
                    BigDecimal.ONE,
                    request.getEvidence()
            );
            if (memory != null) {
                return Result.success(memory);
            }
            return Result.error("更新记忆失败");
        } catch (Exception e) {
            log.error("Failed to update memory: userId={}, key={}", userId, key, e);
            return Result.error("更新记忆失败");
        }
    }

    @DeleteMapping("/user/{userId}/{key}")
    public Result<Void> deleteMemory(
            @PathVariable String userId,
            @PathVariable String key) {
        try {
            longTermMemoryService.deleteMemory(userId, key);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete memory: userId={}, key={}", userId, key, e);
            return Result.error("删除记忆失败");
        }
    }

    @PostMapping("/user/{userId}/recover/{key}")
    public Result<UserMemory> recoverMemory(
            @PathVariable String userId,
            @PathVariable String key) {
        try {
            UserMemory memory = inferredBehaviorService.recoverMemory(userId, key);
            if (memory != null) {
                return Result.success(memory);
            }
            return Result.error("未找到可恢复的记忆");
        } catch (Exception e) {
            log.error("Failed to recover memory: userId={}, key={}", userId, key, e);
            return Result.error("恢复记忆失败");
        }
    }

    @GetMapping("/user/{userId}/profile")
    public Result<String> getUserProfile(@PathVariable String userId) {
        try {
            String profile = longTermMemoryService.loadUserProfile(userId, null, Map.of());
            return Result.success(profile);
        } catch (Exception e) {
            log.error("Failed to get user profile: userId={}", userId, e);
            return Result.error("获取用户画像失败");
        }
    }

    @lombok.Data
    public static class MemoryCreateRequest {
        private String type;
        private String key;
        private String value;
        private String evidence;
    }

    @lombok.Data
    public static class MemoryUpdateRequest {
        private String type;
        private String value;
        private String evidence;
    }
}
