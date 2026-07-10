# 02 - 评论点赞接口优化记录

> 批次2 | 优先级：P0 | 完成时间：2026-07-03 | Commit: f3345bb

---

## S - Situation（业务背景）

评论点赞（`POST /api/posts/comments/{commentId}/like`）是帖子详情页高频交互接口。用户浏览评论区时会对评论进行点赞/取消点赞操作，在帖子热度较高时（如考试周复习资料帖），单条评论可能在短时间内收到大量并发点赞请求。

**数据规模**：
- comments 表：25,025 条
- comment_likes 表：优化前清空（模拟新上线状态）
- 接口QPS量级：与帖子点赞同级别（数百QPS）

---

## T - Task（目标）

1. **修复并发Bug**：与帖子点赞相同的Check-Then-Act竞态条件，高并发下DuplicateKeyException导致500错误
2. **降低接口延迟**：异步化通知，缩短事务持锁时间
3. **复用验证过的优化模式**：批次1已证明DELETE-First+异步通知有效，直接套用

**预期结果**：错误率 0%，QPS不低于帖子点赞的80%（因额外查询逻辑略有开销）

---

## A - Action（行动）

### A1. 分析问题代码

优化前 CommentServiceImpl.toggleCommentLike() 的核心问题：

```java
// ========== 优化前 ==========
@Override
@Transactional
public boolean toggleCommentLike(String userId, String commentId) {
    CommentLike existing = commentLikeMapper.selectOne(
        new LambdaQueryWrapper<CommentLike>()
            .eq(CommentLike::getCommentId, commentId)
            .eq(CommentLike::getUserId, userId));
    
    if (existing != null) {
        commentLikeMapper.deleteById(existing.getId());
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
            .eq(Comment::getId, commentId)
            .setSql("like_count = GREATEST(0, like_count - 1)"));
        return false;
    } else {
        CommentLike like = new CommentLike();
        like.setId(UUID.randomUUID().toString());
        like.setCommentId(commentId);
        like.setUserId(userId);
        like.setCreateTime(LocalDateTime.now());
        commentLikeMapper.insert(like);  // ← 并发时多个线程同时insert→DuplicateKey→500
        commentMapper.update(null, ...);
        // 评论通知：如果是回复评论，通知被回复者
        Comment comment = commentMapper.selectById(commentId);  // 全字段查询
        if (comment.getParentId() != null) {
            // ... 同步Feign通知（拉长事务时间）
        }
        return true;
    }
}
```

**问题清单**：
1. ❌ selectOne + insert/delete：典型的Check-Then-Act竞态
2. ❌ 没有catch DuplicateKeyException：唯一键冲突直接抛500
3. ❌ selectById 全字段查询：只需要id/userId/postId/content，却查了所有列
4. ❌ 通知同步在事务内：Feign调用网络IO期间持有DB行锁
5. ❌ createComment中的通知也在事务内同步执行

### A2. DELETE-First 原子模式改造

```java
// ========== 优化后 ==========
@Override
@Transactional
public boolean toggleCommentLike(String userId, String commentId) {
    String redisKey = "comment:liked:" + commentId + ":" + userId;
    Boolean cached = redisTemplate.hasKey(redisKey);
    
    if (Boolean.TRUE.equals(cached)) {
        redisTemplate.delete(redisKey);
    }
    
    // 核心：先DELETE，用affected rows判断状态
    int deleted = commentLikeMapper.delete(
        new LambdaQueryWrapper<CommentLike>()
            .eq(CommentLike::getCommentId, commentId)
            .eq(CommentLike::getUserId, userId));
    
    if (deleted > 0) {
        // 已取消点赞
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
            .eq(Comment::getId, commentId)
            .setSql("like_count = GREATEST(0, like_count - 1)"));
        redisTemplate.delete(redisKey);
        return false;
    }
    
    try {
        CommentLike like = new CommentLike();
        like.setId(UUID.randomUUID().toString());
        like.setCommentId(commentId);
        like.setUserId(userId);
        like.setCreateTime(LocalDateTime.now());
        commentLikeMapper.insert(like);
        
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
            .eq(Comment::getId, commentId)
            .setSql("like_count = like_count + 1"));
        
        redisTemplate.opsForValue().set(redisKey, "1", 3, TimeUnit.DAYS);
        
        // 异步通知
        Comment comment = commentMapper.selectOne(
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .select(Comment::getId, Comment::getUserId, Comment::getPostId, Comment::getContent));
        
        if (comment.getParentId() != null) {
            sendCommentNotificationAfterCommit(
                comment.getUserId(), userId, "COMMENT_REPLY",
                commentId, comment.getContent(), comment.getPostId());
        } else {
            Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                    .eq(Post::getId, comment.getPostId())
                    .select(Post::getId, Post::getUserId, Post::getTitle));
            sendCommentNotificationAfterCommit(
                post.getUserId(), userId, "COMMENT",
                commentId, comment.getContent(), comment.getPostId());
        }
        
        return true;
    } catch (DuplicateKeyException e) {
        // 并发冲突幂等处理
        redisTemplate.opsForValue().set(redisKey, "1", 3, TimeUnit.DAYS);
        return true;
    }
}
```

