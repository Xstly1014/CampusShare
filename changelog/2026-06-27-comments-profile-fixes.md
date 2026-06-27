# 2026-06-27 - 评论功能、资料修改、首页资料数修复、头像Nginx修复、我的回复

> 本次开发新增评论系统和用户资料修改功能，修复首页资料数和头像显示两个 Bug，并新增"我的回复"功能。

---

## 一、新增功能

### 1.1 帖子评论功能

**需求**：帖子详情页可以查看和发表评论。

**实现**：
- 后端全新创建评论全栈：`Comment` 实体（映射 `comments` 表）、`CommentMapper`、`CommentDTO`（含用户名/头像）、`CommentService`/`CommentServiceImpl`、`CommentController`
- 发评论时原子递增帖子 `comment_count`
- 查评论列表时关联查询用户信息（用户名、头像）
- 前端 `PostDetailPage.tsx` 评论区对接后端：进入页面加载评论列表，底部输入框发评论（回车或点击发送），评论实时追加

**新增接口**：
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/posts/{postId}/comments` | GET | 获取帖子评论列表（按时间正序） |
| `/api/posts/{postId}/comments` | POST | 发表评论（需登录） |

### 1.2 个人主页资料修改

**需求**：头像修改成功后提示"头像修改成功"；昵称和简介在编辑弹窗中修改后点保存生效。

**实现**：
- 后端新增 `PUT /api/users/me` 接口 + `UpdateProfileRequest` DTO，支持更新 username/bio/avatarUrl（含用户名重复检查）
- 前端 `ProfilePage.tsx`：
  - 头像：选图→本地预览→上传文件→调接口保存 avatarUrl→提示"头像修改成功"
  - 昵称/简介：编辑弹窗修改→点保存调接口→提示"资料保存成功"→刷新页面
  - 进入页面时从后端 `GET /users/me` 获取最新用户信息

### 1.3 我的回复功能

**需求**：个人主页新增"我的回复"入口，可查看自己发表的所有评论。

**实现**：
- 后端 `CommentService` 新增 `getCommentsByUserId()`，`CommentController` 新增 `GET /api/posts/my-comments` 接口
- 前端 `ProfilePage` 入口按钮新增"我的回复"（带评论数），`MyListPage` 新增 `comments` 类型专用渲染（评论卡片样式）

---

## 二、Bug 修复

### 2.1 首页学校资料数不对

**根因**：`HomePage.tsx` 使用 `schools.json` 中的硬编码假数据（如 1256、1890），与数据库实际帖子数无关。

**修复**：
- 后端新增 `GET /api/posts/school-counts` 接口，返回每个学校的真实帖子数
- 前端进入首页时调用该接口，用真实数据替换假 `resourceCount`

### 2.2 头像上传后不显示（404）

**根因**：nginx 配置的 location 优先级问题。静态资源 location `~* \.(png|jpg|...)$` 是正则匹配，优先级高于前缀匹配 `location /api/`。请求 `/api/files/xxx.png` 时被正则 location 拦截，nginx 直接从本地 `/usr/share/nginx/html/api/files/xxx.png` 读取 → 404，请求根本没走到 `/api/` 代理转发。

**修复**：将 `location /api/` 改为 `location ^~ /api/`。nginx 中 `^~` 前缀匹配优先级高于正则匹配，确保所有 `/api/` 开头的请求都走代理到 gateway。

---

## 三、改动文件清单

### 后端新增（7个文件）
| 文件 | 说明 |
|------|------|
| `entity/Comment.java` | 评论实体，映射 comments 表 |
| `mapper/CommentMapper.java` | 评论 Mapper |
| `dto/CommentDTO.java` | 评论 DTO，含用户名和头像 |
| `dto/UpdateProfileRequest.java` | 用户资料更新请求 DTO |
| `service/CommentService.java` | 评论服务接口 |
| `service/impl/CommentServiceImpl.java` | 评论服务实现 |
| `controller/CommentController.java` | 评论控制器 |

### 后端修改（6个文件）
| 文件 | 改动 |
|------|------|
| `controller/UserController.java` | 新增 `PUT /users/me` 接口 |
| `service/UserService.java` | 新增 `updateProfile` 方法 |
| `service/UserServiceImpl.java` | 实现资料更新逻辑（含用户名重复检查） |
| `controller/PostController.java` | 新增 `GET /posts/school-counts` 接口 |
| `service/PostService.java` | 新增 `getSchoolPostCounts` 方法 |
| `service/PostServiceImpl.java` | 实现学校帖子数统计 |
| `service/impl/CommentServiceImpl.java` | 新增 `getCommentsByUserId` 方法 |
| `controller/CommentController.java` | 新增 `GET /posts/my-comments` 接口 |

### 前端修改（5个文件）
| 文件 | 改动 |
|------|------|
| `services/api.ts` | 新增评论/用户更新/学校帖子数/我的回复接口方法 |
| `pages/PostDetailPage.tsx` | 评论区对接后端，加载+发表评论 |
| `pages/ProfilePage.tsx` | 头像上传+资料保存对接后端，新增"我的回复"入口 |
| `pages/HomePage.tsx` | 动态获取学校真实帖子数 |
| `pages/MyListPage.tsx` | 新增 comments 类型，评论卡片渲染 |
| `nginx.conf` | `/api/` location 改为 `^~` 前缀匹配 |

---

## 四、接口清单变更

### 新增接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/posts/{postId}/comments` | GET | 获取帖子评论列表 |
| `/api/posts/{postId}/comments` | POST | 发表评论 |
| `/api/posts/my-comments` | GET | 我的回复列表 |
| `/api/posts/school-counts` | GET | 各学校帖子数统计 |
| `/api/users/me` | PUT | 更新用户资料（昵称/简介/头像） |

---

## 五、总结

| 类型 | 内容 |
|------|------|
| 新功能 | 帖子评论（查看+发表） |
| 新功能 | 个人主页资料修改（头像/昵称/简介） |
| 新功能 | 我的回复（查看自己所有评论） |
| Bug修复 | 首页学校资料数改为从后端动态获取 |
| Bug修复 | 头像404（nginx location优先级，改用 ^~ 前缀匹配） |
