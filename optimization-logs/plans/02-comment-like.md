# 优化计划：评论点赞接口（批次2）

> 创建时间：2026-07-03 | 优先级：P0 | 状态：✅ 已完成

---

## 目标接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 评论点赞/取消 | POST | `/api/posts/comments/{commentId}/like` | toggle评论点赞 |

---

## 问题分析

与帖子点赞**完全相同的反模式**：Check-Then-Act（selectOne判断是否已点赞 → insert/delete），高并发下导致唯一索引冲突抛500。此外，评论创建和点赞通知同步在事务内执行，拉长DB锁持有时间。

**并发Bug根因**：
```java
// 优化前：非原子操作
CommentLike existing = commentLikeMapper.selectOne(...);
if (existing != null) {
    commentLikeMapper.delete(...);  // 并发时可能已被其他线程删掉
} else {
    commentLikeMapper.insert(like); // 并发时可能重复插入→DuplicateKeyException→500
}
```

---

## 优化策略

与批次1完全相同的方案（复用已验证模式）：

1. **DELETE-First 原子操作**：先DELETE，affected rows > 0 说明之前已点赞→取消；= 0 说明未点赞→INSERT
2. **catch DuplicateKeyException**：唯一键冲突时幂等返回true（不抛500）
3. **评论查询列裁剪**：用select+指定列替代selectById全字段查询
4. **异步通知（afterCommit）**：评论点赞通知和评论创建的REPLY通知均在事务提交后异步发送
5. **去掉冗余selectOne**：每次toggle减少1次DB查询

---

## 压测结果

### 测试配置
- 并发：50线程，10s ramp-up
- 时长：180秒
- 场景：单用户对同一条评论反复toggle（like/unlike交替）
- DB状态：comment_likes表清空，like_count重置为0

### JMeter 结果

| 指标 | 结果 |
|------|------|
| 总请求数 | 123,431 |
| **错误率** | **0.00%** ✅ |
| **吞吐量** | **685.5 req/s** |
| 平均延迟 | 70ms |
| Min延迟 | 3ms |
| Max延迟 | 487ms |
| Std Dev | 25.77ms |
| P95(post-service) | 115ms |
| P95(gateway) | 117ms |

### Grafana 监控
- GC频率：~0.8 ops/s（健康）
- GC暂停时间：~5ms（健康）
- User服务P95：2.99ms（不受影响）
- 无异常错误日志

### 与帖子点赞对比

| 指标 | 帖子点赞(批次1) | 评论点赞(批次2) | 说明 |
|------|----------------|----------------|------|
| QPS | 713/s | 685/s | 基本持平 |
| Avg延迟 | 68ms | 70ms | 基本持平 |
| Max延迟 | 291ms | 487ms | 评论多一次comment+post查询，Max略高 |
| 错误率 | 0% | 0% | 均已修复 |
| GC频率 | ~1.8 ops/s | ~0.8 ops/s | 评论无额外通知查询压力 |

---

## 关键代码改动

详见 [CommentServiceImpl.java](file:///e:/workspace_work/CampusShare/backend/campushare-post/src/main/java/com/campushare/post/service/impl/CommentServiceImpl.java)
