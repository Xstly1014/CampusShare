package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.*;
import com.campushare.user.entity.User;
import com.campushare.user.feign.PostFeignClient;
import com.campushare.user.feign.PostListDTO;
import com.campushare.user.feign.UserPostStats;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CreatorService;
import com.campushare.user.service.FollowService;
import com.campushare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final CreatorService creatorService;
    private final PostFeignClient postFeignClient;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.getCurrentUser(userId);
        user.setCreator(creatorService.isCreator(userId));
        user.setAdmin(creatorService.isAdmin(userId));
        return Result.success(user);
    }

    @PutMapping("/me")
    public Result<UserDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody UpdateProfileRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.updateProfile(userId, request);
        return Result.success(user);
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangePasswordRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        userService.changePassword(userId, request);
        return Result.success("密码修改成功", null);
    }

    @PutMapping("/me/email")
    public Result<UserDTO> bindEmail(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangeAccountRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.bindEmail(userId, request);
        return Result.success(user);
    }

    @PutMapping("/me/phone")
    public Result<UserDTO> bindPhone(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangeAccountRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.bindPhone(userId, request);
        return Result.success(user);
    }

    @PostMapping("/me/real-name-verify")
    public Result<Void> realNameVerify(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        userService.realNameVerify(userId, body.get("realName"), body.get("idCard"));
        return Result.success(null);
    }

    @GetMapping("/search")
    public Result<List<UserDTO>> searchUsers(
            @RequestHeader("Authorization") String token,
            @RequestParam String keyword) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<User> users = userService.searchUsers(keyword, currentUserId);
        List<UserDTO> result = new ArrayList<>();
        for (User u : users) {
            UserDTO dto = new UserDTO();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setAvatarUrl(u.getAvatarUrl());
            dto.setBio(u.getBio());
            dto.setCreator(creatorService.isCreator(u.getId()));
            dto.setAdmin(creatorService.isAdmin(u.getId()));
            result.add(dto);
        }
        return Result.success(result);
    }

    @GetMapping("/{userId}/profile")
    public Result<UserProfileDTO> getUserProfile(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted()) {
            return Result.success(null);
        }

        UserPostStats stats = postFeignClient.getUserStats(userId).getData();
        long followerCount = followService.getFollowerCount(userId);
        long followingCount = followService.getFollowingCount(userId);
        boolean isFollowing = followService.isFollowing(currentUserId, userId);

        UserProfileDTO dto = UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .postCount(stats.getPostCount())
                .totalViews(stats.getTotalViews())
                .totalLikes(stats.getTotalLikes())
                .totalStars(stats.getTotalStars())
                .followerCount(followerCount)
                .followingCount(followingCount)
                .following(isFollowing)
                .self(currentUserId.equals(userId))
                .creator(creatorService.isCreator(userId))
                .admin(creatorService.isAdmin(userId))
                .build();
        return Result.success(dto);
    }

    @GetMapping("/{userId}/posts")
    public Result<List<PostListDTO>> getUserPosts(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postFeignClient.getUserPosts(userId, page, size);
    }

    @GetMapping("/{userId}/starred")
    public Result<List<PostListDTO>> getUserStarred(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postFeignClient.getUserStarred(userId, page, size);
    }

    @GetMapping("/{userId}/liked")
    public Result<List<PostListDTO>> getUserLiked(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postFeignClient.getUserLiked(userId, page, size);
    }

    @GetMapping("/{userId}/history")
    public Result<List<PostListDTO>> getUserHistory(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postFeignClient.getUserHistory(userId, page, size);
    }

    @PostMapping("/{userId}/follow")
    public Result<Boolean> toggleFollow(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        boolean isFollowing = followService.toggleFollow(currentUserId, userId);
        return Result.success(isFollowing);
    }

    @GetMapping("/me/follow-stats")
    public Result<Map<String, Long>> getFollowStats(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(followService.getFollowStats(userId));
    }

    @GetMapping("/me/following")
    public Result<List<UserDTO>> getFollowingList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(followService.getFollowingList(userId));
    }

    @GetMapping("/me/followers")
    public Result<List<UserDTO>> getFollowerList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(followService.getFollowerList(userId));
    }

    @GetMapping("/me/mutual")
    public Result<List<UserDTO>> getMutualList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(followService.getMutualList(userId));
    }
}
