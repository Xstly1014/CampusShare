# 2026-06-27 - 浏览量/点赞/收藏/个人主页 全链路重构

> 本次为一次大型功能重构，涉及后端核心互动逻辑、个人主页体系、数据初始化、网关安全、前端多页面改造。

---

## 一、背景

用户需要调试帖子的浏览量、点赞、收藏功能，并要求：个人主页的浏览历史/我的收藏/我的点赞能正确联动后端数据（而非 mock 数据）；新增"我的帖子"功能；个人主页顶部统计改为作者维度的数据展示。

经排查发现原有实现存在多处严重缺陷（收藏/点赞只存 Redis 不写 DB、浏览量不记录历史、个人主页全用 mock 数据等），遂进行全链路重构。

---

## 二、问题与修复

### 问题1：收藏/点赞只存 Redis 不写 DB，数据7天后丢失

**根因**：`PostServiceImpl.toggleStar()` / `toggleLike()` 只在 Redis 设置键（7天TTL），从未写入 `post_stars` / `post_likes` 表。导致数据7天后消失、无法查询"我的收藏/点赞"、计数用读-改-写存在并发问题。

**修复**：DB 为数据源 + Redis 为缓存。toggle 操作先写 DB（insert/delete），再同步 Redis 缓存（30天TTL）。计数改为 `setSql("star_count = star_count + 1")` 原子操作。

**改动文件**：`PostServiceImpl.java`、`PostService.java`

---

### 问题2：浏览量不记录历史，无法联动个人主页

**根因**：`incrementViewCount()` 只做 Redis 计数累加，不记录 `view_history` 表，且不接收 userId。

**修复**：方法签名改为 `incrementViewCount(userId, postId)`；直接 DB 原子 `+1`（不再用 Redis batching）；在 `view_history` 表 upsert 记录（同用户同帖子只保留一条，更新最近浏览时间）。

**改动文件**：`PostServiceImpl.java`、`PostService.java`、`PostController.java`

---

### 问题3：帖子详情页收藏/点赞状态不持久化

**根因**：`PostDetailPage.tsx` 完全用 mock 数据（`postsData`），`handleStar` 只改本地 state 不调接口。导致点击高亮后返回再进入状态丢失。

**修复**：
- 后端新增 `GET /api/posts/{postId}/status` 接口，返回当前用户对该帖子的 `{starred, liked}` 状态
- 前端重写 `PostDetailPage.tsx`：进入时从后端获取详情+状态，点击调用真实 `toggleStar`/`toggleLike`，带乐观更新+失败回滚

**改动文件**：`PostController.java`、`PostStatus.java`（新增）、`PostDetailPage.tsx`、`api.ts`

---

### 问题4：列表页收藏按钮只变色不调接口

**根因**：`SchoolDetailPage.tsx` 的 `handleStar` 只操作本地 `Set` state，没调用后端接口。

**修复**：`handleStar` 改为 async 调用 `postApi.toggleStar`，带乐观更新+回滚；`fetchPosts` 加载列表后批量查询每个帖子的真实收藏状态。

**改动文件**：`SchoolDetailPage.tsx`

---

### 问题5：个人主页三个 Tab 全用 mock 数据，且为内嵌展示

**根因**：`ProfilePage.tsx` 的浏览历史/收藏/点赞用硬编码 mock 数据，且以 Tab 形式内嵌展示。

**修复**：
- 改为抖音风格入口按钮，点击跳转独立列表页
- 新建 `MyListPage.tsx` 通用列表页，根据路由参数（`history`/`starred`/`liked`/`mine`）调用对应后端接口
- 路由从3条独立路由合并为 `/profile/:type` 一条（修复 `useParams` 取不到 `type` 的 bug）
- 增加 `location.key` 依赖，从详情页返回时自动重新拉取数据（修复取消点赞/收藏后记录还在的问题）

**改动文件**：`ProfilePage.tsx`、`MyListPage.tsx`（新增）、`router/index.tsx`、`api.ts`

---

### 问题6：缺少个人主页接口（浏览历史/收藏/点赞列表）

**根因**：后端无对应接口。

**修复**：新增3个接口（均需鉴权）：
- `GET /api/posts/history` — 浏览历史，按 view_time 倒序
- `GET /api/posts/starred` — 我的收藏，按收藏时间倒序
- `GET /api/posts/liked` — 我的点赞，按点赞时间倒序

**改动文件**：`PostController.java`、`PostServiceImpl.java`、`PostService.java`

---

### 问题7：缺少"我的帖子"功能

**修复**：
- 后端新增 `GET /api/posts/mine` 接口，返回当前用户发布的帖子（按发布时间倒序）
- 前端 `MyListPage` 新增 `mine` 类型，`ProfilePage` 入口按钮新增"我的帖子"

**改动文件**：`PostController.java`、`PostServiceImpl.java`、`PostService.java`、`MyListPage.tsx`、`ProfilePage.tsx`、`api.ts`

---

### 问题8：个人主页统计数据与下方按钮功能重复

**根因**：顶部统计是"浏览/收藏/点赞/上传的数量"，和下方入口按钮语义重复。

