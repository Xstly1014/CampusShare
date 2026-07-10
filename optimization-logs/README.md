# Optimization Logs — 接口延迟优化专项

> **本目录为本地文档，不提交到 Git（已在 .gitignore）。**
> 本专项聚焦「接口延迟优化」，是 AGENT-WORKFLOW.md 第十三章「性能优化教学模式」的落地工作目录。

---

## 一、本目录是什么

这是 CampusShare 项目**接口延迟优化专项**的工作目录，用于：

1. **规划**（`plans/`）：为每个接口编写优化计划——业务背景、当前实现、疑似瓶颈、优化方案、成功指标
2. **记录**（`records/`）：优化完成后填写详细过程——面试 STAR 格式，可追溯每个接口的「业务背景→延迟根因→优化方案→量化结果」
3. **基线**（`baselines/`）：存放优化前的压测基线数据，作为优化效果对比的基准

**工作节奏**：先规划（写 plans）→ 基线压测（填 baselines）→ 逐接口优化（写 records）→ 验证对比

---

## 二、目录结构

```
optimization-logs/
├── README.md                         ← 本文件（总索引）
├── plans/                            ← 子文件夹1：各接口优化计划（前瞻性）
│   ├── 00-master-plan.md             ← 总计划：接口清单 + 优先级 + 排期 + 依赖
│   ├── 01-post-like-star-download.md ← 高并发写：点赞/收藏/下载记录
│   ├── 02-comment-like.md            ← 高并发写：评论点赞
│   ├── 03-file-upload.md             ← IO密集：文件上传
│   ├── 04-post-create.md             ← 复合：发帖（文件上传+DB+跨服务）
│   ├── 05-post-detail.md             ← 读+写计数：帖子详情
│   ├── 06-post-list-queries.md       ← 读密集：帖子列表（学校/分类/我的）
│   ├── 07-school-counts.md          ← 聚合查询：学校帖子计数
│   ├── 08-comment-list-create.md     ← 评论列表 + 创建
│   ├── 09-notification-queries.md    ← 通知：未读数 + 列表（高频轮询）
│   ├── 10-message-ops.md            ← 私信：列表 + 发送
│   ├── 11-user-profile-stats.md     ← 用户主页 + 统计聚合
│   ├── 12-auth-login.md              ← 认证：登录
│   └── 13-agent-chat.md             ← AI：SSE流式对话
├── records/                          ← 子文件夹2：优化详细记录（面试STAR格式）
│   ├── README.md                     ← 记录模板 + 索引
│   ├── 01-post-like-star-download.md ← 批次1：点赞/收藏/下载（已完成）
│   ├── 02-comment-like.md            ← 批次2：评论点赞（已完成）
│   └── 03-file-upload.md             ← 批次3：文件上传（已完成）
└── baselines/                        ← 子文件夹3：基线压测数据
    ├── README.md                     ← 测试环境 + 数据格式说明
    ├── 01-like-star-download-baseline.md
    ├── 02-comment-like-baseline.md
    └── 03-file-upload-baseline.md
```

---

## 三、与 insights/ 的关系（兼容合并方案）

项目已有 `insights/` 目录（遵循 `PROJECT_INSIGHTS.md` v2.1 STAR 规范），其中 `insights/performance/` 存放历史性能优化记录。本目录与 insights 的关系如下：

| 维度 | insights/performance/ | optimization-logs/ |
|------|----------------------|--------------------|
| **定位** | 项目级深度记录（架构/性能/可靠性/工程化） | 接口延迟优化专项工作目录 |
| **已有内容** | 3 条历史优化记录（见下表） | 本次专项的新记录 |
| **记录格式** | STAR 格式（PROJECT_INSIGHTS.md v2.1） | 相同 STAR 格式（兼容） |
| **新增接口优化记录** | ❌ 不再在此新增 | ✅ 所有新记录写在此处 |
| **交叉引用** | insights/README.md 指向本目录 | 本目录 README 指向 insights 历史 |

### insights/performance/ 历史记录（保留原位，作为参考）

| 文件 | 简介 | 关联接口 |
|------|------|----------|
| [2026-06-29_optimization_接口性能SQL聚合缓存N+1.md](../insights/performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md) | P95 16.3s→239ms，SQL GROUP BY + Redis 缓存 + 修复 N+1（61次/页→4次/页） | `/posts/school-counts`、`/posts/school/{id}` |
| [2026-06-29_optimization_JVM参数与Dockerfile修复.md](../insights/performance/2026-06-29_optimization_JVM参数与Dockerfile修复.md) | QPS 4→204，ENTRYPOINT exec→shell + JwtParser缓存 + 硬件适配 | 全局（JVM层面） |
| [2026-06-29_optimization_数据库复合索引设计.md](../insights/performance/2026-06-29_optimization_数据库复合索引设计.md) | P95 167ms→70ms，EXPLAIN + 复合索引 + A/B 压测 | `/posts/school/{id}` |

> **合并原则**：避免重复记录。新的接口优化记录只写在 `optimization-logs/records/`，`insights/performance/` 保留已有3条不动。`insights/README.md` 已添加指向本目录的链接。

---

## 四、接口清单速查（按优先级）

> 完整清单见 [plans/00-master-plan.md](plans/00-master-plan.md)

### P0 — 核心高并发/复杂接口（优先优化）

