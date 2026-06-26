package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.CreatePostRequest;
import com.campushare.user.entity.Post;
import com.campushare.user.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtUtils jwtUtils;

    @PostMapping
    public Result<Post> createPost(
            @RequestHeader("Authorization") String token,
            @RequestBody CreatePostRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        Post post = postService.createPost(userId, request);
        return Result.success(post);
    }

    @GetMapping("/{postId}")
    public Result<Post> getPostDetail(@PathVariable String postId) {
        Post post = postService.getPostById(postId);
        postService.incrementViewCount(postId);
        return Result.success(post);
    }

    @GetMapping("/school/{schoolId}")
    public Result<List<Post>> getPostsBySchool(
            @PathVariable String schoolId,
            @RequestParam(defaultValue = "all") String postType,
            @RequestParam(defaultValue = "latest") String sortType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getPostsBySchool(schoolId, postType, sortType, page, size);
        return Result.success(posts);
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
}
