# CampusShare API 接口文档

> **文档版本**: v2.0
> **创建日期**: 2026-06-27
> **Base URL**: `/api`
> **数据格式**: JSON
> **字符编码**: UTF-8

---

## 一、通用规范

### 1.1 统一响应格式

所有接口返回统一的 JSON 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719456789000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | number | 状态码，200 表示成功 |
| `message` | string | 提示信息 |
| `data` | any | 业务数据 |
| `timestamp` | number | 服务器时间戳（毫秒） |

### 1.2 状态码说明

| Code | 说明 |
|------|------|
| 200 | 成功 |
| 3001 | 验证码已过期 |
| 3002 | 验证码错误 |
| 4001 | 参数错误 |
| 4002 | 用户不存在 |
| 4003 | 密码错误 |
| 4004 | 用户名已存在 |
| 4005 | 账号已被注册 |
| 4010 | Token 无效或过期 |
| 4030 | 无权限 |
| 5000 | 服务器内部错误 |

### 1.3 认证方式

需要登录的接口需在请求头中携带 Token：

```
Authorization: Bearer {token}
```

### 1.4 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码，从1开始 |
| `size` | int | 20 | 每页数量 |

---

## 二、认证模块

### 2.1 发送验证码

- **接口**: `POST /api/auth/send-code`
- **说明**: 发送注册/重置密码验证码
- **是否需要登录**: 否

**请求参数 (Query)**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `account` | string | 是 | 手机号或邮箱 |
| `type` | string | 否 | 类型：phone/email，默认 phone |

**响应示例**:

```json
{
  "code": 200,
  "message": "验证码发送成功",
  "data": null,
  "timestamp": 1719456789000
}
```

---

### 2.2 用户注册

- **接口**: `POST /api/auth/register`
- **说明**: 用户注册
- **是否需要登录**: 否

**请求体**:

```json
{
  "registerType": "phone",
  "account": "13800138000",
  "verifyCode": "123456",
  "username": "testuser",
  "password": "Test123456"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `registerType` | string | 是 | 注册类型：phone / email |
| `account` | string | 是 | 手机号或邮箱 |
| `verifyCode` | string | 是 | 6位验证码 |
| `username` | string | 是 | 用户名（3-20位） |
| `password` | string | 是 | 密码（6-20位，含字母数字） |

**响应示例**:

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "username": "testuser",
      "email": null,
      "phone": "13800138000",
      "avatarUrl": null,
      "bio": null,
      "schoolId": null,
      "createTime": "2026-06-27 12:00:00"
    }
  },
  "timestamp": 1719456789000
}
```

---

### 2.3 用户登录

- **接口**: `POST /api/auth/login`
- **说明**: 用户登录
- **是否需要登录**: 否

**请求体**:

