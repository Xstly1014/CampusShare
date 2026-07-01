package com.campushare.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.exception.BusinessException;
import com.campushare.post.cache.CategoryCache;
import com.campushare.post.dto.CreatePostRequest;
import com.campushare.post.dto.PostVectorDTO;
import com.campushare.post.dto.UserPostStats;
import com.campushare.post.dto.WarehouseStats;
import com.campushare.post.entity.Category;
import com.campushare.post.entity.Post;
import com.campushare.post.entity.PostDownload;
import com.campushare.post.entity.PostLike;
import com.campushare.post.entity.PostStar;
import com.campushare.post.entity.ViewHistory;
import com.campushare.post.feign.AgentFeignClient;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.PostDownloadMapper;
import com.campushare.post.mapper.PostLikeMapper;
import com.campushare.post.mapper.PostMapper;
import com.campushare.post.mapper.PostStarMapper;
import com.campushare.post.mapper.ViewHistoryMapper;
import com.campushare.post.service.PostService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostStarMapper postStarMapper;
    private final PostLikeMapper postLikeMapper;
    private final ViewHistoryMapper viewHistoryMapper;
    private final PostDownloadMapper postDownloadMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final UserFeignClient userFeignClient;
    private final AgentFeignClient agentFeignClient;
    private final ObjectMapper objectMapper;
    private final CategoryCache categoryCache;

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
        notifyAgentPostChanged(post.getId(), "CREATE");
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
        wrapper.select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                        Post::getAuthorId, Post::getPostType, Post::getTitle, Post::getContent,
                        Post::getFileUrl, Post::getFileName, Post::getFileType, Post::getFileSize,
                        Post::getViewCount, Post::getStarCount, Post::getLikeCount, Post::getCommentCount,
                        Post::getCreateTime)
                .eq(Post::getSchoolId, schoolId)
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
            try {
                UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                req.setUserId(post.getAuthorId());
                req.setSenderId(userId);
                req.setType("STAR");
                req.setTargetId(postId);
                req.setTargetTitle(post.getTitle());
                req.setSchoolId(post.getSchoolId());
                userFeignClient.createNotification(req);
            } catch (Exception e) {
                log.warn("Feign调用创建收藏通知失败: {}", e.getMessage());
            }
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
            try {
                UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                req.setUserId(post.getAuthorId());
                req.setSenderId(userId);
                req.setType("LIKE");
                req.setTargetId(postId);
                req.setTargetTitle(post.getTitle());
                req.setSchoolId(post.getSchoolId());
                userFeignClient.createNotification(req);
            } catch (Exception e) {
                log.warn("Feign调用创建点赞通知失败: {}", e.getMessage());
            }
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
    public IPage<Post> getViewHistory(String userId, int page, int size) {
        long total = viewHistoryMapper.countValidByUserId(userId);
        Page<Post> result = new Page<>(page, size, total);
        if (total == 0) {
            result.setRecords(new ArrayList<>());
            return result;
        }

        int offset = (page - 1) * size;
        List<String> postIds = viewHistoryMapper.selectValidPostIdsPage(userId, offset, size);
        result.setRecords(selectPostsByIdsWithoutContent(postIds));
        return result;
    }

    @Override
    public IPage<Post> getStarredPosts(String userId, int page, int size) {
        long total = postStarMapper.countValidByUserId(userId);
        Page<Post> result = new Page<>(page, size, total);
        if (total == 0) {
            result.setRecords(new ArrayList<>());
            return result;
        }

        int offset = (page - 1) * size;
        List<String> postIds = postStarMapper.selectValidPostIdsPage(userId, offset, size);
        result.setRecords(selectPostsByIdsWithoutContent(postIds));
        return result;
    }

    @Override
    public IPage<Post> getLikedPosts(String userId, int page, int size) {
        long total = postLikeMapper.countValidByUserId(userId);
        Page<Post> result = new Page<>(page, size, total);
        if (total == 0) {
            result.setRecords(new ArrayList<>());
            return result;
        }

        int offset = (page - 1) * size;
        List<String> postIds = postLikeMapper.selectValidPostIdsPage(userId, offset, size);
        result.setRecords(selectPostsByIdsWithoutContent(postIds));
        return result;
    }

    @Override
    public IPage<Post> getMyPosts(String userId, int page, int size, String postType, String keyword) {
        Page<Post> postPage = postMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                                Post::getAuthorId, Post::getPostType, Post::getTitle, Post::getContent,
                                Post::getFileUrl, Post::getFileName, Post::getFileType, Post::getFileSize,
                                Post::getViewCount, Post::getStarCount, Post::getLikeCount, Post::getCommentCount,
                                Post::getCreateTime)
                        .eq(Post::getAuthorId, userId)
                        .eq(Post::getDeleted, false)
                        .eq(postType != null && !postType.isEmpty(), Post::getPostType, postType)
                        .like(keyword != null && !keyword.isEmpty(), Post::getTitle, keyword)
                        .orderByDesc(Post::getCreateTime));
        return postPage;
    }

    @Override
    public UserPostStats getMyPostStats(String userId) {
        long totalViews = postMapper.sumViewCountByAuthorId(userId);
        long totalLikes = postMapper.sumLikeCountByAuthorId(userId);
        long totalStars = postMapper.sumStarCountByAuthorId(userId);
        long postCount = postMapper.countByAuthorId(userId);
        return new UserPostStats(totalViews, totalLikes, totalStars, postCount);
    }

    @Override
    public Map<String, Long> getSchoolPostCounts() {
        String cacheKey = "cache:school:post:counts";
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<Map<String, Long>>() {});
            }
        } catch (Exception e) {
            log.warn("读取学校帖子数缓存失败: {}", e.getMessage());
        }

        Map<String, Long> counts = new HashMap<>();
        List<Map<String, Object>> rows = postMapper.countGroupBySchool();
        for (Map<String, Object> row : rows) {
            Object schoolId = row.get("schoolId");
            Object cnt = row.get("cnt");
            if (schoolId != null && cnt != null) {
                counts.put(schoolId.toString(), ((Number) cnt).longValue());
            }
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(counts), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入学校帖子数缓存失败: {}", e.getMessage());
        }
        return counts;
    }

    private List<Post> selectPostsByIdsWithoutContent(List<String> postIds) {
        if (postIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                                Post::getAuthorId, Post::getPostType, Post::getTitle, Post::getContent,
                                Post::getFileUrl, Post::getFileName, Post::getFileType, Post::getFileSize,
                                Post::getViewCount, Post::getStarCount, Post::getLikeCount, Post::getCommentCount,
                                Post::getCreateTime)
                        .in(Post::getId, postIds)
                        .eq(Post::getDeleted, false));
        Map<String, Post> postMap = new HashMap<>();
        for (Post p : posts) {
            postMap.put(p.getId(), p);
        }
        List<Post> result = new ArrayList<>();
        for (String pid : postIds) {
            Post p = postMap.get(pid);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public UserPostStats getUserPostStats(String userId) {
        return getMyPostStats(userId);
    }

    @Override
    public WarehouseStats getWarehouseStats(String userId) {
        long uploadCount = postMapper.countResourceByAuthorId(userId);
        long downloadCount = postDownloadMapper.countValidByUserId(userId, null);
        long totalViews = postMapper.sumViewCountByAuthorIdResource(userId);
        long totalLikes = postMapper.sumLikeCountByAuthorIdResource(userId);
        long totalStars = postMapper.sumStarCountByAuthorIdResource(userId);
        long totalDownloadsOfMyPosts = postDownloadMapper.countDownloadsByAuthor(userId);

        java.util.Map<String, Long> uploadsByCategory = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : postMapper.countUploadsByAuthorGroupByCategory(userId)) {
            uploadsByCategory.put(String.valueOf(row.get("categoryId")), ((Number) row.get("cnt")).longValue());
        }

        java.util.Map<String, Long> downloadsByCategory = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : postDownloadMapper.countDownloadsGroupByCategory(userId)) {
            downloadsByCategory.put(String.valueOf(row.get("categoryId")), ((Number) row.get("cnt")).longValue());
        }

        java.util.Set<String> allCategoryIds = new java.util.LinkedHashSet<>();
        allCategoryIds.addAll(uploadsByCategory.keySet());
        allCategoryIds.addAll(downloadsByCategory.keySet());

        List<WarehouseStats.CategoryStat> categoryStats = new ArrayList<>();
        for (String categoryId : allCategoryIds) {
            Category cat = categoryCache.getCategory(categoryId);
            categoryStats.add(WarehouseStats.CategoryStat.builder()
                    .categoryId(categoryId)
                    .categoryName(cat != null ? cat.getName() : "未知分类")
                    .color(cat != null ? cat.getColor() : "blue")
                    .icon(cat != null ? cat.getIcon() : "FileText")
                    .uploadCount(uploadsByCategory.getOrDefault(categoryId, 0L))
                    .downloadCount(downloadsByCategory.getOrDefault(categoryId, 0L))
                    .build());
        }
        categoryStats.sort((a, b) -> Long.compare(b.getUploadCount() + b.getDownloadCount(), a.getUploadCount() + a.getDownloadCount()));

        return WarehouseStats.builder()
                .uploadCount(uploadCount)
                .downloadCount(downloadCount)
                .totalViews(totalViews)
                .totalLikes(totalLikes)
                .totalStars(totalStars)
                .totalDownloadsOfMyPosts(totalDownloadsOfMyPosts)
                .categoryStats(categoryStats)
                .build();
    }

    @Override
    public void recordDownload(String userId, String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() || !"resource".equals(post.getPostType())) {
            return;
        }
        PostDownload download = PostDownload.builder()
                .postId(postId)
                .userId(userId)
                .downloadTime(LocalDateTime.now())
                .build();
        postDownloadMapper.insert(download);
    }

    @Override
    public IPage<Post> getMyDownloads(String userId, int page, int size, String keyword) {
        int offset = (page - 1) * size;
        long total = postDownloadMapper.countValidByUserId(userId, keyword);
        List<PostDownload> downloads = postDownloadMapper.selectValidPage(userId, offset, size, keyword);

        if (downloads.isEmpty()) {
            Page<Post> emptyPage = new Page<>(page, size, 0);
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        List<String> postIds = new ArrayList<>(downloads.size());
        for (PostDownload d : downloads) {
            postIds.add(d.getPostId());
        }
        List<Post> posts = postMapper.selectBatchIds(postIds);
        java.util.Map<String, Post> postMap = new java.util.HashMap<>(posts.size());
        for (Post p : posts) {
            postMap.put(p.getId(), p);
        }

        List<Post> orderedPosts = new ArrayList<>();
        for (PostDownload d : downloads) {
            Post p = postMap.get(d.getPostId());
            if (p != null && !p.getDeleted()) {
                orderedPosts.add(p);
            }
        }

        Page<Post> result = new Page<>(page, size, total);
        result.setRecords(orderedPosts);
        return result;
    }

    @Override
    public Map<String, PostDownload> getDownloadRecordMap(String userId, List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        List<PostDownload> downloads = postDownloadMapper.selectList(
                new LambdaQueryWrapper<PostDownload>()
                        .eq(PostDownload::getUserId, userId)
                        .in(PostDownload::getPostId, postIds));
        Map<String, PostDownload> map = new HashMap<>(downloads.size());
        for (PostDownload d : downloads) {
            map.put(d.getPostId(), d);
        }
        return map;
    }

    @Override
    public void deleteDownloadRecord(String userId, int recordId) {
        PostDownload download = postDownloadMapper.selectById(recordId);
        if (download == null || !download.getUserId().equals(userId)) {
            throw new BusinessException(4030, "下载记录不存在或无权删除");
        }
        postDownloadMapper.deleteById(recordId);
    }

    private void notifyAgentPostChanged(String postId, String action) {
        CompletableFuture.runAsync(() -> {
            try {
                AgentFeignClient.PostVectorNotifyRequest req = new AgentFeignClient.PostVectorNotifyRequest();
                req.setPostId(postId);
                req.setAction(action);
                agentFeignClient.notifyPostChanged(req);
            } catch (Exception e) {
                log.warn("Feign调用通知agent-service帖子变更失败 postId={} action={}: {}", postId, action, e.getMessage());
            }
        });
    }

    @Override
    public IPage<PostVectorDTO> getPostsForVector(int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Post::getId, Post::getSchoolId, Post::getCategoryId, Post::getSubCategoryId,
                        Post::getAuthorId, Post::getPostType, Post::getTitle, Post::getContent,
                        Post::getLikeCount, Post::getViewCount, Post::getCreateTime)
                .eq(Post::getStatus, 1)
                .eq(Post::getDeleted, false)
                .orderByDesc(Post::getCreateTime);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PostVectorDTO> dtoPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        List<PostVectorDTO> dtoList = new ArrayList<>(postPage.getRecords().size());
        for (Post p : postPage.getRecords()) {
            dtoList.add(toVectorDTO(p));
        }
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public PostVectorDTO getPostVectorData(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted()) {
            return null;
        }
        return toVectorDTO(post);
    }

    private PostVectorDTO toVectorDTO(Post post) {
        PostVectorDTO dto = new PostVectorDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        String content = post.getContent();
        dto.setContentExcerpt(content != null && content.length() > 500 ? content.substring(0, 500) : content);
        dto.setPostType(post.getPostType());
        dto.setCategoryId(post.getCategoryId());
        dto.setSchoolId(post.getSchoolId());
        dto.setAuthorId(post.getAuthorId());
        dto.setLikeCount(post.getLikeCount());
        dto.setViewCount(post.getViewCount());
        dto.setCreateTime(post.getCreateTime());
        return dto;
    }
}
