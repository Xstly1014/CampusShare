# CampusShare API 接口文档

> **文档版本**: v1.0
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
- **说明**: 获取帖子详细信息（浏览量 +1）
- **是否需要登录**: 否

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `postId` | string | 是 | 帖子ID |

**响应示例**: 同发布成功响应 data 结构

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

## 四、文件模块

### 4.1 文件上传

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
- 单文件大小：最大 50MB

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

### 4.2 文件访问

- **接口**: `GET /api/files/{date}/{filename}`
- **说明**: 访问/下载上传的文件
- **是否需要登录**: 否
- **响应**: 文件二进制流

---

## 五、错误码汇总

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

## 六、版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 初始版本，包含认证、帖子、文件模块 |