**修复**：改为作者维度的纯展示统计（不可点击）：
- 总浏览（我的帖子被浏览总次数）
- 获赞（我的帖子被点赞总次数）
- 被收藏（我的帖子被收藏总次数）
- 帖子（我发布的帖子总数）

后端新增 `GET /api/posts/my-stats` 接口 + `UserPostStats` DTO，聚合查询。

**改动文件**：`PostController.java`、`UserPostStats.java`（新增）、`PostServiceImpl.java`、`PostService.java`、`ProfilePage.tsx`、`api.ts`

---

### 问题9：网关白名单过宽，star/like 等接口无需鉴权

**根因**：`JwtAuthenticationFilter` 白名单含 `/api/posts/`，用 `path::contains` 匹配导致所有 `/api/posts/**` 绕过 JWT。

**修复**：移除 `/api/posts/`，仅保留 `/api/posts/school` 公开。

**改动文件**：`JwtAuthenticationFilter.java`

---

### 问题10：数据初始化逻辑不符合调试需求

**根因**：`DataInitServiceImpl.initTestData()` 生成随机计数（0-5000）、学校ID用1-12（init.sql只有1-8）、含 PRD 未定义的 "note" 类型、无清空功能。

**修复**：
- 新增 `POST /api/admin/clear-posts` 接口 + `clearAllPosts()` 方法，物理删除 posts/post_stars/post_likes/view_history + 清 Redis
- `initTestData()` 修改：计数全0、学校1-8、仅 resource/discussion、默认10条/校

**改动文件**：`DataInitServiceImpl.java`、`DataInitService.java`、`DataInitController.java`

---

## 三、新增文件清单

| 文件 | 说明 |
|------|------|
| `entity/PostStar.java` | 收藏实体，映射 post_stars 表 |
| `entity/PostLike.java` | 点赞实体，映射 post_likes 表 |
| `entity/ViewHistory.java` | 浏览历史实体，映射 view_history 表 |
| `mapper/PostStarMapper.java` | 收藏 Mapper，含物理删除 |
| `mapper/PostLikeMapper.java` | 点赞 Mapper，含物理删除 |
| `mapper/ViewHistoryMapper.java` | 浏览历史 Mapper，含物理删除 |
| `dto/PostStatus.java` | 帖子状态 DTO（starred/liked） |
| `dto/UserPostStats.java` | 用户帖子统计 DTO |
| `frontend/.../pages/MyListPage.tsx` | 通用列表页（浏览历史/收藏/点赞/我的帖子） |

---

## 四、Redis 缓存设计变更

| Key | 变更前 | 变更后 |
|-----|--------|--------|
| `post:view:{postId}` | Redis 计数累加，每10次落DB | **已移除**，改为直接 DB 原子 +1 |
| `post:star:{postId}:{userId}` | 唯一数据源，7天TTL | DB为源+Redis缓存，30天TTL |
| `post:like:{postId}:{userId}` | 唯一数据源，7天TTL | DB为源+Redis缓存，30天TTL |

> 浏览量改为直接 DB 操作，便于调试观察；收藏/点赞改为 DB 持久化 + Redis 缓存加速状态查询。

---

## 五、接口清单变更

### 新增接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/posts/{postId}/status` | GET | 查询当前用户对帖子的收藏/点赞状态 |
| `/api/posts/history` | GET | 浏览历史列表 |
| `/api/posts/starred` | GET | 我的收藏列表 |
| `/api/posts/liked` | GET | 我的点赞列表 |
| `/api/posts/mine` | GET | 我的帖子列表 |
| `/api/posts/my-stats` | GET | 我的帖子统计数据 |
| `/api/admin/clear-posts` | POST | 清空所有帖子及关联数据 |

### 修改接口
| 接口 | 变更 |
|------|------|
| `GET /api/posts/{postId}` | 改为需登录，记录浏览历史，浏览量原子+1 |
| `POST /api/admin/init-test-data` | 默认10条/校，计数全0，学校1-8，仅resource/discussion |

---

## 六、总结

| 问题 | 根因 | 修复 |
|------|------|------|
| 收藏/点赞7天丢失 | 只存Redis不写DB | DB为源+Redis缓存 |
| 浏览无历史记录 | 不写view_history表 | upsert浏览历史 |
| 详情页状态不持久 | 用mock数据不调接口 | 后端status接口+前端对接 |
| 列表页收藏不生效 | 只改本地state | 调用真实接口+批量查状态 |
| 个人主页全mock | 硬编码假数据 | 入口按钮+独立列表页对接后端 |
| 缺个人主页接口 | 后端无API | 新增history/starred/liked/mine/my-stats |
| 缺我的帖子功能 | 未实现 | 新增mine接口+列表页 |
| 统计与按钮重复 | 语义冗余 | 改为作者维度纯展示 |
| 网关白名单过宽 | /api/posts/匹配所有 | 仅保留/api/posts/school公开 |
| 测试数据计数非0 | 生成随机值 | 全0，学校1-8，去掉note |
| 无清空数据功能 | 缺少接口 | 新增clearAllPosts+/admin/clear-posts |
