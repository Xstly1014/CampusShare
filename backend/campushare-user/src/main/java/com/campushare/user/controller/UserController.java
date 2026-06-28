package com.campushare.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.*;
import com.campushare.user.entity.Follow;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.FollowMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.PostService;
import com.campushare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final PostService postService;
    private final com.campushare.user.service.NotificationService notificationService;
    private final com.campushare.user.service.CreatorService creatorService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.getCurrentUser(userId);
        user.setCreator(creatorService.isCreator(userId));
        return Result.success(user);
    }

    /**
     * 更新当前用户资料（昵称、简介、头像）
     */
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

    // ================================================================
    // User search
    // ================================================================

    @GetMapping("/search")
    public Result<List<UserDTO>> searchUsers(
            @RequestHeader("Authorization") String token,
            @RequestParam String keyword) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getUsername, keyword)
                        .eq(User::getDeleted, false)
                        .ne(User::getId, currentUserId)
                        .last("LIMIT 20"));
        List<UserDTO> result = new ArrayList<>();
        for (User u : users) {
            UserDTO dto = new UserDTO();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setAvatarUrl(u.getAvatarUrl());
            dto.setBio(u.getBio());
            dto.setCreator(creatorService.isCreator(u.getId()));
            result.add(dto);
        }
        return Result.success(result);
    }

    // ================================================================
    // User profile (view other users)
    // ================================================================

    @GetMapping("/{userId}/profile")
    public Result<UserProfileDTO> getUserProfile(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted()) {
            return Result.success(null);
        }

        UserPostStats stats = postService.getMyPostStats(userId);
        long followerCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
        long followingCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        boolean isFollowing = followMapper.exists(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, currentUserId)
                        .eq(Follow::getFollowingId, userId));

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
                .isFollowing(isFollowing)
                .isSelf(currentUserId.equals(userId))
                .isCreator(creatorService.isCreator(userId))
                .build();
        return Result.success(dto);
    }

    @GetMapping("/{userId}/posts")
    public Result<List<PostListDTO>> getUserPosts(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<com.campushare.user.entity.Post> posts = postService.getMyPosts(userId, page, size);
        return Result.success(enrichPosts(posts));
    }

    @GetMapping("/{userId}/starred")
    public Result<List<PostListDTO>> getUserStarred(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<com.campushare.user.entity.Post> posts = postService.getStarredPosts(userId, page, size);
        return Result.success(enrichPosts(posts));
    }

    @GetMapping("/{userId}/liked")
    public Result<List<PostListDTO>> getUserLiked(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<com.campushare.user.entity.Post> posts = postService.getLikedPosts(userId, page, size);
        return Result.success(enrichPosts(posts));
    }

    @GetMapping("/{userId}/history")
    public Result<List<PostListDTO>> getUserHistory(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<com.campushare.user.entity.Post> posts = postService.getViewHistory(userId, page, size);
        return Result.success(enrichPosts(posts));
    }

    // ================================================================
    // Follow / Unfollow
    // ================================================================

    @PostMapping("/{userId}/follow")
    public Result<Boolean> toggleFollow(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId) {
        String currentUserId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        if (currentUserId.equals(userId)) {
            return Result.success(false);
        }
        Follow existing = followMapper.selectOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, currentUserId)
                        .eq(Follow::getFollowingId, userId));
        if (existing != null) {
            followMapper.deleteById(existing.getId());
            return Result.success(false);
        } else {
            Follow follow = Follow.builder()
                    .followerId(currentUserId)
                    .followingId(userId)
                    .build();
            followMapper.insert(follow);
            // Create follow notification
            notificationService.createNotification(userId, currentUserId, "FOLLOW", null, null);
            return Result.success(true);
        }
    }

    // ================================================================
    // Follow stats & lists (followers / following / mutual)
    // ================================================================

    @GetMapping("/me/follow-stats")
    public Result<Map<String, Long>> getFollowStats(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        long followingCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        long followerCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));

        // Mutual: users I follow who also follow me
        List<Follow> myFollowings = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        long mutualCount = 0;
        for (Follow f : myFollowings) {
            boolean mutual = followMapper.exists(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getFollowerId, f.getFollowingId())
                            .eq(Follow::getFollowingId, userId));
            if (mutual) mutualCount++;
        }

        Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("following", followingCount);
        stats.put("followers", followerCount);
        stats.put("mutual", mutualCount);
        return Result.success(stats);
    }

    @GetMapping("/me/following")
    public Result<List<UserDTO>> getFollowingList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        List<UserDTO> result = buildUserDTOsFromFollows(follows, true);
        return Result.success(result);
    }

    @GetMapping("/me/followers")
    public Result<List<UserDTO>> getFollowerList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
        List<UserDTO> result = buildUserDTOsFromFollows(follows, false);
        return Result.success(result);
    }

    @GetMapping("/me/mutual")
    public Result<List<UserDTO>> getMutualList(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Follow> myFollowings = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        List<UserDTO> result = new ArrayList<>();
        for (Follow f : myFollowings) {
            boolean mutual = followMapper.exists(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getFollowerId, f.getFollowingId())
                            .eq(Follow::getFollowingId, userId));
            if (mutual) {
                User u = userMapper.selectById(f.getFollowingId());
                if (u != null && !u.getDeleted()) {
                    UserDTO dto = new UserDTO();
                    dto.setId(u.getId());
                    dto.setUsername(u.getUsername());
                    dto.setAvatarUrl(u.getAvatarUrl());
                    dto.setBio(u.getBio());
                    dto.setCreator(creatorService.isCreator(u.getId()));
                    result.add(dto);
                }
            }
        }
        return Result.success(result);
    }

    private List<UserDTO> buildUserDTOsFromFollows(List<Follow> follows, boolean isFollowing) {
        List<UserDTO> result = new ArrayList<>();
        for (Follow f : follows) {
            String targetId = isFollowing ? f.getFollowingId() : f.getFollowerId();
            User u = userMapper.selectById(targetId);
            if (u != null && !u.getDeleted()) {
                UserDTO dto = new UserDTO();
                dto.setId(u.getId());
                dto.setUsername(u.getUsername());
                dto.setAvatarUrl(u.getAvatarUrl());
                dto.setBio(u.getBio());
                dto.setCreator(creatorService.isCreator(u.getId()));
                result.add(dto);
            }
        }
        return result;
    }

    // ================================================================
    // Helper
    // ================================================================

    private java.time.format.DateTimeFormatter FMT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private List<PostListDTO> enrichPosts(List<com.campushare.user.entity.Post> posts) {
        if (posts.isEmpty()) return new ArrayList<>();
        List<String> authorIds = new ArrayList<>();
        for (com.campushare.user.entity.Post p : posts) {
            if (!authorIds.contains(p.getAuthorId())) authorIds.add(p.getAuthorId());
        }
        List<User> authors = userMapper.selectBatchIds(authorIds);
        java.util.Map<String, User> authorMap = new java.util.HashMap<>();
        for (User u : authors) authorMap.put(u.getId(), u);

        List<PostListDTO> result = new ArrayList<>();
        for (com.campushare.user.entity.Post p : posts) {
            User author = authorMap.get(p.getAuthorId());
            PostListDTO dto = new PostListDTO();
            dto.setId(p.getId());
            dto.setSchoolId(p.getSchoolId());
            dto.setAuthorId(p.getAuthorId());
            dto.setAuthorName(author != null ? author.getUsername() : "未知用户");
            dto.setAuthorAvatar(author != null && author.getAvatarUrl() != null ? author.getAvatarUrl() : null);
            dto.setPostType(p.getPostType());
            dto.setTitle(p.getTitle());
            dto.setContent(p.getContent());
            dto.setFileUrl(p.getFileUrl());
            dto.setFileName(p.getFileName());
            dto.setFileType(p.getFileType());
            dto.setFileSize(p.getFileSize());
            dto.setViewCount(p.getViewCount());
            dto.setStarCount(p.getStarCount());
            dto.setLikeCount(p.getLikeCount());
            dto.setCommentCount(p.getCommentCount());
            dto.setCreateTime(p.getCreateTime() != null ? p.getCreateTime().format(FMT) : null);
            result.add(dto);
        }
        return result;
    }
}