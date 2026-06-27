# 2026-06-27 - 重构浏览量/点赞/收藏功能 + 数据清理重生成

## 背景
用户需要调试帖子的浏览量、点赞、收藏功能，要求：清空全部帖子数据后重新生成（每校10条，计数全0），并确保浏览/点赞/收藏能正确联动个人主页（浏览历史/我的收藏/我的点赞）。

---

## 问题1：收藏/点赞只存Redis不写DB，数据7天后丢失

### 根因
`PostServiceImpl.toggleStar()` 和 `toggleLike()` 只在 Redis 中设置 `post:star:{postId}:{userId}` 和 `post:like:{postId}:{userId}` 键（7天TTL），**从未写入 `post_stars` 和 `post_likes` 表**。导致：
- 7天后收藏/点赞数据自动消失
- 无法查询"我的收藏"/"我的点赞"（DB无数据）
- 计数通过读-改-写（非原子操作）存在并发问题

### 修复
- **DB为数据源**：toggleStar/toggleLike 改为操作 `post_stars`/`post_likes` 表（insert/delete）
- **Redis作缓存**：操作DB后同步更新Redis缓存（30天TTL），`isStarredBy`/`isLikedBy` 先查Redis再回源DB
- **原子计数**：用 `LambdaUpdateWrapper.setSql("star_count = star_count + 1")` 替代读-改-写，避免并发问题
- 新增 `isStarredBy`/`isLikedBy` 方法供前端查询当前用户对某帖子的状态

### 改动文件
- `PostServiceImpl.java`
- `PostService.java`

---

## 问题2：浏览量不记录历史，无法联动个人主页

### 根因
`incrementViewCount()` 只做 Redis 计数累加，**不记录 `view_history` 表**，且不接收 `userId` 参数。导致：
- 浏览帖子不会出现在个人主页的"浏览历史"中
- 计数逻辑（每10次刷DB）有累积bug

### 修复
- 方法签名改为 `incrementViewCount(String userId, String postId)`
- **原子+1**：直接 `setSql("view_count = view_count + 1")` 更新DB，不再用Redis batching
- **浏览历史upsert**：查 `view_history` 表是否已有 `(post_id, user_id)` 记录，有则更新 `view_time`，无则插入。保证每个帖子在历史中只出现一条（最近浏览时间）
- `PostController.getPostDetail` 改为从 Authorization 头提取 userId 传入

### 改动文件
- `PostServiceImpl.java`
- `PostService.java`
- `PostController.java`

---

## 问题3：缺少个人主页接口（浏览历史/收藏/点赞列表）

### 根因
`ProfilePage.tsx` 三个Tab（浏览历史/我的收藏/我的点赞）全用 mock 数据，后端无对应接口。

### 修复
新增3个接口（均需鉴权）：
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/posts/history` | GET | 查询用户浏览历史，按 view_time 倒序 |
| `/api/posts/starred` | GET | 查询用户收藏的帖子，按收藏时间倒序 |
| `/api/posts/liked` | GET | 查询用户点赞的帖子，按点赞时间倒序 |

每个接口实现逻辑：先查关联表（view_history/post_stars/post_likes）获取 post_id 列表 → 再查 posts 表 → 保持顺序并过滤已删除帖子。

### 改动文件
- `PostController.java`
- `PostServiceImpl.java`
- `PostService.java`

---

## 问题4：网关白名单过宽，star/like等接口无需鉴权

### 根因
`JwtAuthenticationFilter` 白名单含 `/api/posts/`，由于使用 `path::contains` 匹配，导致 `/api/posts/{id}/star`、`/api/posts/{id}/like`、`/api/posts/history` 等需鉴权接口全部绕过JWT验证。

### 修复
- 移除 `/api/posts/` 白名单条目
- 保留 `/api/posts/school`（公开列表接口）
- 其余 `/api/posts/**` 接口均需鉴权

### 改动文件
- `JwtAuthenticationFilter.java`

---

## 问题5：数据初始化逻辑不符合调试需求

### 根因
`DataInitServiceImpl.initTestData()` 存在：
- 浏览量/点赞/收藏生成随机值（0-5000），不利于调试观察
- 学校ID用1-12，但 `init.sql` 只插入了1-8
- 包含PRD未定义的 "note" 类型
- 无清空数据功能

### 修复
- **新增 `clearAllPosts()`**：物理删除 posts/post_stars/post_likes/view_history 全表数据 + 清除Redis相关键
- **新增 `POST /admin/clear-posts` 接口**
- `initTestData()` 修改：
  - view_count/star_count/like_count/comment_count 全部设为0
  - 学校ID改为1-8（匹配init.sql）
  - POST_TYPES 只保留 resource/discussion（匹配PRD）
  - 默认每校生成10条（原默认500）

### 数据初始化接口
| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/api/admin/clear-posts` | POST | 无 | 清空所有帖子及关联数据 |
| `/api/admin/init-test-data` | POST | postsPerSchool(默认10) | 生成测试数据 |

### 改动文件
- `DataInitServiceImpl.java`
- `DataInitService.java`
- `DataInitController.java`

---

## 新增文件
| 文件 | 说明 |
|------|------|
| `entity/PostStar.java` | 收藏实体，映射 post_stars 表 |
| `entity/PostLike.java` | 点赞实体，映射 post_likes 表 |
| `entity/ViewHistory.java` | 浏览历史实体，映射 view_history 表 |
| `mapper/PostStarMapper.java` | 收藏Mapper，含物理删除 |
| `mapper/PostLikeMapper.java` | 点赞Mapper，含物理删除 |
| `mapper/ViewHistoryMapper.java` | 浏览历史Mapper，含物理删除 |

---

## 数据库表说明
本次改动**无需新增表**，以下表在 `init.sql` 中已存在但此前未被使用：
- `post_stars` (id, post_id, user_id, create_time) — 帖子收藏
- `post_likes` (id, post_id, user_id, create_time) — 帖子点赞
- `view_history` (id, post_id, user_id, view_time) — 浏览历史

## Redis 缓存设计
| Key | 值 | TTL | 用途 |
|-----|----|----|------|
| `post:star:{postId}:{userId}` | "1" | 30天 | 收藏状态缓存（DB为源） |
| `post:like:{postId}:{userId}` | "1" | 30天 | 点赞状态缓存（DB为源） |

> 浏览量不再使用Redis，改为直接DB原子+1，便于调试观察。

---

## 总结
| 问题 | 根因 | 修复 |
|------|------|------|
| 收藏/点赞7天丢失 | 只存Redis不写DB | DB为源+Redis缓存 |
| 浏览无历史记录 | 不写view_history表 | upsert浏览历史 |
| 个人主页无接口 | 后端缺少API | 新增history/starred/liked接口 |
| 网关白名单过宽 | /api/posts/匹配所有 | 仅保留/api/posts/school公开 |
| 测试数据计数非0 | 生成随机值 | 全部设为0，学校1-8，去掉note类型 |
| 无清空数据功能 | 缺少接口 | 新增clearAllPosts + /admin/clear-posts |