```json
{
  "account": "13800138000",
  "password": "Test123456"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `account` | string | 是 | 手机号或邮箱 |
| `password` | string | 是 | 密码 |

**响应示例**: 同注册成功响应

---

### 2.4 重置密码

- **接口**: `POST /api/auth/reset-password`
- **说明**: 忘记密码，通过验证码重置
- **是否需要登录**: 否

**请求体**:

```json
{
  "account": "13800138000",
  "verifyCode": "123456",
  "newPassword": "NewPass123"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `account` | string | 是 | 手机号或邮箱 |
| `verifyCode` | string | 是 | 验证码 |
| `newPassword` | string | 是 | 新密码 |

---

### 2.5 获取当前用户信息

- **接口**: `GET /api/users/me`
- **说明**: 获取当前登录用户信息
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "username": "testuser",
    "email": "test@example.com",
    "phone": "13800138000",
    "avatarUrl": null,
    "bio": "这是一名测试用户",
    "schoolId": "3",
    "createTime": "2026-06-27 12:00:00"
  },
  "timestamp": 1719456789000
}
```

---

## 三、帖子模块

### 3.1 发布帖子

- **接口**: `POST /api/posts`
- **说明**: 发布新帖子（资源贴/讨论贴）
- **是否需要登录**: 是

**请求体**:

```json
{
  "schoolId": "1",
  "postType": "resource",
  "title": "高等数学期末复习资料",
  "content": "涵盖期末考试全部重点...",
  "fileUrl": "/files/20260627/uuid.pdf",
  "fileName": "高等数学复习资料.pdf",
  "fileType": "pdf",
  "fileSize": 2411776
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `schoolId` | string | 是 | 学校ID |
| `postType` | string | 是 | 类型：resource（资料贴）/ discussion（讨论贴） |
| `title` | string | 是 | 标题（最多100字） |
| `content` | string | 否 | 内容/描述（最多2000字） |
| `fileUrl` | string | 否 | 文件URL（资料贴必填） |
| `fileName` | string | 否 | 文件名 |
| `fileType` | string | 否 | 文件类型（扩展名） |
| `fileSize` | long | 否 | 文件大小（字节） |

**响应示例**:

```json
{
  "code": 200,
  "message": "发布成功",
  "data": {
    "id": "post-uuid-xxx",
    "schoolId": "1",
    "authorId": "user-uuid-xxx",
    "postType": "resource",
    "title": "高等数学期末复习资料",
    "content": "涵盖期末考试全部重点...",
    "fileUrl": "/files/20260627/uuid.pdf",
    "fileName": "高等数学复习资料.pdf",
    "fileType": "pdf",
    "fileSize": 2411776,
    "viewCount": 0,
    "starCount": 0,
    "likeCount": 0,
    "commentCount": 0,
    "status": 1,
    "createTime": "2026-06-27 12:00:00",
    "updateTime": "2026-06-27 12:00:00"
  },
  "timestamp": 1719456789000
}
```

---

### 3.2 获取帖子详情

- **接口**: `GET /api/posts/{postId}`
- **说明**: 获取帖子详细信息，同时浏览量 +1 并记录浏览历史（需登录）
- **是否需要登录**: 是

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

> **行为说明**: 调用此接口会原子递增 `view_count`，并在 `view_history` 表中 upsert 记录（同一用户对同一帖子只保留一条，更新为最近浏览时间），用于个人主页"浏览历史"展示。

**响应示例**: 同发布成功响应 data 结构（`viewCount` 已 +1）

---

### 3.3 获取学校帖子列表

- **接口**: `GET /api/posts/school/{schoolId}`
- **说明**: 分页获取指定学校的帖子列表
- **是否需要登录**: 否

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `schoolId` | string | 是 | 学校ID |

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `postType` | string | all | 类型过滤：all / resource / discussion |
| `sortType` | string | latest | 排序：latest（最新）/ hottest（最热）/ active（活跃） |
| `page` | int | 1 | 页码 |
| `size` | int | 20 | 每页数量 |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "post-uuid-xxx",
      "schoolId": "1",
      "authorId": "user-uuid-xxx",
      "postType": "resource",
      "title": "高等数学期末复习资料",
      "content": "涵盖期末考试全部重点...",
      "fileUrl": "/files/20260627/uuid.pdf",
      "fileName": "高等数学复习资料.pdf",
      "fileType": "pdf",
      "fileSize": 2411776,
      "viewCount": 156,
      "starCount": 28,
      "likeCount": 42,
      "commentCount": 15,
      "createTime": "2026-06-27 12:00:00"
    }
  ],
  "timestamp": 1719456789000
}
```

---

### 3.4 收藏/取消收藏帖子

- **接口**: `POST /api/posts/{postId}/star`
- **说明**: 切换收藏状态（已收藏则取消，未收藏则收藏）
- **是否需要登录**: 是

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": true,
  "timestamp": 1719456789000
}
```

> `data` 为 true 表示已收藏，false 表示已取消收藏

---

### 3.5 点赞/取消点赞帖子

- **接口**: `POST /api/posts/{postId}/like`
- **说明**: 切换点赞状态
- **是否需要登录**: 是

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

**响应示例**: 同收藏接口

---

### 3.6 查询帖子状态