### A3. 异步通知工具方法

```java
private void sendCommentNotificationAfterCommit(String recipientId, String senderId, String type,
                                                String targetId, String targetTitle, String postId) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            CompletableFuture.runAsync(() -> {
                try {
                    UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                    req.setUserId(recipientId);
                    req.setSenderId(senderId);
                    req.setType(type);
                    req.setTargetId(targetId);
                    req.setTargetTitle(targetTitle.length() > 50 ? targetTitle.substring(0, 50) + "..." : targetTitle);
                    req.setSchoolId(null);
                    req.setPostId(postId);
                    userFeignClient.createNotification(req);
                } catch (Exception e) {
                    log.warn("异步发送评论{}通知失败: commentId={}, error={}", type, targetId, e.getMessage());
                }
            });
        }
    });
}
```

### A4. createComment 通知同步→异步

在同一个文件中，顺手修复了createComment方法中存在的相同问题——REPLY/COMMENT通知同步在事务内执行。改为相同的afterCommit异步模式。

### A5. 评论查询列裁剪

```java
// 优化前：selectById 查全部字段
Comment comment = commentMapper.selectById(commentId);

// 优化后：只查需要的4个字段
Comment comment = commentMapper.selectOne(
    new LambdaQueryWrapper<Comment>()
        .eq(Comment::getId, commentId)
        .select(Comment::getId, Comment::getUserId, Comment::getPostId, Comment::getContent));
```

---

## R - Result（结果）

### 压测配置
- 工具：JMeter 5.6.3 GUI模式
- 并发：50线程，10秒ramp-up
- 时长：180秒（3分钟）
- 场景：单用户(13068735577)对单条评论(5fc7d9ff-...)反复toggle
- JVM：post-service -Xms512m -Xmx1024m（已预热，JVM已运行17小时+）

### JMeter 结果

| 指标 | 结果 |
|------|------|
| 总请求数 | **123,431** |
| 错误率 | **0.00%** ✅ |
| 吞吐量 | **685.5 req/s** |
| 平均延迟 | 70ms |
| Min延迟 | 3ms |
| Max延迟 | 487ms |
| P95(post-service) | 115ms |
| P95(gateway) | 117ms |
| Std Dev | 25.77ms |

### Grafana 监控
- 请求QPS峰值：~750 req/s
- post-service P95延迟：稳定在115ms
- user-service P95：2.99ms（不受评论点赞影响）
- GC频率：~0.8 ops/s（健康）
- GC暂停时间：~5ms（健康）
- 应用日志无DuplicateKeyException/错误

### 与帖子点赞（批次1）对比

| 指标 | 帖子点赞 | 评论点赞 | 差异分析 |
|------|---------|---------|---------|
| QPS | 713/s | 685/s | 评论多一次comment查询+post查询，开销约4% |
| Avg延迟 | 68ms | 70ms | 基本持平 |
| Max延迟 | 291ms | 487ms | 评论通知路径有两次DB查询，偶发慢查询 |
| P95(post) | 95ms | 115ms | 多一次查询增加~20ms |
| 错误率 | 0% | 0% | 均已修复 |
| GC频率 | ~1.8 ops/s | ~0.8 ops/s | 评论通知targetTitle截取逻辑创建更少临时对象 |

---

## 关键经验

1. **同模式Bug批量修复**：发现一个Check-Then-Act反模式后，应全局搜索所有`selectOne + insert/delete`组合，一次性修复。评论点赞是帖子点赞的同模式问题。

2. **网关路由死路径**：网关配置了`/api/comments/**`路由到post-service，但CommentController的路径是`/posts/comments/**`，`/api/comments/**`是死路径。这是配置冗余，后续应清理。

3. **异步通知收益可量化**：通知从事务内同步改为afterCommit异步，事务持锁时间 = DB操作时间（不含Feign网络调用），在高并发下显著减少锁等待。

4. **列裁剪是低 hanging fruit**：selectById vs select(指定列)，减少数据传输和内存分配，在高QPS下累积效果可观。
