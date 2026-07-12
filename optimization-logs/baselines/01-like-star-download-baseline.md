# 01: 帖子点赞/收藏/下载记录 - 基线数据

**测试日期**: 2026-07-03
**测试工具**: JMeter 5.6.3（本地 Windows GUI 模式）
**测试场景**: 50 并发线程，每个接口持续 60 秒，三个接口顺序执行（serialize\_threadgroups=true）
**测试账号**: 13068735577（单一用户）
**目标帖子**: 单个 resource 类型帖子
**监控**: Prometheus + Grafana（CampusShare 应用监控 Dashboard）
**环境**: post-service JVM 已运行17+小时（JIT充分预热）

## 测试结果（JMeter Summary Report）

### 点赞 (POST /api/posts/{id}/like)

| 指标          | 值             | 评估                    |
| ----------- | ------------- | --------------------- |
| Samples     | 71,153        | 60秒 × 50线程            |
| Average     | 40 ms         | ✅ 可接受                 |
| Min         | 2 ms          | -                     |
| Max         | 205 ms        | ⚠️ 长尾偏高               |
| Std Dev     | 21.28 ms      | 波动较大                  |
| **Error %** | **38.08%**    | 🔴 **严重！3条请求中就有1条失败** |
| Throughput  | \~245 req/sec | -                     |

### 收藏 (POST /api/posts/{id}/star)

| 指标          | 值             | 评估                |
| ----------- | ------------- | ----------------- |
| Samples     | 65,164        | 60秒 × 50线程        |
| Average     | 42 ms         | ✅ 可接受             |
| Min         | 2 ms          | -                 |
| Max         | 208 ms        | ⚠️ 长尾偏高           |
| Std Dev     | 20.49 ms      | 波动较大              |
| **Error %** | **37.30%**    | 🔴 **严重！与点赞几乎一致** |
| Throughput  | \~362 req/sec | -                 |

### 下载记录 (POST /api/posts/{id}/download)

| 指标          | 值               | 评估         |
| ----------- | --------------- | ---------- |
| Samples     | 209,521         | 60秒 × 50线程 |
| Average     | **13 ms**       | ✅ 优秀       |
| Min         | 1 ms            | -          |
| Max         | 118 ms          | ✅ 可接受      |
| Std Dev     | 6.43 ms         | 稳定         |
| **Error %** | **0.00%**       | ✅ 零错误      |
| Throughput  | \~1,164 req/sec | ✅ 高性能      |

## 关键发现

### 1. 下载接口：表现优秀（基准线）

- 纯 INSERT 操作，无事务、无 Redis 写入、无 Feign 调用
- 13ms 平均延迟、0% 错误率、1164 QPS → 作为性能基准

### 2. 点赞/收藏：38% 错误率 = 严重并发 Bug

- 错误率 \~37-38%，与下载形成鲜明对比（0% vs 38%）
- 代码分析预测的竞态条件被确认：
  - 当前实现：`selectOne` 检查是否存在 → 不存在则 `insert`，存在则 `delete`
  - 50 并发线程同时 toggle 同一个帖子时，多个线程同时 selectOne 查到"不存在"，然后同时 insert → **重复键异常**
  - 或者同时查到"存在"，然后同时 delete（第二次 delete 会删不到）
- 错误类型待确认（需查看 post-service 错误日志）

### 3. 延迟本身不高，但错误率不可接受

- 成功请求 avg 40-42ms，本身不算慢
- 但 38% 的错误率意味着线上用户每3次点赞就有1次失败——这是 P0 Bug

### 4. 长尾延迟（Max 205ms vs Avg 40ms）

- Max 是 Avg 的 5 倍，可能原因：
  - 事务内同步 Feign 调用创建通知（网络开销）
  - GC 停顿
  - 错误请求的异常处理开销
  - MySQL 行锁等待（多个线程同时更新同一行 like\_count）

## 延迟根因分析（待验证）

| 可能原因                                | 影响接口      | 严重程度  | 验证方式                     |
| ----------------------------------- | --------- | ----- | ------------------------ |
| selectOne+insert 非原子，缺少唯一索引 → 重复键异常 | Like/Star | 🔴 P0 | 查看错误日志 + SHOW INDEX      |
| 事务内同步 Feign 调用（创建通知）拉长事务持锁时间        | Like/Star | 🟡 P1 | 对比 Feign 超时配置 + trace 日志 |
| 每次 toggle 都查 getPostById 查帖子（可用缓存）  | Like/Star | 🟢 P2 | 代码确认是否有缓存                |
| like\_count 更新用行锁，并发更新同一帖子锁竞争       | Like/Star | 🟡 P1 | MySQL 慢查询 + 锁等待日志        |

## 待补充数据

- [ ] post-service 错误日志（确认具体异常类型）
- [ ] post\_likes/post\_stars 表索引情况（是否有 (post\_id, user\_id) 唯一索引）
- [ ] Grafana 截图（JVM 堆、GC、CPU、P95 延迟面板）
- [ ] 并发错误时的 HTTP 响应码和响应体