- **接口**: `GET /api/posts/{postId}/status`
- **说明**: 查询当前用户对指定帖子的收藏/点赞状态（用于页面初始化显示）
- **是否需要登录**: 否（未登录时返回 starred=false, liked=false）

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "starred": true,
    "liked": false
  },
  "timestamp": 1719456789000
}
```

---

### 3.7 获取浏览历史

- **接口**: `GET /api/posts/history`
- **说明**: 获取当前用户的浏览历史，按最近浏览时间倒序
- **是否需要登录**: 是

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码 |
| `size` | int | 20 | 每页数量 |

**响应示例**: 同帖子列表（`data` 为 Post 数组）

---

### 3.8 获取我的收藏

- **接口**: `GET /api/posts/starred`
- **说明**: 获取当前用户收藏的帖子，按收藏时间倒序
- **是否需要登录**: 是

**查询参数**: 同 3.7

**响应示例**: 同帖子列表

---

### 3.9 获取我的点赞

- **接口**: `GET /api/posts/liked`
- **说明**: 获取当前用户点赞的帖子，按点赞时间倒序
- **是否需要登录**: 是

**查询参数**: 同 3.7

**响应示例**: 同帖子列表

---

### 3.10 获取我的帖子

- **接口**: `GET /api/posts/mine`
- **说明**: 获取当前用户发布的帖子，按发布时间倒序
- **是否需要登录**: 是

**查询参数**: 同 3.7

**响应示例**: 同帖子列表

---

### 3.11 获取我的帖子统计

- **接口**: `GET /api/posts/my-stats`
- **说明**: 获取当前用户发布帖子的聚合统计数据（总浏览/获赞/被收藏/帖子数），用于个人主页展示
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalViews": 1256,
    "totalLikes": 89,
    "totalStars": 45,
    "postCount": 12
  },
  "timestamp": 1719456789000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalViews` | long | 我的所有帖子被浏览总次数 |
| `totalLikes` | long | 我的所有帖子被点赞总次数（获赞） |
| `totalStars` | long | 我的所有帖子被收藏总次数 |
| `postCount` | long | 我发布的帖子总数 |

---

### 3.12 获取各学校帖子数

- **接口**: `GET /api/posts/school-counts`
- **说明**: 获取每个学校的帖子总数，用于首页展示
- **是否需要登录**: 否

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "1": 10,
    "2": 10,
    "3": 10
  },
  "timestamp": 1719456789000
}
```

> `data` 为 `{ schoolId: postCount }` 映射。

---

### 3.13 获取帖子评论列表

- **接口**: `GET /api/posts/{postId}/comments`
- **说明**: 获取指定帖子的评论列表，按创建时间正序
- **是否需要登录**: 否

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "comment-uuid-xxx",
      "postId": "post-uuid-xxx",
      "userId": "user-uuid-xxx",
      "username": "testuser",
      "avatarUrl": "https://api.dicebear.com/7.x/avataaars/svg?seed=user-uuid-xxx",
      "parentId": null,
      "replyToUserId": null,
      "content": "这份资料很有用，感谢分享！",
      "likeCount": 0,
      "createTime": "2026-06-27 14:00:00"
    }
  ],
  "timestamp": 1719456789000
}
```

---

### 3.14 发表评论

- **接口**: `POST /api/posts/{postId}/comments`
- **说明**: 在指定帖子下发表评论，同时帖子 comment_count +1
- **是否需要登录**: 是

**请求体**:

