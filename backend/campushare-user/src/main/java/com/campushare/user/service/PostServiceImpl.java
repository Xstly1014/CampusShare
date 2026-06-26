package com.campushare.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.CreatePostRequest;
import com.campushare.user.entity.Post;
import com.campushare.user.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    public Post createPost(String userId, CreatePostRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessException(40001, "标题不能为空");
        }
        if (request.getSchoolId() == null) {
            throw new BusinessException(40002, "学校ID不能为空");
        }

        Post post = Post.builder()
                .schoolId(request.getSchoolId())
                .authorId(userId)
                .postType(request.getPostType() != null ? request.getPostType() : "discussion")
                .title(request.getTitle().trim())
                .content(request.getContent())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileType(request.getFileType())
                .fileSize(request.getFileSize())
                .viewCount(0)
                .starCount(0)
                .likeCount(0)
                .commentCount(0)
                .status(1)
                .build();

        postMapper.insert(post);
        return post;
    }

    @Override
    public Post getPostById(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted()) {
            throw new BusinessException(40004, "帖子不存在");
        }
        return post;
    }

    @Override
    public List<Post> getPostsBySchool(String schoolId, String postType, String sortType, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getSchoolId, schoolId)
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
        return pageResult.getRecords();
    }

    @Override
    @Transactional
    public void incrementViewCount(String postId) {
        String key = "post:view:" + postId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count % 10 == 0) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                post.setViewCount(post.getViewCount() + count.intValue());
                postMapper.updateById(post);
            }
            redisTemplate.delete(key);
        } else {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
    }

    @Override
    @Transactional
    public boolean toggleStar(String userId, String postId) {
        String key = "post:star:" + postId + ":" + userId;
        Boolean hasKey = redisTemplate.hasKey(key);

        Post post = getPostById(postId);
        boolean isStarred = false;

        if (Boolean.TRUE.equals(hasKey)) {
            redisTemplate.delete(key);
            post.setStarCount(Math.max(0, post.getStarCount() - 1));
        } else {
            redisTemplate.opsForValue().set(key, "1", 7, TimeUnit.DAYS);
            post.setStarCount(post.getStarCount() + 1);
            isStarred = true;
        }

        postMapper.updateById(post);
        return isStarred;
    }

    @Override
    @Transactional
    public boolean toggleLike(String userId, String postId) {
        String key = "post:like:" + postId + ":" + userId;
        Boolean hasKey = redisTemplate.hasKey(key);

        Post post = getPostById(postId);
        boolean isLiked = false;

        if (Boolean.TRUE.equals(hasKey)) {
            redisTemplate.delete(key);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            redisTemplate.opsForValue().set(key, "1", 7, TimeUnit.DAYS);
            post.setLikeCount(post.getLikeCount() + 1);
            isLiked = true;
        }

        postMapper.updateById(post);
        return isLiked;
    }
}
