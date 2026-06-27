package com.campushare.user.service;

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
import java.util.List;
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

    // Redis key patterns
    private static final String REDIS_KEY_STAR = "post:star:";
    private static final String REDIS_KEY_LIKE = "post:like:";
    private static final int REDIS_CACHE_TTL_DAYS = 30;

    // ================================================================
    // Create post
    // ================================================================
    @Override
    @Transactional
    @Timed(value = "campushare.post.create.duration", description = "Time taken to create a post")
    public Post createPost(String userId, CreatePostRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessException(40001, "标题不能为空");
        }
        if (request.getSchoolId() == null) {
            throw new BusinessException(40002, "学校ID不能为空");
        }

        Counter.builder("campushare.post.create.total")
                .tag("type", request.getPostType() != null ? request.getPostType() : "discussion")
                .tag("school", request.getSchoolId())
                .register(meterRegistry)
                .increment();

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

    // ================================================================
    // Edit / Delete post
    // ================================================================
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
        // Logical delete
        postMapper.deleteById(postId);
        log.info("用户 {} 删除帖子 {}", userId, postId);
    }

    // ================================================================
    // Get post
    // ================================================================
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

    // ================================================================
    // View count + view history
    // ================================================================
    @Override
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public void incrementViewCount(String userId, String postId) {
        // 1. Atomic increment view_count in DB
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("view_count = view_count + 1"));

        // 2. Record view history (upsert: update view_time if exists, else insert)
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

    // ================================================================
    // Star (favorite) toggle - DB is source of truth, Redis as cache
    // ================================================================
    @Override
    @Transactional
    public boolean toggleStar(String userId, String postId) {
        // Verify post exists
        getPostById(postId);

        String redisKey = REDIS_KEY_STAR + postId + ":" + userId;

        // Check DB for existing star record
        PostStar existing = postStarMapper.selectOne(
                new LambdaQueryWrapper<PostStar>()
                        .eq(PostStar::getPostId, postId)
                        .eq(PostStar::getUserId, userId));

        if (existing != null) {
            // Unstar: delete from DB, decrement count, remove Redis cache
            postStarMapper.deleteById(existing.getId());
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("star_count = GREATEST(0, star_count - 1)"));
            redisTemplate.delete(redisKey);
            log.info("用户 {} 取消收藏帖子 {}", userId, postId);
            return false;
        } else {
            // Star: insert into DB, increment count, set Redis cache
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
            log.info("用户 {} 收藏帖子 {}", userId, postId);
            return true;
        }
    }

    // ================================================================
    // Like toggle - DB is source of truth, Redis as cache
    // ================================================================
    @Override
    @Transactional
    public boolean toggleLike(String userId, String postId) {
        // Verify post exists
        getPostById(postId);

        String redisKey = REDIS_KEY_LIKE + postId + ":" + userId;

        // Check DB for existing like record
        PostLike existing = postLikeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId));

        if (existing != null) {
            // Unlike: delete from DB, decrement count, remove Redis cache
            postLikeMapper.deleteById(existing.getId());
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
            redisTemplate.delete(redisKey);
            log.info("用户 {} 取消点赞帖子 {}", userId, postId);
            return false;
        } else {
            // Like: insert into DB, increment count, set Redis cache
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
            log.info("用户 {} 点赞帖子 {}", userId, postId);
            return true;
        }
    }

    // ================================================================
    // State check (for display)
    // ================================================================
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
        // Fallback to DB
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
        // Fallback to DB
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

    // ================================================================
    // Personal homepage queries
    // ================================================================
    @Override
    public List<Post> getViewHistory(String userId, int page, int size) {
        // 1. Query view_history ordered by view_time desc
        Page<ViewHistory> historyPage = viewHistoryMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ViewHistory>()
                        .eq(ViewHistory::getUserId, userId)
                        .orderByDesc(ViewHistory::getViewTime));

        if (historyPage.getRecords().isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Get post IDs in order
        List<String> postIds = new ArrayList<>();
        for (ViewHistory vh : historyPage.getRecords()) {
            postIds.add(vh.getPostId());
        }

        // 3. Query posts and preserve order
        List<Post> posts = postMapper.selectBatchIds(postIds);
        // Reorder to match history order (filter out deleted posts)
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
        // 1. Query post_stars ordered by create_time desc
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
        // 1. Query post_likes ordered by create_time desc
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

    // ================================================================
    // My posts + aggregate stats
    // ================================================================
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
    public java.util.Map<String, Long> getSchoolPostCounts() {
        List<Post> allPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getDeleted, false)
                        .eq(Post::getStatus, 1));
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (Post p : allPosts) {
            counts.merge(p.getSchoolId(), 1L, Long::sum);
        }
        return counts;
    }
}