```json
{
  "content": "这份资料很有用，感谢分享！",
  "parentId": null,
  "replyToUserId": null
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 评论内容 |
| `parentId` | string | 否 | 父评论ID（用于楼中楼回复） |
| `replyToUserId` | string | 否 | 回复的用户ID |

**响应示例**: 同 3.13 单条评论结构

---

### 3.15 获取我的回复

- **接口**: `GET /api/posts/my-comments`
- **说明**: 获取当前用户发表的所有评论，按创建时间倒序
- **是否需要登录**: 是

**响应示例**: 同 3.13 评论列表结构

---

## 四、用户模块

### 4.1 更新用户资料

- **接口**: `PUT /api/users/me`
- **说明**: 更新当前用户的昵称、个人简介、头像
- **是否需要登录**: 是

**请求体**:

```json
{
  "username": "新昵称",
  "bio": "新的个人简介",
  "avatarUrl": "/files/20260627/uuid.png"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 否 | 新昵称（修改时检查重复） |
| `bio` | string | 否 | 个人简介 |
| `avatarUrl` | string | 否 | 头像URL（上传文件后获取） |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "user-uuid-xxx",
    "username": "新昵称",
    "email": "test@example.com",
    "phone": "13800138000",
    "avatarUrl": "/files/20260627/uuid.png",
    "bio": "新的个人简介",
    "schoolId": "3",
    "createTime": "2026-06-27 12:00:00"
  },
  "timestamp": 1719456789000
}
```

---

### 4.2 修改密码

- **接口**: `PUT /api/users/me/password`
- **说明**: 修改当前用户密码
- **是否需要登录**: 是

**请求体**:

```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass123",
  "confirmPassword": "NewPass123"
}
```

---

### 4.3 绑定/更换邮箱

- **接口**: `PUT /api/users/me/email`
- **说明**: 绑定或更换邮箱（需验证码）
- **是否需要登录**: 是

**请求体**:

```json
{
  "originalAccount": "原账号",
  "originalVerifyCode": "原验证码",
  "newAccount": "new@example.com",
  "newVerifyCode": "123456",
  "realNameVerify": false
}
```

---

### 4.4 绑定/更换手机号

- **接口**: `PUT /api/users/me/phone`
- **说明**: 绑定或更换手机号（需验证码）
- **是否需要登录**: 是

**请求体**: 同 4.3 结构

---

### 4.5 实名认证

- **接口**: `POST /api/users/me/real-name-verify`
- **说明**: 提交实名认证信息
- **是否需要登录**: 是

**请求体**:

```json
{
  "realName": "张三",
  "idCard": "110101199001011234"
}
```

---

### 4.6 更新隐私设置

- **接口**: `PUT /api/users/me/privacy`
- **说明**: 更新当前用户的隐私设置（控制他人可见内容范围）
- **是否需要登录**: 是

**请求体**:

```json
{
  "publicPosts": true,
  "publicStars": true,
  "publicLikes": false,
  "publicHistory": false,
  "searchable": true
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `publicPosts` | boolean | 否 | 是否公开帖子（false时他人主页帖子列表返回空） |
| `publicStars` | boolean | 否 | 是否公开收藏 |
| `publicLikes` | boolean | 否 | 是否公开点赞 |
| `publicHistory` | boolean | 否 | 是否公开浏览历史 |
| `searchable` | boolean | 否 | 是否允许被搜索（false时搜索结果不包含该用户） |

**响应示例**: 返回更新后的 UserDTO（含8个隐私/通知字段）

---

### 4.7 更新通知偏好设置

- **接口**: `PUT /api/users/me/notification-settings`
- **说明**: 更新当前用户的通知偏好（控制未读红点推送，收纳篮始终可见）
- **是否需要登录**: 是

**请求体**:

```json
{
  "notifyMessages": true,
  "notifyReplies": false,
  "notifyLikes": true
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `notifyMessages` | boolean | 否 | 是否接收新消息通知（false时不产生私信红点） |
| `notifyReplies` | boolean | 否 | 是否接收帖子回复通知（false时不产生评论/回复红点） |
| `notifyLikes` | boolean | 否 | 是否接收点赞收藏通知（false时不产生点赞/收藏/关注红点） |

> **行为说明**: 关闭某类通知时，该类型不推送未读红点（收纳篮 unreadCount 为0，底部导航栏未读总数不包含），但收纳篮始终显示且历史内容可查看。null 值视为默认开启。

**响应示例**: 返回更新后的 UserDTO

---

### 4.8 搜索用户

- **接口**: `GET /api/users/search`
- **说明**: 按用户名模糊搜索用户（排除自己，最多20条）
- **是否需要登录**: 是

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | string | 是 | 搜索关键词（匹配用户名） |

**响应示例**:

```json
{
  "code": 200,
  "data": [
    { "id": "xxx", "username": "testuser", "avatarUrl": null, "bio": "..." }
  ]
}
```

---

### 4.9 获取用户主页资料

- **接口**: `GET /api/users/{userId}/profile`
- **说明**: 获取指定用户的公开资料（含统计、关注状态）
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "id": "xxx",
    "username": "testuser",
    "avatarUrl": null,
    "bio": "...",
    "postCount": 12,
    "totalViews": 1256,
    "totalLikes": 89,
    "totalStars": 45,
    "followerCount": 30,
    "followingCount": 15,
    "isFollowing": false,
    "isSelf": false
  }
}
```

---

### 4.10 获取用户的帖子/收藏/点赞/浏览历史

| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/users/{userId}/posts` | GET | 用户发布的帖子 |
| `GET /api/users/{userId}/starred` | GET | 用户收藏的帖子 |
| `GET /api/users/{userId}/liked` | GET | 用户点赞的帖子 |
| `GET /api/users/{userId}/history` | GET | 用户浏览历史 |

**查询参数**: `page`（默认1）、`size`（默认20）

---

### 4.11 关注/取消关注用户

- **接口**: `POST /api/users/{userId}/follow`
- **说明**: 切换关注状态（已关注则取消，未关注则关注）。不能关注自己。
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": true
}
```

> `data` 为 true 表示已关注，false 表示已取消关注

---

### 4.12 获取关注统计

- **接口**: `GET /api/users/me/follow-stats`
- **说明**: 获取当前用户的关注/粉丝/互关数量
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "following": 15,
    "followers": 30,
    "mutual": 8
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `following` | long | 我关注的用户数 |
| `followers` | long | 关注我的用户数 |
| `mutual` | long | 互相关注的用户数 |

---

### 4.13 获取关注/粉丝/互关列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/users/me/following` | GET | 我关注的用户列表 |
| `GET /api/users/me/followers` | GET | 我的粉丝列表 |
| `GET /api/users/me/mutual` | GET | 互相关注的用户列表 |

- **是否需要登录**: 是

**响应示例**（三个接口结构相同）:

```json
{
  "code": 200,
  "data": [
    { "id": "xxx", "username": "testuser", "avatarUrl": null, "bio": "..." }
  ]
}
```

---

## 五、私信模块

### 5.1 发送私信

- **接口**: `POST /api/messages/send`
- **说明**: 向指定用户发送私信。受单向消息限制：若对方未关注你且未回复过你，只能发送一条。
- **是否需要登录**: 是

**请求体**:

```json
{
  "receiverId": "user-uuid-xxx",
  "content": "你好！"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `receiverId` | string | 是 | 接收者用户ID |
| `content` | string | 是 | 消息内容（不能为空） |

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "id": "msg-uuid-xxx",
    "senderId": "my-user-id",
    "senderName": "我的昵称",
    "senderAvatar": "/files/xxx.png",
    "receiverId": "user-uuid-xxx",
    "content": "你好！",
    "isRead": 0,
    "createTime": "2026-06-27 18:00:00"
  }
}
```

**错误情况**：
- 不能给自己发私信（4001）
- 超出单向消息限制（4001）：对方未关注你且未回复你，只能发一条

---

### 5.2 获取会话消息

- **接口**: `GET /api/messages/conversation/{otherUserId}`
- **说明**: 获取与指定用户的完整对话记录（按时间正序），同时将对方发来的消息标记为已读
- **是否需要登录**: 是

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `otherUserId` | string | 是 | 对方用户ID |

**响应示例**: 同 5.1 响应 data 为数组

---

### 5.3 获取会话列表

- **接口**: `GET /api/messages/list`
- **说明**: 获取当前用户的所有私信会话，每个会话返回最近一条消息，按时间倒序
- **是否需要登录**: 是

**响应示例**: 同 5.1 响应 data 为数组（每项为各会话的最近消息）

---

### 5.4 检查是否可发送消息

- **接口**: `GET /api/messages/can-send/{otherUserId}`
- **说明**: 检查当前用户是否可以向指定用户发送消息（用于前端控制输入框禁用状态）
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": true
}
```

> `data` 为 true 表示可发送，false 表示受单向限制（已发过一条且对方未关注未回复）

**发送限制规则**：
- 对方关注了你 → 可自由发送
- 对方回复过你（至少一条）→ 可自由发送
- 否则 → 你只能发送一条消息（已发送过则不可再发）
- 互相关注不是必要条件，互相回复一条即可

---

## 六、通知模块

### 6.1 获取通知列表

- **接口**: `GET /api/notifications/feed`
- **说明**: 获取统一通知列表（点赞/收藏/关注收纳组 + 陌生人私信收纳组 + 已对话私信列表），按最新时间倒序
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": [
    {
      "itemType": "LIKE",
      "title": "赞",
      "preview": "张三 等3人赞了你的帖子",
      "unreadCount": 2,
      "totalCount": 3,
      "latestTime": "2026-06-27 18:00:00",
      "isPinned": false
    },
    {
      "itemType": "CONVERSATION",
      "title": "张三",
      "preview": "你好！",
      "unreadCount": 1,
      "totalCount": 5,
      "latestTime": "2026-06-27 17:30:00",
      "isPinned": false,
      "otherUserId": "xxx",
      "otherUserName": "张三",
      "otherUserAvatar": "/files/xxx.png"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `itemType` | string | 通知类型：LIKE/STAR/FOLLOW/STRANGER_MSG/CONVERSATION |
| `title` | string | 显示标题 |
| `preview` | string | 最新内容预览 |
| `unreadCount` | int | 未读数 |
| `totalCount` | int | 总数 |
| `latestTime` | string | 最新时间 |
| `isPinned` | boolean | 是否置顶 |
| `otherUserId` | string | 仅 CONVERSATION：对方用户ID |
| `otherUserName` | string | 仅 CONVERSATION：对方用户名 |
| `otherUserAvatar` | string | 仅 CONVERSATION：对方头像 |

---

### 6.2 获取通知详情

- **接口**: `GET /api/notifications/detail/{type}`
- **说明**: 展开通知收纳组，获取具体通知列表
- **是否需要登录**: 是

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `type` | string | LIKE / STAR / FOLLOW |

**响应示例**:

```json
{
  "code": 200,
  "data": [
    {
      "id": "1",
      "senderId": "xxx",
      "senderName": "张三",
      "senderAvatar": "/files/xxx.png",
      "type": "LIKE",
      "targetId": "post-uuid",
      "targetTitle": "高等数学复习资料",
      "isRead": 0,
      "createTime": "2026-06-27 18:00:00"
    }
  ]
}
```

---

### 6.3 标记通知已读

- **接口**: `POST /api/notifications/read/{type}`
- **说明**: 标记指定类型的所有通知为已读
- **是否需要登录**: 是

---

### 6.4 切换通知置顶

- **接口**: `POST /api/notifications/pin`
- **说明**: 切换通知项的置顶状态
- **是否需要登录**: 是

**请求体**:

```json
{
  "itemType": "CONVERSATION",
  "targetId": "user-uuid-xxx"
}
```

---

### 6.5 获取未读数

- **接口**: `GET /api/notifications/unread-count`
- **说明**: 获取通知+私信的总未读数（用于铃铛红点）
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": 5
}
```

