package com.campushare.post.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.post.dto.*;
import com.campushare.post.entity.Category;
import com.campushare.post.entity.Post;
import com.campushare.post.entity.SubCategory;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.CategoryMapper;
import com.campushare.post.mapper.SubCategoryMapper;
import com.campushare.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtUtils jwtUtils;
    private final CategoryMapper categoryMapper;
    private final SubCategoryMapper subCategoryMapper;
    private final UserFeignClient userFeignClient;

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

        String authorName = "未知用户";
        String authorAvatar = null;
        try {
            List<UserFeignClient.UserSimpleInfo> users = userFeignClient.getBatchUserInfo(
                    Collections.singletonList(post.getAuthorId()));
            if (users != null && !users.isEmpty()) {
                UserFeignClient.UserSimpleInfo author = users.get(0);
                authorName = author.getUsername();
                authorAvatar = author.getAvatarUrl();
            }
        } catch (Exception e) {
        }

        Category cat = post.getCategoryId() != null ? categoryMapper.selectById(post.getCategoryId()) : null;
        SubCategory sub = post.getSubCategoryId() != null ? subCategoryMapper.selectById(post.getSubCategoryId()) : null;
        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(post.getId());
        dto.setSchoolId(post.getSchoolId());
        dto.setCategoryId(post.getCategoryId());
        dto.setSubCategoryId(post.getSubCategoryId());
        dto.setCategoryName(cat != null ? cat.getName() : null);
        dto.setSubCategoryName(sub != null ? sub.getName() : null);
        dto.setAuthorId(post.getAuthorId());
        dto.setAuthorName(authorName);
        dto.setAuthorAvatar(authorAvatar);
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

    @GetMapping("/user/{userId}/posts")
    public Result<List<PostListDTO>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Post> posts = postService.getMyPosts(userId, page, size);
        return Result.success(enrichWithAuthor(posts));
    }

    @GetMapping("/user/{userId}/stats")
    public Result<UserPostStats> getUserPostStats(
            @PathVariable String userId) {
        UserPostStats stats = postService.getUserPostStats(userId);
        return Result.success(stats);
    }

    private List<PostListDTO> enrichWithAuthor(List<Post> posts) {
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> authorIds = new HashSet<>();
        Set<String> catIds = new HashSet<>();
        Set<String> subCatIds = new HashSet<>();
        for (Post p : posts) {
            if (p.getAuthorId() != null) authorIds.add(p.getAuthorId());
            if (p.getCategoryId() != null) catIds.add(p.getCategoryId());
            if (p.getSubCategoryId() != null) subCatIds.add(p.getSubCategoryId());
        }

        Map<String, UserFeignClient.UserSimpleInfo> authorMap = new HashMap<>();
        try {
            List<UserFeignClient.UserSimpleInfo> authors = userFeignClient.getBatchUserInfo(new ArrayList<>(authorIds));
            if (authors != null) {
                for (UserFeignClient.UserSimpleInfo u : authors) {
                    authorMap.put(u.getId(), u);
                }
            }
        } catch (Exception e) {
        }

        Map<String, Category> categoryMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<Category> cats = categoryMapper.selectBatchIds(catIds);
            if (cats != null) {
                for (Category c : cats) {
                    categoryMap.put(c.getId(), c);
                }
            }
        }

        Map<String, SubCategory> subCategoryMap = new HashMap<>();
        if (!subCatIds.isEmpty()) {
            List<SubCategory> subs = subCategoryMapper.selectBatchIds(subCatIds);
            if (subs != null) {
                for (SubCategory s : subs) {
                    subCategoryMap.put(s.getId(), s);
                }
            }
        }

        List<PostListDTO> result = new ArrayList<>();
        for (Post p : posts) {
            UserFeignClient.UserSimpleInfo author = authorMap.get(p.getAuthorId());
            Category cat = p.getCategoryId() != null ? categoryMap.get(p.getCategoryId()) : null;
            SubCategory sub = p.getSubCategoryId() != null ? subCategoryMap.get(p.getSubCategoryId()) : null;
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
