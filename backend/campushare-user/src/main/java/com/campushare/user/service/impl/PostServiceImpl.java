package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.CreatePostRequest;
import com.campushare.user.dto.UserPostStats;
import com.campushare.user.entity.Post;
import com.campushare.user.entity.PostLike;
import com.campushare.user.entity.PostStar;
import com.campushare.user.entity.ViewHistory;
import com.campushare.user.mapper.PostLikeMapper;
import com.campushare.user.mapper.PostMapper;
import com.campushare.user.mapper.PostStarMapper;
import com.campushare.user.mapper.ViewHistoryMapper;
import com.campushare.user.service.NotificationService;
import com.campushare.user.service.PostService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostStarMapper postStarMapper;
    private final PostLikeMapper postLikeMapper;
    private final ViewHistoryMapper viewHistoryMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final NotificationService notificationService;

    private static final String REDIS_KEY_STAR = "post:star:";
    private static final String REDIS_KEY_LIKE = "post:like:";
    private static final int REDIS_CACHE_TTL_DAYS = 30;

    @Override
    @Transactional
    @Timed(value = "campushare.post.create.duration", description = "Time taken to create a post")
    public Post createPost(String userId, CreatePostRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessException(40001, "标题不能为空");
        }
        boolean hasSchool = request.getSchoolId() != null && !request.getSchoolId().trim().isEmpty();
        boolean hasCategory = request.getCategoryId() != null && !request.getCategoryId().trim().isEmpty();
        if (!hasSchool && !hasCategory) {
            throw new BusinessException(40002, "必须选择学校或分类");
        }

        Counter.builder("campushare.post.create.total")
                .tag("type", request.getPostType() != null ? request.getPostType() : "discussion")
                .tag("school", request.getSchoolId() != null ? request.getSchoolId() : "none")
                .register(meterRegistry)
                .increment();

        Post post = Post.builder()
                .schoolId(hasSchool ? request.getSchoolId() : null)
                .categoryId(hasCategory ? request.getCategoryId() : null)
                .subCategoryId(hasCategory ? request.getSubCategoryId() : null)
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
    @Transactional
    public Post editPost(String userId, String postId, CreatePostRequest request) {
        Post post = getPostById(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(4030, "无权编辑他人的帖子");
        }
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            post.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getFileUrl() != null) {
            post.setFileUrl(request.getFileUrl());
            post.setFileName(request.getFileName());
            post.setFileType(request.getFileType());
            post.setFileSize(request.getFileSize());
        }
        boolean hasSchool = request.getSchoolId() != null && !request.getSchoolId().trim().isEmpty();
        boolean hasCategory = request.getCategoryId() != null && !request.getCategoryId().trim().isEmpty();
        if (hasSchool) {
            post.setSchoolId(request.getSchoolId());
            post.setCategoryId(null);
            post.setSubCategoryId(null);
        } else if (hasCategory) {
            post.setSchoolId(null);
            post.setCategoryId(request.getCategoryId());
            post.setSubCategoryId(request.getSubCategoryId());
        }
        postMapper.updateById(post);
        return post;
    }

    @Override
    @Transactional
    public void deletePost(String userId, String postId) {
        Post post = getPostById(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(4030, "无权删除他人的帖子");
        }
        postMapper.deleteById(postId);
        log.info("用户 {} 删除帖子 {}", userId, postId);
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
                .isNull(Post::getCategoryId)
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
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public void incrementViewCount(String userId, String postId) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("view_count = view_count + 1"));

        if (userId != null && !userId.isEmpty()) {
            ViewHistory existing = viewHistoryMapper.selectOne(
                    new LambdaQueryWrapper<ViewHistory>()
                            .eq(ViewHistory::getPostId, postId)
                            .eq(ViewHistory::getUserId, userId));

            if (existing != null) {
                existing.setViewTime(LocalDateTime.now());
                viewHistoryMapper.updateById(existing);
            } else {
                ViewHistory vh = ViewHistory.builder()
                        .postId(postId)
                        .userId(userId)
                        .viewTime(LocalDateTime.now())
                        .build();
                viewHistoryMapper.insert(vh);
            }
        }
    }

    @Override
    @Transactional
    public boolean toggleStar(String userId, String postId) {
        Post post = getPostById(postId);
        String redisKey = REDIS_KEY_STAR + postId + ":" + userId;

        PostStar existing = postStarMapper.selectOne(
                new LambdaQueryWrapper<PostStar>()
                        .eq(PostStar::getPostId, postId)
                        .eq(PostStar::getUserId, userId));

        if (existing != null) {
            postStarMapper.deleteById(existing.getId());
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("star_count = GREATEST(0, star_count - 1)"));
            redisTemplate.delete(redisKey);
            log.info("用户 {} 取消收藏帖子 {}", userId, postId);
            return false;
        } else {
            PostStar star = PostStar.builder()
                    .postId(postId)
                    .userId(userId)
                    .createTime(LocalDateTime.now())
                    .build();
            postStarMapper.insert(star);
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("star_count = star_count + 1"));
            redisTemplate.opsForValue().set(redisKey, "1", REDIS_CACHE_TTL_DAYS, TimeUnit.DAYS);
            notificationService.createNotification(post.getAuthorId(), userId, "STAR", postId, post.getTitle());
            log.info("用户 {} 收藏帖子 {}", userId, postId);
            return true;
        }
    }

    @Override
    @Transactional
    public boolean toggleLike(String userId, String postId) {
        Post post = getPostById(postId);
        String redisKey = REDIS_KEY_LIKE + postId + ":" + userId;

        PostLike existing = postLikeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId));

        if (existing != null) {
            postLikeMapper.deleteById(existing.getId());
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
            redisTemplate.delete(redisKey);
            log.info("用户 {} 取消点赞帖子 {}", userId, postId);
            return false;
        } else {
            PostLike like = PostLike.builder()
                    .postId(postId)
                    .userId(userId)
                    .createTime(LocalDateTime.now())
                    .build();
            postLikeMapper.insert(like);
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = like_count + 1"));
            redisTemplate.opsForValue().set(redisKey, "1", REDIS_CACHE_TTL_DAYS, TimeUnit.DAYS);
            notificationService.createNotification(post.getAuthorId(), userId, "LIKE", postId, post.getTitle());
            log.info("用户 {} 点赞帖子 {}", userId, postId);
            return true;
        }
    }

    @Override
    public boolean isStarredBy(String userId, String postId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        String redisKey = REDIS_KEY_STAR + postId + ":" + userId;
        Boolean cached = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        Long count = postStarMapper.selectCount(
                new LambdaQueryWrapper<PostStar>()
                        .eq(PostStar::getPostId, postId)
                        .eq(PostStar::getUserId, userId));
        if (count != null && count > 0) {
            redisTemplate.opsForValue().set(redisKey, "1", REDIS_CACHE_TTL_DAYS, TimeUnit.DAYS);
            return true;
        }
        return false;
    }

    @Override
    public boolean isLikedBy(String userId, String postId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        String redisKey = REDIS_KEY_LIKE + postId + ":" + userId;
        Boolean cached = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        Long count = postLikeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId));
        if (count != null && count > 0) {
            redisTemplate.opsForValue().set(redisKey, "1", REDIS_CACHE_TTL_DAYS, TimeUnit.DAYS);
            return true;
        }
        return false;
    }

    @Override
    public List<Post> getViewHistory(String userId, int page, int size) {
        Page<ViewHistory> historyPage = viewHistoryMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ViewHistory>()
                        .eq(ViewHistory::getUserId, userId)
                        .orderByDesc(ViewHistory::getViewTime));

        if (historyPage.getRecords().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> postIds = new ArrayList<>();
        for (ViewHistory vh : historyPage.getRecords()) {
            postIds.add(vh.getPostId());
        }

        List<Post> posts = postMapper.selectBatchIds(postIds);
        List<Post> result = new ArrayList<>();
        for (String pid : postIds) {
            for (Post p : posts) {
                if (p.getId().equals(pid) && !p.getDeleted()) {
                    result.add(p);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<Post> getStarredPosts(String userId, int page, int size) {
        Page<PostStar> starPage = postStarMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<PostStar>()
                        .eq(PostStar::getUserId, userId)
                        .orderByDesc(PostStar::getCreateTime));

        if (starPage.getRecords().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> postIds = new ArrayList<>();
        for (PostStar ps : starPage.getRecords()) {
            postIds.add(ps.getPostId());
        }

        List<Post> posts = postMapper.selectBatchIds(postIds);
        List<Post> result = new ArrayList<>();
        for (String pid : postIds) {
            for (Post p : posts) {
                if (p.getId().equals(pid) && !p.getDeleted()) {
                    result.add(p);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<Post> getLikedPosts(String userId, int page, int size) {
        Page<PostLike> likePage = postLikeMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
                        .orderByDesc(PostLike::getCreateTime));

        if (likePage.getRecords().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> postIds = new ArrayList<>();
        for (PostLike pl : likePage.getRecords()) {
            postIds.add(pl.getPostId());
        }

        List<Post> posts = postMapper.selectBatchIds(postIds);
        List<Post> result = new ArrayList<>();
        for (String pid : postIds) {
            for (Post p : posts) {
                if (p.getId().equals(pid) && !p.getDeleted()) {
                    result.add(p);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<Post> getMyPosts(String userId, int page, int size) {
        Page<Post> postPage = postMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getAuthorId, userId)
                        .eq(Post::getDeleted, false)
                        .orderByDesc(Post::getCreateTime));
        return postPage.getRecords();
    }

    @Override
    public UserPostStats getMyPostStats(String userId) {
        List<Post> myPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getAuthorId, userId)
                        .eq(Post::getDeleted, false));

        long totalViews = 0;
        long totalLikes = 0;
        long totalStars = 0;
        for (Post p : myPosts) {
            if (p.getViewCount() != null) totalViews += p.getViewCount();
            if (p.getLikeCount() != null) totalLikes += p.getLikeCount();
            if (p.getStarCount() != null) totalStars += p.getStarCount();
        }
        return new UserPostStats(totalViews, totalLikes, totalStars, myPosts.size());
    }

    @Override
    public Map<String, Long> getSchoolPostCounts() {
        List<Post> allPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getDeleted, false)
                        .eq(Post::getStatus, 1)
                        .isNotNull(Post::getSchoolId)
                        .isNull(Post::getCategoryId));
        Map<String, Long> counts = new HashMap<>();
        for (Post p : allPosts) {
            counts.merge(p.getSchoolId(), 1L, Long::sum);
        }
        return counts;
    }
}
