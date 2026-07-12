# 优化计划 05：帖子详情（读 + 写计数混合）

> **优先级：P0** | **服务：post-service(8082) + user-service(8081)** | **接口数：1**

---

## 一、业务背景

### 1.1 业务背景类型：被动场景型（系统/运行时驱动）

**为什么现在做**：帖子详情是最高频的读接口之一，且是**读+写混合**接口（读帖子内容 + view_count +1 + 记录浏览历史）。热门帖子被集中访问时，view_count 行锁竞争 + 浏览历史 upsert + 作者信息跨服务查询叠加，延迟易劣化。

**如果不做会怎样**：
- 热门帖子 view_count 行锁竞争，P95 劣化
- 浏览历史 upsert 与详情查询串行，拖慢响应
- 作者信息每次跨服务查（N+1 变种：单帖单查但高频）

**做成之后意味着什么**：帖子详情在热门帖场景下保持 < 200ms，view_count 增量不阻塞读。

### 1.2 涉及接口

| # | 接口 | 方法 | 路径 | 当前实现要点 |
|---|------|------|------|-------------|
| 1 | 帖子详情 | GET | `/posts/{postId}` | 查 Post 详情 + view_count+1 + upsert view_history + 查作者信息(Feign) + 查分类/子分类 |

---

## 二、当前实现分析

> ⚠️ 优化前需 `Read` PostController.getPostDetail + PostServiceImpl 确认

### 2.1 疑似瓶颈

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| PD1 | **view_count 写阻塞读** | 高 | UPDATE post SET view_count=view_count+1 串行在详情查询中，热门帖行锁 |
| PD2 | **view_history upsert 同步** | 中 | 每次详情访问都 upsert view_history，写操作拖慢读 |
| PD3 | **作者信息跨服务查** | 中 | 每次查作者 Feign（虽是单帖单查，但高频） |
| PD4 | **无详情缓存** | 中 | 帖子内容不变但每次查 DB |
| PD5 | **content 大字段** | 低 | 详情页需要 content，但若有冗余字段查询需排除 |
| PD6 | **评论数/点赞数实时查** | 中 | 详情页的 like_count/star_count/comment_count 是否冗余在 post 表 |

### 2.2 已知优化（来自历史）

- 列表查询已排除 content 大字段（见 insights/performance 历史记录）
- 详情页是否复用需确认

---

## 三、优化方案

### 3.1 方案选型对比

| 方案 | 核心思路 | 优点 | 缺点 | 选用？ |
|------|----------|------|------|--------|
| **A: 读写分离 + 异步写 + 缓存** | view_count 异步累加(Redis→定时刷DB)；详情 Redis 缓存；作者信息缓存 | 读不阻塞、减少DB压力 | 一致性延迟 | ✅ |
| B: 读写分离数据库 | 读副本 | 彻底解决 | 运维复杂 | ❌ 当前量级不需要 |
| C: CDN 详情页 | 静态化 + CDN | 极快 | 动态内容(计数)难处理 | ❌ 当前不需要 |

### 3.2 第一阶段优化（方案A）

**优化1：view_count 异步化**
- 读请求不直接 UPDATE post，而是 Redis INCR `cache:post:view:{postId}`
- 定时任务（如每分钟）将 Redis 累计增量批量 UPDATE 回 post 表
- 详情查询读 post.view_count（容忍1分钟延迟）或 Redis 实时值

**优化2：view_history 异步化**
- 浏览历史记录改为 @Async 或入消息队列
- 不阻塞详情主流程

**优化3：详情 Redis 缓存**
- key: `cache:post:detail:{postId}`，TTL 5分钟
- 帖子编辑/删除时主动失效
- 作者信息冗余在缓存中（避免每次 Feign）

**优化4：作者信息缓存**
- key: `cache:user:simple:{userId}`，TTL 30分钟
- 减少跨服务 Feign 调用

---

## 四、成功指标

| 指标 | 优化前（待测） | 目标 | 测量方式 |
|------|---------------|------|----------|
| 详情 P95 | 待测 | < 200ms | JMeter 50并发 |
| 详情 P99 | 待测 | < 500ms | JMeter 50并发 |
| 详情缓存命中率 | - | > 80% | Redis stats |
| view_count 延迟 | 同步 | < 1min 异步 | 验证刷新 |
| Feign 作者查询次数 | 每次查 | 缓存命中后 0 | 链路追踪 |

---

## 五、前置条件与风险

- **前置**：基线压测数据；确认当前详情查询是否含 Feign 调用
- **风险1**：view_count 异步化后数据延迟（1分钟），需评估业务可接受度
- **风险2**：详情缓存一致性，帖子编辑/删除需主动失效
- **风险3**：Redis 宕机时降级为直接查 DB（不能让详情不可用）

---

## 六、关联记录

- 优化完成后记录：`records/05-post-detail.md`（待创建）
- 关联计划：[06-帖子列表](06-post-list-queries.md)（作者信息批量查询模式可复用）
- 关联历史：[insights/performance 接口性能SQL聚合缓存N+1](../../insights/performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md)
