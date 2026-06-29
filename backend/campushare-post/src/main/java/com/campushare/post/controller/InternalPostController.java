package com.campushare.post.controller;

import com.campushare.common.result.Result;
import com.campushare.post.cache.CategoryCache;
import com.campushare.post.dto.PostListDTO;
import com.campushare.post.dto.UserPostStats;
import com.campushare.post.entity.Category;
import com.campushare.post.entity.Post;
import com.campushare.post.entity.SubCategory;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {

    private final PostService postService;
    private final UserFeignClient userFeignClient;
    private final CategoryCache categoryCache;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/user/{userId}/posts")
    public Result<List<PostListDTO>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getMyPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/user/{userId}/starred")
    public Result<List<PostListDTO>> getUserStarred(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getStarredPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/user/{userId}/liked")
    public Result<List<PostListDTO>> getUserLiked(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getLikedPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/user/{userId}/history")
    public Result<List<PostListDTO>> getUserHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getViewHistory(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/user/{userId}/stats")
    public Result<UserPostStats> getUserStats(@PathVariable String userId) {
        return Result.success(postService.getUserPostStats(userId));
    }

    private List<PostListDTO> enrichWithAuthor(List<Post> posts) {
        int size = posts.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        Set<String> authorIds = new HashSet<>(size);
        for (Post p : posts) {
            if (p.getAuthorId() != null) {
                authorIds.add(p.getAuthorId());
            }
        }

        Map<String, UserFeignClient.UserSimpleInfo> authorMap = new HashMap<>(authorIds.size());
        if (!authorIds.isEmpty()) {
            try {
                List<UserFeignClient.UserSimpleInfo> authors = userFeignClient.getBatchUserInfo(new ArrayList<>(authorIds));
                if (authors != null) {
                    for (UserFeignClient.UserSimpleInfo u : authors) {
                        authorMap.put(u.getId(), u);
                    }
                }
            } catch (Exception e) {
            }
        }

        List<PostListDTO> result = new ArrayList<>(size);
        for (Post p : posts) {
            UserFeignClient.UserSimpleInfo author = authorMap.get(p.getAuthorId());
            Category cat = categoryCache.getCategory(p.getCategoryId());
            SubCategory sub = categoryCache.getSubCategory(p.getSubCategoryId());
            PostListDTO dto = new PostListDTO();
            dto.setId(p.getId());
            dto.setSchoolId(p.getSchoolId());
            dto.setCategoryId(p.getCategoryId());
            dto.setSubCategoryId(p.getSubCategoryId());
            dto.setCategoryName(cat != null ? cat.getName() : null);
            dto.setSubCategoryName(sub != null ? sub.getName() : null);
            dto.setAuthorId(p.getAuthorId());
            dto.setAuthorName(author != null ? author.getUsername() : "未知用户");
            dto.setAuthorAvatar(author != null ? author.getAvatarUrl() : null);
            dto.setPostType(p.getPostType());
            dto.setTitle(p.getTitle());
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
