# 2026-06-27 - 帖子编辑删除、评论点赞/删除/楼中楼、设置页面、时区修复

> 本次涵盖帖子编辑/删除、评论系统完善（点赞/删除/楼中楼回复）、个人主页设置功能、多处 Bug 修复（时区、头像、列表资料数）、文档下载完善。

---

## 一、新增功能

### 1.1 帖子编辑与删除
- `PUT /api/posts/{postId}` 编辑帖子（仅作者，可改标题/内容/文件）
- `DELETE /api/posts/{postId}` 逻辑删除帖子（仅作者）
- 前端帖子详情页顶部显示编辑/删除按钮（仅作者可见），编辑弹窗修改标题和内容，删除用自定义确认弹窗

### 1.2 评论点赞
- 新增 `comment_likes` 表 + `CommentLike` 实体 + `CommentLikeMapper`
- `POST /api/posts/comments/{commentId}/like` 切换评论点赞（原子递增/递减 like_count）
- 获取评论列表时后端批量返回 `liked` 状态

### 1.3 评论删除
- `DELETE /api/posts/comments/{commentId}` 删除评论（评论作者或帖子作者可删）
- 删除后递减 comment_count + 清理 comment_likes
- 自己删除的评论从"我的回复"中移除

### 1.4 楼中楼回复
- 所有回复挂在根评论的 parentId 下，按时间正序排列
- 显示"用户名 回复 @被回复用户名"
- 楼内回复也可被回复（parentId 始终取根评论 ID）

### 1.5 个人主页设置功能
- **账号与安全**：显示账号信息（用户名/邮箱/手机号）、修改密码入口、退出登录（自定义确认弹窗）
- **隐私设置**：公开帖子/收藏/点赞/浏览历史/可被搜索等开关
- **通用设置**：消息通知开关、语言、清除缓存
- **帮助与反馈**：常见问题 FAQ + 意见反馈输入框

### 1.6 文档下载完善
- 下载改为 fetch + blob 方式，支持错误处理和成功提示
- 文件大小显示优化（KB/MB 自适应）

---

## 二、Bug 修复

### 2.1 时区问题（显示8小时前）
**根因**：Docker 容器内 JVM 默认 UTC，`LocalDateTime.now()` 返回 UTC 时间。
**修复**：Dockerfile 两个 stage 安装 tzdata + 设置 `TZ=Asia/Shanghai`；docker-compose.yml 环境变量加 TZ。

### 2.2 列表页作者头像/昵称不对
**根因**：列表接口返回原始 Post 只有 authorId，前端用 dicebear 随机头像和 authorId 前8位。
**修复**：新增 PostListDTO 含 authorName/authorAvatar；所有列表接口通过 enrichWithAuthor 批量查询用户信息。

### 2.3 学校详情页资料数不对
**根因**：用 schools.json 硬编码假数据。
**修复**：从 getSchoolPostCounts 接口获取真实帖子数。

### 2.4 浏览器 alert/confirm 替换
**根因**：删除评论/帖子用浏览器原生 confirm()。
**修复**：改为自定义确认弹窗组件。

---

## 三、改动文件清单

### 后端新增
- `PostListDTO.java`、`PostDetailDTO.java`、`CommentLike.java`、`CommentLikeMapper.java`

### 后端修改
- `PostController.java`（编辑/删除接口、所有列表接口返回 PostListDTO、getPostDetail 返回 PostDetailDTO）
- `PostService.java`/`PostServiceImpl.java`（editPost/deletePost/getSchoolPostCounts）
- `CommentController.java`（删除/点赞/我的回复接口）
- `CommentService.java`/`CommentServiceImpl.java`（删除/点赞/状态查询/权限）
- `CommentDTO.java`（schoolId/replyToUsername/liked/isAuthor）
- `init.sql`（comment_likes 表）
- `Dockerfile`（时区设置）
- `docker-compose.yml`（TZ 环境变量）

### 前端新增
- `SettingsPage.tsx`（设置页面）

### 前端修改
- `PostDetailPage.tsx`（编辑/删除、评论点赞/删除/楼中楼、自定义弹窗、下载完善）
- `SchoolDetailPage.tsx`（真实资料数、作者信息、时间格式）
- `MyListPage.tsx`（作者信息、时间格式、评论跳转 schoolId）
- `ProfilePage.tsx`（设置按钮跳转）
- `router/index.tsx`（设置路由）
- `api.ts`（编辑/删除/评论删除/评论点赞接口）
- `nginx.conf`（^~ 前缀匹配修复）

---

## 四、总结

| 类型 | 内容 |
|------|------|
| 新功能 | 帖子编辑/删除 |
| 新功能 | 评论点赞 |
| 新功能 | 评论删除（作者+博主） |
| 新功能 | 楼中楼回复 |
| 新功能 | 个人主页设置（账号/隐私/通用/帮助） |
| 新功能 | 文档下载完善 |
| Bug修复 | 时区8小时偏移（Docker TZ） |
| Bug修复 | 列表页作者头像/昵称 |
| Bug修复 | 学校详情页资料数 |
| Bug修复 | 浏览器 alert/confirm 替换 |
