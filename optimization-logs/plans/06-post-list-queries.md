# 优化计划 06：帖子列表查询族（读密集）

> **优先级：P1** | **服务：post-service(8082)** | **接口数：6（含历史已优化复检）**

---

## 一、业务背景

### 1.1 业务背景类型：被动场景型（系统/运行时驱动）

**为什么现在做**：帖子列表是最高频的读接口群（首页、学校详情、分类详情、个人主页），历史已优化 `/posts/school/{id}` 和 `/posts/school-counts`（见 insights），但 `/categories/{id}/posts`、`/posts/mine`、`/posts/starred`、`/posts/liked`、`/posts/history` 等接口可能存在同类 N+1 问题。需统一复检并补齐。

**如果不做会怎样**：未优化的列表接口在高频访问时 N+1 查询（61次/页）导致延迟劣化、DB 连接池耗尽。

**做成之后意味着什么**：所有列表接口统一批量查询模式，单页 DB/网络请求 < 5 次。

### 1.2 涉及接口

| # | 接口 | 方法 | 路径 | 场景 | 历史 |
|---|------|------|------|------|------|
| 1 | 学校帖子列表 | GET | `/posts/school/{schoolId}` | N+1、分页、大字段 | ✅已优化(复检) |
| 2 | 分类帖子列表 | GET | `/categories/{categoryId}/posts` | N+1、分页 | ❌ |
| 3 | 我的帖子 | GET | `/posts/mine` | 分页、N+1 | ❌ |
| 4 | 我的收藏 | GET | `/posts/starred` | 分页、N+1、跨表JOIN(post_stars→posts) | ❌ |
| 5 | 我的点赞 | GET | `/posts/liked` | 分页、N+1、跨表JOIN(post_likes→posts) | ❌ |
| 6 | 浏览历史 | GET | `/posts/history` | 分页、N+1、跨表JOIN(view_history→posts) | ❌ |

---

## 二、当前实现分析

### 2.1 已知历史优化（学校帖子列表）

来自 [insights/performance 接口性能SQL聚合缓存N+1](../../insights/performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md)：
- ✅ SQL GROUP BY 替代内存聚合
- ✅ Redis 缓存 school-counts
- ✅ 批量查询作者/分类/子分类（N+1 修复，61次→4次）
- ✅ 排除 content 大字段
- ✅ HashSet 去重

### 2.2 复检要点（学校帖子列表）

| 复检项 | 说明 |
|--------|------|
| 复检1 | 确认批量查询模式仍生效（无回退） |
| 复检2 | 确认 content 排除仍生效 |
| 复检3 | 确认 Redis 缓存命中率 |
| 复检4 | 复测 P95 是否仍 < 200ms |

### 2.3 疑似瓶颈（未优化接口）

> ⚠️ 优化前需 `Read` PostServiceImpl 确认

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| PL1 | **我的收藏/点赞/历史 跨表查询** | 高 | post_stars/post_likes/view_history → posts JOIN，是否用 MyBatis Plus 联表或内存组装？ |
| PL2 | **N+1 查询** | 高 | 作者信息/分类信息是否逐条查（未用批量） |
| PL3 | **content 大字段** | 中 | 列表查询是否 SELECT * 含 content |
| PL4 | **无分页缓存** | 低 | 个人列表（我的帖子）变更频率低，可缓存 |
| PL5 | **分类帖子列表** | 中 | 是否复用学校列表的批量查询模式 |

### 2.4 跨表查询模式分析

「我的收藏/点赞/历史」是关联表(post_stars/post_likes/view_history) → posts 的查询：
- ❌ 禁止跨服务 JOIN（微服务边界，但同库内可 JOIN）
- ✅ 正确模式：先查关联表取 postId 列表 → 批量查 posts → 批量查作者/分类

---

## 三、优化方案

### 3.1 优化策略

| 接口 | 优化项 | 方案 |
|------|--------|------|
| 学校帖子列表(复检) | 验证历史优化 | 复测 P95，确认无回退 |
| 分类帖子列表 | 复用批量查询模式 | 批量查作者/分类/子分类 |
| 我的帖子 | 批量查询+排除content | 同上 |
| 我的收藏 | 关联表→postId列表→批量查posts | 两步查询模式 |
| 我的点赞 | 同收藏 | 同上 |
| 浏览历史 | 同收藏 | 同上 |

### 3.2 通用优化模式（复用历史）

```java
// 1. 查 postId 列表（关联表，分页）
List<String> postIds = postStarMapper.selectList(...).stream().map(PostStar::getPostId).collect();

// 2. 批量查 posts（排除 content）
List<Post> posts = postMapper.selectBatchIds(postIds); // 排除 content

// 3. HashSet 收集 ID（去重 O(n)）
Set<String> authorIds = posts.stream().map(Post::getAuthorId).collect(toSet());
Set<String> categoryIds = ...;

// 4. 批量查作者/分类/子分类
Map<String, UserSimpleInfo> authorMap = userFeignClient.getBatchUserInfo(authorIds);
Map<String, Category> categoryMap = categoryMapper.selectBatchIds(categoryIds)...;

// 5. 组装 DTO
```

### 3.3 方案选型

| 方案 | 核心思路 | 选用？ |
|------|----------|--------|
| **A: 复用批量查询模式** | 统一应用历史已验证的批量查询+排除content+HashSet去重 | ✅ |
| B: 列表 Redis 缓存 | 个人列表(我的帖子)变更低频，TTL 1分钟 | ⚠️ 第二阶段 |
| C: 读副本 | 读写分离 | ❌ 当前不需要 |

---

## 四、成功指标

| 指标 | 优化前（待测） | 目标 | 测量方式 |
|------|---------------|------|----------|
| 各列表接口 P95 | 待测 | < 500ms | JMeter 20并发 |
| 单页 DB/网络请求 | 待测(可能61次) | < 5次 | 链路追踪 |
| 学校列表 P95（复检） | 历史239ms | < 200ms | JMeter |

---

## 五、前置条件与风险

- **前置**：基线压测数据
- **风险1**：跨表查询（关联表→posts）的性能取决于数据量，需确认索引
- **风险2**：个人列表缓存一致性（编辑帖子后缓存未失效）

---

## 六、关联记录

- 优化完成后记录：`records/06-post-list-queries.md`（待创建）
- 关联历史：[insights/performance 接口性能SQL聚合缓存N+1](../../insights/performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md)
- 关联历史：[insights/performance 数据库复合索引设计](../../insights/performance/2026-06-29_optimization_数据库复合索引设计.md)