---

## 七、创作者认证模块

### 7.1 获取创作者认证统计

- **接口**: `GET /api/creator/stats`
- **说明**: 获取当前用户的创作者认证统计数据（总获赞、发帖数、是否满足认证条件）
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "totalLikes": 89,
    "postCount": 12,
    "canApply": false,
    "thresholdLikes": 10000,
    "thresholdPosts": 50
  }
}
```

---

### 7.2 获取创作者认证状态

- **接口**: `GET /api/creator/status`
- **说明**: 获取当前用户的创作者认证申请状态和等级
- **是否需要登录**: 是

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "isCreator": false,
    "creatorLevel": "NONE",
    "applicationStatus": "NONE",
    "realName": null,
    "rejectReason": null
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `isCreator` | boolean | 是否已认证为创作者 |
| `creatorLevel` | string | 创作者等级：NONE/JUNIOR/INTERMEDIATE/SENIOR/AUTHORITY |
| `applicationStatus` | string | 申请状态：NONE/PENDING/APPROVED/REJECTED |
| `realName` | string | 认证真实姓名（未认证为null） |
| `rejectReason` | string | 驳回原因（被驳回时有值） |

---

### 7.3 提交创作者认证申请

- **接口**: `POST /api/creator/apply`
- **说明**: 提交创作者认证申请（需满足总获赞≥10000且发帖≥50篇）
- **是否需要登录**: 是

**请求体**:

```json
{
  "realName": "张三",
  "idCard": "110101199001011234",
  "idCardFront": "/files/20260627/front.png",
  "idCardBack": "/files/20260627/back.png"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `realName` | string | 是 | 真实姓名 |
| `idCard` | string | 是 | 身份证号 |
| `idCardFront` | string | 否 | 身份证正面照URL |
| `idCardBack` | string | 否 | 身份证背面照URL |

---

### 7.4 管理员获取认证申请列表

- **接口**: `GET /api/creator/admin/applications`
- **说明**: 管理员分页查看创作者认证申请列表
- **是否需要登录**: 是（需管理员权限）

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `status` | string | all | 筛选状态：all/pending/approved/rejected |
| `page` | int | 1 | 页码 |
| `size` | int | 20 | 每页数量 |

---

### 7.5 管理员审核认证申请

- **接口**: `POST /api/creator/admin/applications/{id}/verify`
- **说明**: 管理员审核认证申请（通过或驳回）
- **是否需要登录**: 是（需管理员权限）

**请求体**:

```json
{
  "approved": false,
  "rejectReason": "身份证信息不清晰，请重新上传"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `approved` | boolean | 是 | 是否通过 |
| `rejectReason` | string | 否 | 驳回原因（驳回时必填） |

---

## 八、管理模块

### 8.1 清空帖子数据

- **接口**: `POST /api/admin/clear-posts`
- **说明**: 清空所有帖子及关联数据（posts、post_stars、post_likes、view_history、Redis缓存），用于调试重置
- **是否需要登录**: 否

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "所有帖子及相关数据已清空（posts, post_stars, post_likes, view_history, Redis缓存）",
  "timestamp": 1719456789000
}
```

---

### 8.2 生成测试数据

- **接口**: `POST /api/admin/init-test-data`
- **说明**: 为每个学校生成测试帖子（内容随机，浏览量/点赞/收藏全为0）
- **是否需要登录**: 否

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `postsPerSchool` | int | 10 | 每个学校生成的帖子数 |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "测试数据生成完成！共生成 80 条帖子，63 个文件",
  "timestamp": 1719456789000
}
```

> 生成范围为学校ID 1-8（匹配 init.sql），帖子类型仅 resource/discussion。

---

## 九、文件模块

### 9.1 文件上传

- **接口**: `POST /api/files/upload`
- **说明**: 上传文件
- **是否需要登录**: 是
- **Content-Type**: `multipart/form-data`

**请求参数 (FormData)**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 上传的文件 |

**支持的文件格式**:
- 文档：PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX
- 文本：TXT, MD, HTML
- 压缩包：ZIP, RAR, 7Z
- 图片：JPG, PNG, GIF
- 单文件大小：最大 100MB

**响应示例**:

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "/files/20260627/uuid-value.pdf",
    "fileName": "高等数学复习资料.pdf",
    "fileType": "pdf",
    "fileSize": 2411776
  },
  "timestamp": 1719456789000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | string | 文件访问URL |
| `fileName` | string | 原始文件名 |
| `fileType` | string | 文件类型（扩展名小写） |
| `fileSize` | long | 文件大小（字节） |

---

### 9.2 文件访问

- **接口**: `GET /api/files/{date}/{filename}`
- **说明**: 访问/下载上传的文件
- **是否需要登录**: 否
- **响应**: 文件二进制流

---

## 十、AI智能助手模块（agent-service，端口8083）

> agent-service 基于 Spring WebFlux 响应式框架，提供 SSE 流式对话、RAG 知库检索、帖子向量同步。
> 所有 `/api/agent/**` 接口需经网关 JWT 认证，网关注入 `X-User-Id`/`X-Username` 头。
> 内部运维端点 `/internal/agent/**` 不经网关，直接访问 8083 端口。

### 10.1 AI 对话（SSE 流式）

- **接口**: `POST /api/agent/chat`
- **说明**: 与 AI 助手对话，响应为 SSE 流（`text/event-stream`），逐 token 返回
- **是否需要登录**: 是

**请求体**:

```json
{
  "message": "怎么登录注册",
  "sessionId": "可选，已有会话ID"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | string | 是 | 用户消息 |
| `sessionId` | string | 否 | 已有会话ID，不传则新建会话 |

**响应**: SSE 事件流（`data:` 前缀的 token 增量，以 `[DONE]` 结束）

### 10.2 会话管理

| 接口 | 方法 | 路径 | 说明 | 登录 |
|------|------|------|------|------|
| 会话列表 | GET | `/api/agent/sessions` | 获取用户历史会话 | 是 |
| 会话详情 | GET | `/api/agent/sessions/{sessionId}` | 获取会话轮次记录 | 是 |
| 删除会话 | DELETE | `/api/agent/sessions/{sessionId}` | 删除指定会话 | 是 |

### 10.3 内部运维端点（不经网关，直接访问 8083）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 知识库重建索引 | POST | `/internal/agent/knowledge/reindex` | 扫描知识库文档重新 embedding 入库 |
| 知识库状态 | GET | `/internal/agent/knowledge/status` | 查询知识库向量数量 |
| 向量库总状态 | GET | `/internal/agent/vector/status` | 查询知识库+帖子向量数量 |
| 帖子向量全量同步 | POST | `/internal/agent/post-vector/sync-all` | 从 post-service 拉取全量帖子向量化 |
| 帖子向量单条同步 | POST | `/internal/agent/post-vector/sync/{postId}` | 同步单条帖子向量（增删改触发） |

> ⚠️ 内部端点返回 `Mono<Result<T>>`（WebFlux 响应式），所有阻塞调用通过 `Schedulers.boundedElastic()` 执行。

---

## 十一、错误码汇总

| 错误码 | 错误信息 | 可能原因 |
|--------|---------|---------|
| 200 | success | 请求成功 |
| 3001 | 验证码已过期 | 验证码超过5分钟有效期 |
| 3002 | 验证码错误 | 输入的验证码不匹配 |
| 4001 | 参数错误 | 请求参数缺失或格式不正确 |
| 4002 | 用户不存在 | 账号未注册 |
| 4003 | 密码错误 | 登录密码不正确 |
| 4004 | 用户名已存在 | 注册时用户名重复 |
| 4005 | 账号已被注册 | 手机号/邮箱已注册 |
| 4010 | 登录已过期 | Token 无效或已过期 |
| 4030 | 无权限访问 | 未登录或权限不足 |
| 40001 | 标题不能为空 | 发帖时标题为空 |
| 40002 | 学校ID不能为空 | 发帖时缺少学校ID |
| 40004 | 帖子不存在 | 访问的帖子已删除或不存在 |
| 50001 | 文件不能为空 | 上传文件为空 |
| 50002 | 文件上传失败 | 服务器存储异常 |
| 50000 | 服务器内部错误 | 未知异常 |

---

## 十二、版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 初始版本，包含认证、帖子、文件模块 |
| v1.1 | 2026-06-27 | 收藏/点赞改为DB持久化+Redis缓存；帖子详情记录浏览历史；新增管理模块（清空/生成数据）；网关白名单修复 |
| v1.2 | 2026-06-27 | 新增帖子状态查询接口(status)；新增个人主页接口(history/starred/liked/mine/my-stats)；列表页收藏按钮对接后端；个人主页改为入口按钮+独立列表页 |
| v1.3 | 2026-06-27 | 新增评论功能(发表/列表)；新增用户资料更新(头像/昵称/简介)；新增我的回复；新增学校帖子数统计；修复nginx /api/代理优先级 |
| v1.4 | 2026-06-27 | 新增帖子编辑/删除；评论点赞/删除/楼中楼回复；列表接口返回作者信息(PostListDTO)；时区修复(Docker TZ)；文档下载完善 |
| v1.5 | 2026-06-27 | 新增用户社交：关注/取消关注、关注统计、关注/粉丝/互关列表、用户主页资料、用户帖子/收藏/点赞/历史；新增密码修改、账号绑定、实名认证、用户搜索 |
| v1.6 | 2026-06-27 | 新增私信模块：发送消息、会话记录、会话列表、可发送检查；单向消息限制（未互关/未回复仅可发一条） |
| v1.7 | 2026-06-27 | 新增通知模块：通知列表(feed)、通知详情、标记已读、置顶、未读数；点赞/收藏/关注自动创建通知 |
| v1.8 | 2026-06-29 | 新增创作者认证模块：认证统计/状态/申请/管理员审核；通知中心改为独立页面+分类Tab；文件大小上限提升至100MB |
| v1.9 | 2026-06-30 | 新增隐私设置接口(5个开关)、通知偏好设置接口(3个开关)；UserDTO增加8个隐私/通知字段；修复OTel agent依赖与网关YAML配置 |
| v2.0 | 2026-07-02 | 新增AI智能助手模块（第十章）：SSE流式对话、会话管理、知识库reindex、帖子向量同步等端点；登录接口明确仅支持手机号/邮箱（移除用户名登录）；修复agent-service WebFlux阻塞调用 |
