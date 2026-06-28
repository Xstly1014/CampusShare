package com.campushare.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.post.dto.CategoryDTO;
import com.campushare.post.dto.PostListDTO;
import com.campushare.post.entity.Category;
import com.campushare.post.entity.Post;
import com.campushare.post.entity.SubCategory;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.CategoryMapper;
import com.campushare.post.mapper.PostMapper;
import com.campushare.post.mapper.SubCategoryMapper;
import com.campushare.post.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;
    private final SubCategoryMapper subCategoryMapper;
    private final PostMapper postMapper;
    private final UserFeignClient userFeignClient;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        wrapper.eq(Post::getSubCategoryId, subCategoryId)
                .eq(Post::getStatus, 1)
                .eq(Post::getDeleted, false);

        if (postType != null && !"all".equals(postType)) {
            wrapper.eq(Post::getPostType, postType);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword));
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
        wrapper.eq(Post::getCategoryId, categoryId)
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
        List<Post> allPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getDeleted, false)
                        .eq(Post::getStatus, 1));
        Map<String, Long> counts = new HashMap<>();
        for (Post p : allPosts) {
            if (p.getCategoryId() != null) {
                counts.merge(p.getCategoryId(), 1L, Long::sum);
            }
            if (p.getSubCategoryId() != null) {
                counts.merge("sub_" + p.getSubCategoryId(), 1L, Long::sum);
            }
        }
        return Result.success(counts);
    }

    private List<PostListDTO> enrichWithAuthor(List<Post> posts) {
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> authorIds = new ArrayList<>();
        for (Post p : posts) {
            if (!authorIds.contains(p.getAuthorId())) {
                authorIds.add(p.getAuthorId());
            }
        }

        Map<String, UserFeignClient.UserSimpleInfo> authorMap = new HashMap<>();
        try {
            List<UserFeignClient.UserSimpleInfo> authors = userFeignClient.getBatchUserInfo(authorIds);
            if (authors != null) {
                for (UserFeignClient.UserSimpleInfo u : authors) {
                    authorMap.put(u.getId(), u);
                }
            }
        } catch (Exception e) {
        }

        List<PostListDTO> result = new ArrayList<>();
        for (Post p : posts) {
            UserFeignClient.UserSimpleInfo author = authorMap.get(p.getAuthorId());
            Category cat = p.getCategoryId() != null ? categoryMapper.selectById(p.getCategoryId()) : null;
            SubCategory sub = p.getSubCategoryId() != null ? subCategoryMapper.selectById(p.getSubCategoryId()) : null;
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
}