| # | 接口 | 方法 | 路径 | 复杂场景 | 状态 | 计划文件 |
|---|------|------|------|----------|------|----------|
| 1 | 帖子点赞 | POST | `/posts/{postId}/like` | 高并发写、原子计数、通知 | ✅已完成 | [01](plans/01-post-like-star-download.md) |
| 2 | 帖子收藏 | POST | `/posts/{postId}/star` | 高并发写、原子计数 | ✅已完成 | [01](plans/01-post-like-star-download.md) |
| 3 | 下载记录 | POST | `/posts/{postId}/download` | 高并发写、跨服务 | ✅已完成 | [01](plans/01-post-like-star-download.md) |
| 4 | 评论点赞 | POST | `/posts/comments/{commentId}/like` | 高并发写、原子计数 | ✅已完成 | [02](plans/02-comment-like.md) |
| 5 | 文件上传 | POST | `/files/upload` | 大文件IO、磁盘写 | ✅已完成 | [03](plans/03-file-upload.md) |
| 6 | 发帖 | POST | `/posts` | 文件上传+DB+跨服务 | ⬜待优化 | [04](plans/04-post-create.md) |
| 7 | 帖子详情 | GET | `/posts/{postId}` | 读+view_count写计数+详情组装 | ⬜待优化 | [05](plans/05-post-detail.md) |

### P1 — 读密集/聚合/轮询接口

| # | 接口 | 方法 | 路径 | 复杂场景 | 状态 | 计划文件 |
|---|------|------|------|----------|------|----------|
| 8 | 学校帖子列表 | GET | `/posts/school/{schoolId}` | N+1、分页、大字段 | 🔁复检 | [06](plans/06-post-list-queries.md) |
| 9 | 分类帖子列表 | GET | `/categories/{id}/posts` | N+1、分页 | ⬜待优化 | [06](plans/06-post-list-queries.md) |
| 10 | 我的帖子/收藏/点赞/历史 | GET | `/posts/{mine,starred,liked,history}` | 分页、N+1 | ⬜待优化 | [06](plans/06-post-list-queries.md) |
| 11 | 学校帖子计数 | GET | `/posts/school-counts` | 聚合查询、缓存 | 🔁复检 | [07](plans/07-school-counts.md) |
| 12 | 评论列表 | GET | `/posts/{postId}/comments` | 楼中楼组装、N+1 | ⬜待优化 | [08](plans/08-comment-list-create.md) |
| 13 | 评论创建 | POST | `/posts/{postId}/comments` | 跨服务通知、计数 | ⬜待优化 | [08](plans/08-comment-list-create.md) |
| 14 | 未读通知数 | GET | `/notifications/unread-count` | 高频轮询（30s） | ⬜待优化 | [09](plans/09-notification-queries.md) |
| 15 | 通知列表 | GET | `/notifications/feed` | 聚合、分页 | ⬜待优化 | [09](plans/09-notification-queries.md) |

### P2 — 常规接口

| # | 接口 | 方法 | 路径 | 复杂场景 | 状态 | 计划文件 |
|---|------|------|------|----------|------|----------|
| 16 | 私信列表 | GET | `/messages` | 分页、最近消息组装 | ⬜待优化 | [10](plans/10-message-ops.md) |
| 17 | 私信发送 | POST | `/messages` | 跨服务、通知 | ⬜待优化 | [10](plans/10-message-ops.md) |
| 18 | 用户主页 | GET | `/users/{userId}` | 跨服务聚合 | ⬜待优化 | [11](plans/11-user-profile-stats.md) |
| 19 | 收纳袋统计 | GET | `/posts/warehouse-stats` | 聚合 | 🔁数据已清理（待长期优化） | [11](plans/11-user-profile-stats.md) |
| 20 | 我的统计 | GET | `/posts/my-stats` | 聚合 | ⬜待优化 | [11](plans/11-user-profile-stats.md) |
| 21 | 登录 | POST | `/auth/login` | BCrypt、DB查询 | ⬜待优化 | [12](plans/12-auth-login.md) |
| 22 | AI对话 | POST | `/agent/chat` | SSE流式、外部API、RAG | ⬜待优化 | [13](plans/13-agent-chat.md) |

---

## 五、工作流（与 AGENT-WORKFLOW.md 第十三章联动）

```
1. 读 plans/00-master-plan.md 确定下一个要优化的接口
2. 读该接口的 plans/0X-xxx.md 了解优化方案
3. 在 baselines/ 跑基线压测，记录优化前数据（P50/P95/P99/QPS）
4. 按教学模式（第十三章）逐步优化：监控→发现→分析→修改→验证
5. 优化完成后，在 records/ 创建详细记录（STAR格式）
6. 更新本 README 的接口清单状态
7. 更新 changelog/
```

---

## 六、记录规范

`records/` 中的每条记录必须覆盖以下面试核心问题（STAR 映射）：

| 面试官问题 | 对应章节 | STAR |
|-----------|----------|------|
| 业务背景是什么？为什么现在做？不做会怎样？ | 业务背景 | S |
| 原本是什么原因造成了接口延迟？ | 延迟根因 | S+A |
| 这个接口是怎么优化的？方案是什么？ | 优化方案与实现 | A |
| 达成了什么结果？有数据支撑吗？ | 量化结果 | R |
| 有什么副作用/遗留问题/技术选型对比？ | tradeoff | A+R |

详细模板见 [records/README.md](records/README.md)。
