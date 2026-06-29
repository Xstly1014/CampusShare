package com.campushare.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.result.Result;
import com.campushare.post.cache.CategoryCache;
import com.campushare.post.dto.CategoryDTO;
import com.campushare.post.dto.PostListDTO;
import com.campushare.post.entity.Category;
import com.campushare.post.entity.Post;
import com.campushare.post.entity.SubCategory;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.PostMapper;
import com.campushare.post.service.CategoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryCache categoryCache;
    private final PostMapper postMapper;
    private final UserFeignClient userFeignClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CACHE_CATEGORY_COUNTS = "cache:category:post:counts";

    @GetMapping
    public Result<List<CategoryDTO>> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    @GetMapping("/{categoryId}")
    public Result<CategoryDTO> getCategoryDetail(@PathVariable String categoryId) {
        return Result.success(categoryService.getCategoryById(categoryId));
    }

    @GetMapping("/sub/{subCategoryId}/posts")
    public Result<List<PostListDTO>> getPostsBySubCategory(
            @PathVariable String subCategoryId,
            @RequestParam(defaultValue = "all") String postType,
            @RequestParam(defaultValue = "latest") String sortType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                        Post::getAuthorId, Post::getPostType, Post::getTitle,
                        Post::getViewCount, Post::getStarCount, Post::getLikeCount, Post::getCommentCount,
                        Post::getCreateTime)
                .eq(Post::getSubCategoryId, subCategoryId)
                .eq(Post::getStatus, 1)
                .eq(Post::getDeleted, false);

        if (postType != null && !"all".equals(postType)) {
            wrapper.eq(Post::getPostType, postType);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Post::getTitle, keyword));
        }

        if ("hottest".equals(sortType)) {
            wrapper.orderByDesc(Post::getStarCount, Post::getCreateTime);
        } else if ("active".equals(sortType)) {
            wrapper.orderByDesc(Post::getCommentCount, Post::getCreateTime);
        } else {
            wrapper.orderByDesc(Post::getCreateTime);
        }

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(enrichWithAuthor(pageResult.getRecords()));
    }

    @GetMapping("/{categoryId}/posts")
    public Result<List<PostListDTO>> getPostsByCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "all") String postType,
            @RequestParam(defaultValue = "latest") String sortType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                        Post::getAuthorId, Post::getPostType, Post::getTitle,
                        Post::getViewCount, Post::getStarCount, Post::getLikeCount, Post::getCommentCount,
                        Post::getCreateTime)
                .eq(Post::getCategoryId, categoryId)
                .eq(Post::getStatus, 1)
                .eq(Post::getDeleted, false);

        if (postType != null && !"all".equals(postType)) {
            wrapper.eq(Post::getPostType, postType);
        }

        if ("hottest".equals(sortType)) {
            wrapper.orderByDesc(Post::getStarCount, Post::getCreateTime);
        } else if ("active".equals(sortType)) {
            wrapper.orderByDesc(Post::getCommentCount, Post::getCreateTime);
        } else {
            wrapper.orderByDesc(Post::getCreateTime);
        }

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(enrichWithAuthor(pageResult.getRecords()));
    }

    @GetMapping("/counts")
    public Result<Map<String, Long>> getCategoryPostCounts() {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_CATEGORY_COUNTS);
            if (cached != null) {
                Map<String, Long> cachedMap = objectMapper.readValue(cached, new TypeReference<Map<String, Long>>() {});
                return Result.success(cachedMap);
            }
        } catch (Exception e) {
            log.warn("读取分类帖子数缓存失败: {}", e.getMessage());
        }

        Map<String, Long> counts = new HashMap<>();
        List<Map<String, Object>> catRows = postMapper.countGroupByCategory();
        for (Map<String, Object> row : catRows) {
            Object cid = row.get("categoryId");
            Object cnt = row.get("cnt");
            if (cid != null && cnt != null) {
                counts.put(cid.toString(), ((Number) cnt).longValue());
            }
        }
        List<Map<String, Object>> subRows = postMapper.countGroupBySubCategory();
        for (Map<String, Object> row : subRows) {
            Object sid = row.get("subCategoryId");
            Object cnt = row.get("cnt");
            if (sid != null && cnt != null) {
                counts.put("sub_" + sid, ((Number) cnt).longValue());
            }
        }

        try {
            redisTemplate.opsForValue().set(CACHE_CATEGORY_COUNTS, objectMapper.writeValueAsString(counts), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入分类帖子数缓存失败: {}", e.getMessage());
        }
        return Result.success(counts);
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
