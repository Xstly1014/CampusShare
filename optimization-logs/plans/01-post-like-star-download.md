# 优化计划 01：帖子点赞 / 收藏 / 下载记录（高并发写）

> **优先级：P0** | **服务：post-service(8082)** | **接口数：3**

---

## 一、业务背景

### 1.1 业务背景类型：被动场景型（系统/运行时驱动）

**为什么现在做**：点赞/收藏/下载是用户互动核心动作，在热门帖子场景下可能出现瞬时高并发写入（一条热门资源帖被集中点赞/下载），当前实现为同步写DB+同步发通知，高并发下数据库行锁竞争、Feign调用阻塞、响应延迟飙升。

**如果不做会怎样**：
- 热门帖子并发点赞时 DB 行锁等待，P99 延迟可能飙至秒级
- 通知发送的 Feign 同步调用阻塞主流程，user-service 抖动会拖垮 post-service
- 下载计数与下载动作耦合，下载响应被计数逻辑拖慢

**做成之后意味着什么**：核心互动接口能在高并发下保持低延迟，通知发送异步化后 post-service 不再因 user-service 抖动而变慢，为后续压测打基础。

### 1.2 涉及接口

| # | 接口 | 方法 | 路径 | 当前实现要点 |
|---|------|------|------|-------------|
| 1 | 帖子点赞 | POST | `/posts/{postId}/like` | toggle 语义：已赞则取消、未赞则点赞；写 post_likes 表 + update post.like_count + Feign 发 LIKE 通知 |
| 2 | 帖子收藏 | POST | `/posts/{postId}/star` | toggle 语义；写 post_stars 表 + update post.star_count |
| 3 | 下载记录 | POST | `/posts/{postId}/download` | 写/更新 view_history 或专用下载记录表 + update post.download_count |

---

## 二、当前实现分析

> ⚠️ 优化前需 `Read` 实际源码确认以下分析，以下为基于架构文档的预判

### 2.1 点赞 toggleLike 流程

```
1. JWT 取 userId
2. 查 post_likes 判断是否已赞（SELECT）
3. 已赞 → DELETE post_likes + UPDATE post SET like_count = like_count - 1
   未赞 → INSERT post_likes + UPDATE post SET like_count = like_count + 1
4. Feign 调用 user-service 创建 LIKE 通知（同步）
5. 返回
```

### 2.2 疑似瓶颈

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| L1 | **通知发送同步阻塞** | 高 | Feign 调用 user-service 同步等待，user-service 慢则点赞慢；用户感知不到但 P99 受影响 |
| L2 | **toggle 非原子** | 中 | 查存在性→写表→改计数，3步非事务原子，并发下可能重复插入或计数错乱（虽有唯一约束兜底） |
| L3 | **post 表行锁竞争** | 高 | UPDATE post SET like_count=like_count+1 WHERE id=? 热门帖所有并发点赞都争抢同一行锁 |
| L4 | **无缓存** | 中 | 每次都查 DB 判断是否已赞，高频用户重复查询 |
| L5 | **下载计数耦合下载动作** | 中 | 下载记录接口若同步写DB，会拖慢下载响应 |

---

## 三、优化方案

### 3.1 方案选型对比

| 方案 | 核心思路 | 优点 | 缺点 | 选用？ |
|------|----------|------|------|--------|
| **A: 通知异步化 + 计数原子化** | Feign 通知改 @Async/事件总线；计数用 setSql 原子操作；已赞状态 Redis 缓存 | 改动小、解耦通知、原子计数 | 不解决行锁竞争 | ✅ 第一阶段 |
| B: 计数异步化(Redis累加+定时刷DB) | like_count 先写 Redis，定时任务批量刷 DB | 完全消除行锁 | 数据延迟、一致性复杂、崩溃丢计数 | ⚠️ 第二阶段（需评估） |
| C: 消息队列解耦 | 点赞只入 MQ，消费者异步处理写库+通知 | 彻底异步、削峰 | 引入 MQ 复杂度高、需保证幂等 | 🔜 后续（引入 RocketMQ 时） |

### 3.2 第一阶段优化（方案A）

**优化1：通知发送异步化**
- 将 Feign 通知调用从同步改为 `@Async`（需在 user-service Feign 接口加超时配置）
- 通知失败不影响主流程（catch + log warn）
- 前提：Spring Retry 已配置（见 user-service RetryConfig）

**优化2：计数原子操作确认**
- 确认 `setSql("like_count = like_count + 1")` 原子操作（架构文档要求）
- 检查是否存在读-改-写反模式

**优化3：已赞状态 Redis 缓存**
- key: `cache:post:like:{userId}:{postId}`，value: 0/1，TTL 1天
- 点赞/取消时同步更新缓存
- 查询点赞状态优先读缓存

**优化4：toggle 原子化**
- 利用唯一约束(user_id, post_id) + INSERT ... ON DUPLICATE KEY 或 try-catch 唯一约束异常
- 减少一次 SELECT 查询

**优化5：下载计数解耦**
- 下载记录写入异步化，下载响应不被计数拖慢

---

## 四、成功指标

| 指标 | 优化前（待测） | 目标 | 测量方式 |
|------|---------------|------|----------|
| 点赞 P95 | 待测 | < 200ms | JMeter 50并发 |
| 点赞 P99 | 待测 | < 500ms | JMeter 50并发 |
| 收藏 P95 | 待测 | < 200ms | JMeter 50并发 |
| 下载记录 P95 | 待测 | < 200ms | JMeter 50并发 |
| 通知发送失败影响 | 同步阻塞 | 异步不影响 | 压测中杀 user-service 观察 |

---

## 五、前置条件与风险

- **前置**：基线压测数据（baselines/）
- **风险1**：@Async 需要配置线程池，避免默认 SimpleAsyncTaskExecutor 无限创建线程
- **风险2**：Redis 缓存一致性，点赞/取消需同步更新缓存，缓存失败需降级
- **风险3**：异步通知丢失（user-service 宕机时），需评估是否需要本地补偿表

---

## 六、关联记录

- 优化完成后记录：`records/01-post-like-star-download.md`（待创建）
- 关联历史：无
- 关联 insights：[architecture/评论点赞通知组件交互](../../insights/architecture/2026-06-29_component-interaction_评论点赞通知.md)
