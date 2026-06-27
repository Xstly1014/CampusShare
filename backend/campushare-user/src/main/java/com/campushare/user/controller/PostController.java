package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.*;
import com.campushare.user.entity.Post;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping
    public Result<Post> createPost(
            @RequestHeader("Authorization") String token,
            @RequestBody CreatePostRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        Post post = postService.createPost(userId, request);
        return Result.success(post);
    }

    @PutMapping("/{postId}")
    public Result<Post> editPost(
            @RequestHeader("Authorization") String token,
            @PathVariable String postId,
            @RequestBody CreatePostRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        Post post = postService.editPost(userId, postId, request);
        return Result.success(post);
    }

    @DeleteMapping("/{postId}")
    public Result<Void> deletePost(
            @RequestHeader("Authorization") String token,
            @PathVariable String postId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        postService.deletePost(userId, postId);
        return Result.success(null);
    }

    @GetMapping("/{postId}")
    public Result<PostDetailDTO> getPostDetail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String postId) {
        Post post = postService.getPostById(postId);
        String userId = extractUserId(token);
        postService.incrementViewCount(userId, postId);
        post.setViewCount(post.getViewCount() + 1);
        User author = userMapper.selectById(post.getAuthorId());
        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(post.getId());
        dto.setSchoolId(post.getSchoolId());
        dto.setAuthorId(post.getAuthorId());
        dto.setAuthorName(author != null ? author.getUsername() : "未知用户");
        dto.setAuthorAvatar(author != null && author.getAvatarUrl() != null ? author.getAvatarUrl() : null);
        dto.setPostType(post.getPostType());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setFileUrl(post.getFileUrl());
        dto.setFileName(post.getFileName());
        dto.setFileType(post.getFileType());
        dto.setFileSize(post.getFileSize());
        dto.setViewCount(post.getViewCount());
        dto.setStarCount(post.getStarCount());
        dto.setLikeCount(post.getLikeCount());
        dto.setCommentCount(post.getCommentCount());
        dto.setStatus(post.getStatus());
        dto.setCreateTime(post.getCreateTime() != null ? post.getCreateTime().format(FMT) : null);
        dto.setUpdateTime(post.getUpdateTime() != null ? post.getUpdateTime().format(FMT) : null);
        return Result.success(dto);
    }

    @GetMapping("/school-counts")
    public Result<Map<String, Long>> getSchoolPostCounts() {
        Map<String, Long> counts = postService.getSchoolPostCounts();
        return Result.success(counts);
    }

    @GetMapping("/school/{schoolId}")
    public Result<List<PostListDTO>> getPostsBySchool(
            @PathVariable String schoolId,
            @RequestParam(defaultValue = "all") String postType,
            @RequestParam(defaultValue = "latest") String sortType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getPostsBySchool(schoolId, postType, sortType, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/{postId}/status")
    public Result<PostStatus> getPostStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String postId) {
        String userId = extractUserId(token);
        PostStatus status = new PostStatus();
        if (userId != null && !userId.isEmpty()) {
            status.setStarred(postService.isStarredBy(userId, postId));
            status.setLiked(postService.isLikedBy(userId, postId));
        } else {
            status.setStarred(false);
            status.setLiked(false);
        }
        return Result.success(status);
    }

    @PostMapping("/{postId}/star")
    public Result<Boolean> toggleStar(
            @RequestHeader("Authorization") String token,
            @PathVariable String postId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        boolean isStarred = postService.toggleStar(userId, postId);
        return Result.success(isStarred);
    }

    @PostMapping("/{postId}/like")
    public Result<Boolean> toggleLike(
            @RequestHeader("Authorization") String token,
            @PathVariable String postId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        boolean isLiked = postService.toggleLike(userId, postId);
        return Result.success(isLiked);
    }

    // ================================================================
    // Personal homepage endpoints (require auth)
    // ================================================================

    @GetMapping("/history")
    public Result<List<PostListDTO>> getViewHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Post> posts = postService.getViewHistory(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/starred")
    public Result<List<PostListDTO>> getStarredPosts(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Post> posts = postService.getStarredPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/liked")
    public Result<List<PostListDTO>> getLikedPosts(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Post> posts = postService.getLikedPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/mine")
    public Result<List<PostListDTO>> getMyPosts(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<Post> posts = postService.getMyPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/my-stats")
    public Result<UserPostStats> getMyPostStats(
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserPostStats stats = postService.getMyPostStats(userId);
        return Result.success(stats);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /** Convert List<Post> to List<PostListDTO> with author name and avatar */
    private List<PostListDTO> enrichWithAuthor(List<Post> posts) {
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }
        // Batch query authors
        List<String> authorIds = new ArrayList<>();
        for (Post p : posts) {
            if (!authorIds.contains(p.getAuthorId())) {
                authorIds.add(p.getAuthorId());
            }
        }
        List<User> authors = userMapper.selectBatchIds(authorIds);
        Map<String, User> authorMap = new HashMap<>();
        for (User u : authors) {
            authorMap.put(u.getId(), u);
        }

        List<PostListDTO> result = new ArrayList<>();
        for (Post p : posts) {
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

    private String extractUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return jwtUtils.getUserId(token.replace("Bearer ", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
