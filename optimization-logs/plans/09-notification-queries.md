# 优化计划 09：通知查询族（高频轮询）

> **优先级：P1** | **服务：user-service(8081)** | **接口数：2**

---

## 一、业务背景

### 1.1 业务背景类型：主动需求型（用户/业务方驱动）+ 被动场景型

**为什么现在做**：前端导航栏每 30 秒轮询 `/notifications/unread-count` 刷新红点，是**全站最高频的读接口**。通知列表 `/notifications/feed` 聚合了 LIKE/STAR/FOLLOW/COMMENT/REPLY 多类型通知，需跨类型组装。高频轮询 + COUNT 查询 + 聚合组装，延迟易劣化。

**如果不做会怎样**：
- 全站在线用户每30秒一次 COUNT 查询，DB 压力持续高位
- 通知列表聚合查询慢，用户感知卡顿
- 轮询占用 Tomcat 线程，挤占其他请求

**做成之后意味着什么**：未读数缓存后 DB 零压力（仅缓存失效时查），通知列表 P95 < 200ms。

### 1.2 涉及接口

| # | 接口 | 方法 | 路径 | 场景 |
|---|------|------|------|------|
| 1 | 未读通知数 | GET | `/notifications/unread-count` | 高频轮询(30s)、COUNT |
| 2 | 通知列表 | GET | `/notifications/feed` | 聚合、分页、跨类型 |

---

## 二、当前实现分析

> ⚠️ 优化前需 `Read` NotificationController + NotificationServiceImpl 确认

### 2.1 未读数疑似瓶颈

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| NU1 | **每次轮询都 COUNT 查 DB** | 高 | `SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=0`，高频 |
| NU2 | **无缓存** | 高 | 未读数变更频率低（仅收到新通知/标记已读时变），适合缓存 |
| NU3 | **轮询占用线程** | 中 | 30s 一次 × 在线用户数 = 持续线程占用 |

### 2.2 通知列表疑似瓶颈

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| NL1 | **多类型聚合** | 中 | LIKE/STAR/FOLLOW/COMMENT/REPLY 聚合查询 |
| NL2 | **sender 信息 N+1** | 高 | 每条通知查 sender 信息（Feign 或本服务） |
| NL3 | **target_title 冗余** | 低 | 通知表冗余了 target_title，避免回查帖子 |
| NL4 | **无分页** | 中 | 是否分页需确认 |
| NL5 | **聚合查询 SQL** | 中 | feed 是 UNION 还是单表查询？需确认 |

---

## 三、优化方案

### 3.1 未读数优化

**优化1：Redis 缓存未读数**
- key: `cache:notification:unread:{userId}`，value: int
- 通知写入时 INCR；标记已读时 DECR
- TTL 无（永久，靠 INCR/DECR 维护）或长TTL(1天)兜底
- 查询直接读 Redis，不查 DB

**优化2：缓存降级**
- Redis 不可用时降级查 DB
- 缓存重建：查 DB 后写回缓存

### 3.2 通知列表优化

**优化1：sender 信息批量查**
- 收集 sender_id → 批量查（本服务 users 表，user-service 内部）
- 复用批量查询模式

**优化2：分页**
- 按 type 分组分页 + 时间倒序

**优化3：通知列表短缓存**
- TTL 10s（用户可接受10秒延迟）

### 3.3 轮询优化（前端）

> 前端已用 TanStack Query refetchInterval（Phase 1.4 完成）

- 页面不可见时自动暂停（已实现）
- 可考虑 WebSocket 推送替代轮询（Phase 5.x 待做）

### 3.4 方案选型

| 方案 | 核心思路 | 选用？ |
|------|----------|--------|
| **A: 未读数 Redis 缓存 + 列表批量查** | 高频查询走缓存、列表批量查 | ✅ |
| B: WebSocket 推送 | 替代轮询 | 🔜 后续 Phase 5.x |
| C: 通知列表物化视图 | DB 物化视图 | ❌ MySQL 不支持 |

---

## 四、成功指标

| 指标 | 优化前（待测） | 目标 | 测量方式 |
|------|---------------|------|----------|
| 未读数 P95（缓存命中） | 待测 | < 10ms | JMeter 100并发 |
| 未读数 DB 查询次数 | 每次轮询都查 | 0（仅缓存失效） | Redis stats |
| 通知列表 P95 | 待测 | < 200ms | JMeter |
| 通知列表 DB/网络请求 | 待测 | < 3次 | 链路追踪 |

---

## 五、前置条件与风险

- **前置**：基线压测数据；确认通知表结构与查询模式
- **风险1**：未读数缓存一致性，INCR/DECR 必须与通知写入/标记已读原子
- **风险2**：通知列表 sender 信息若跨服务（sender 在 user 表，user-service 内部可直查）
- **风险3**：缓存击穿（热点用户），可加互斥锁

---

## 六、关联记录

- 优化完成后记录：`records/09-notification-queries.md`（待创建）
- 关联计划：[01-帖子点赞](01-post-like-star-download.md)（点赞触发通知，INCR 配合）
- 关联前端：Phase 1.4 轮询规范化（已完成）
