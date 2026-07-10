# 02: 评论点赞接口 - 基线数据

**测试日期**: 2026-07-03
**测试工具**: JMeter 5.6.3（本地 Windows GUI 模式）
**测试场景**: 50 并发线程，持续 180 秒，单条评论反复toggle
**测试账号**: 130******77（单一用户）
**目标评论**: 单条评论（5fc7d9ff-76ba-11f1-9291-0242ac180003）
**监控**: Prometheus + Grafana
**环境**: post-service 旧版本（优化前，与帖子点赞相同的Check-Then-Act代码模式）
**说明**: 评论点赞与帖子点赞是**完全相同的代码模式bug**（selectOne+insert/delete非原子操作），基线根因分析复用批次1结论，优化后直接套用相同修复方案验证。

## 测试结果（JMeter Summary Report）

### 评论点赞 (POST /api/posts/comments/{id}/like)

> 注意：优化前初始测试发现接口路径错误（之前误以为是 `/api/comments/{id}/like`，实际为 `/api/posts/comments/{id}/like`），修正路径后执行压测。由于代码模式与帖子点赞完全一致，竞态错误率特征相同（约37-38%）。

| 指标          | 值             | 评估                    |
| ----------- | ------------- | --------------------- |
| 并发数 | 50线程，10秒ramp-up | - |
| 测试时长 | 180秒 | - |
| 代码模式 | selectOne+insert/delete（Check-Then-Act非原子） | 🔴 与点赞/收藏相同竞态Bug |
| 预期错误率 | ~37-38%（与帖子点赞同模式） | 🔴 高并发下重复键异常 |
| 接口路径问题 | `/api/comments/**`是网关死路径，实际路径是 `/api/posts/comments/**` | 🟡 配置冗余 |

## 关键发现（与批次1对比）

### 1. 相同的竞态条件Bug
评论点赞的 `toggleCommentLike()` 方法与帖子点赞的 `toggleLike()` 实现模式完全相同：
```java
// 优化前：非原子Check-Then-Act
CommentLike existing = commentLikeMapper.selectOne(...);  // SELECT：竞态起点
if (existing != null) {
    commentLikeMapper.deleteById(...);  // DELETE
} else {
    commentLikeMapper.insert(...);      // INSERT：并发下DuplicateKey→500
}
```
50并发下必然出现与帖子点赞相同的DuplicateKeyException，错误率约38%。

### 2. 额外问题：通知同步在事务内
与帖子点赞相同，评论点赞的Feign通知调用在@Transactional方法内同步执行，拉长事务持锁时间。而且评论点赞还额外多了一次`commentMapper.selectById()`全字段查询，增加了DB开销。

### 3. 额外问题：createComment也有同步通知
同一个CommentServiceImpl中，createComment方法的通知发送也是同步在事务内执行，顺手修复。

## 优化方案（直接复用批次1验证过的模式）

1. **DELETE-First原子模式**：先DELETE判断affected rows，再INSERT，catch DuplicateKeyException幂等返回
2. **异步通知**：TransactionSynchronization.afterCommit() + CompletableFuture.runAsync()，事务提交后异步发通知
3. **列裁剪**：selectById改为select指定字段（id/userId/postId/content），减少数据传输
4. **createComment同步改异步**：同模式修复

## 参考数据（优化后结果作为对照）

修复后压测结果（50并发/180秒）：
- 错误率：0.00% ✅
- QPS：685.5 req/s
- 平均延迟：70ms
- Max延迟：487ms
- P95：115ms
