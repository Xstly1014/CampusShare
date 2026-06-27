# CampusShare 数据库设计文档

> **文档版本**: v1.0
> **创建日期**: 2026-06-27
> **数据库类型**: MySQL 8.0
> **字符集**: utf8mb4_unicode_ci
> **存储引擎**: InnoDB

---

## 一、数据库概览

### 1.1 ER 关系图

```
users ───┐
         │ (1:N)
roles ───┘
   │
   │ (1:N)
user_roles

schools ───┐
            │ (1:N)
posts ──────┤
            │ (1:N)
comments ───┤
            │ (1:N)
post_stars ─┤
            │ (1:N)
post_likes ─┤
            │ (1:N)
view_history┘

resources (待完善)
```

### 1.2 表清单

| 表名 | 说明 | 记录数预估 |
|------|------|-----------|
| `users` | 用户表 | 百万级 |
| `roles` | 角色表 | < 100 |
| `user_roles` | 用户角色关联表 | 百万级 |
| `schools` | 学校表 | < 3000 |
| `posts` | 帖子表 | 千万级 |
| `comments` | 评论表 | 亿级 |
| `post_stars` | 帖子收藏表 | 千万级 |
| `post_likes` | 帖子点赞表 | 千万级 |
| `view_history` | 浏览历史表 | 亿级 |
| `resources` | 资源表(legacy) | 百万级 |

---

## 二、核心表结构

### 2.1 用户表 `users`

**说明**：存储用户基础信息

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 用户ID（UUID） |
| `username` | VARCHAR(50) | NOT NULL UNIQUE | - | 用户名 |
| `email` | VARCHAR(100) | UNIQUE | NULL | 邮箱 |
| `phone` | VARCHAR(20) | UNIQUE | NULL | 手机号 |
| `password_hash` | VARCHAR(255) | NOT NULL | - | 密码哈希（BCrypt） |
| `avatar_url` | VARCHAR(500) | - | NULL | 头像URL |
| `bio` | VARCHAR(200) | - | NULL | 个人简介 |
| `school_id` | VARCHAR(36) | - | NULL | 所属学校ID |
| `status` | TINYINT | - | 1 | 账号状态：1-正常，0-禁用 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| `deleted` | TINYINT | - | 0 | 逻辑删除标记 |

**索引**：
```sql
INDEX idx_username (username)
INDEX idx_email (email)
INDEX idx_phone (phone)
INDEX idx_school (school_id)
INDEX idx_status (status)
INDEX idx_create_time (create_time)
```

---

### 2.2 角色表 `roles`

**说明**：系统角色定义

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 角色ID |
| `role_name` | VARCHAR(50) | NOT NULL UNIQUE | - | 角色名称 |
| `role_code` | VARCHAR(50) | NOT NULL UNIQUE | - | 角色编码 |
| `description` | VARCHAR(200) | - | NULL | 角色描述 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |

**初始数据**：
| id | role_name | role_code | description |
|----|-----------|-----------|-------------|
| 1 | 普通用户 | USER | 普通注册用户 |
| 2 | 管理员 | ADMIN | 系统管理员 |

---

### 2.3 用户角色关联表 `user_roles`

**说明**：用户与角色的多对多关联

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `user_id` | VARCHAR(36) | NOT NULL | - | 用户ID |
| `role_id` | INT | NOT NULL | - | 角色ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |

**约束**：
- UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
- FOREIGN KEY `user_id` REFERENCES `users(id)` ON DELETE CASCADE
- FOREIGN KEY `role_id` REFERENCES `roles(id)` ON DELETE CASCADE

---

### 2.4 学校表 `schools`

**说明**：高校基础信息

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 学校ID |
| `name` | VARCHAR(100) | NOT NULL UNIQUE | - | 学校名称 |
| `logo_url` | VARCHAR(500) | - | NULL | 校徽URL |
| `region` | VARCHAR(100) | - | NULL | 所属地区 |
| `description` | VARCHAR(500) | - | NULL | 学校简介 |
| `resource_count` | INT | - | 0 | 资源数量（冗余统计） |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**索引**：
```sql
INDEX idx_name (name)
INDEX idx_region (region)
```

---

### 2.5 帖子表 `posts`

**说明**：统一存储资源贴和讨论帖

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 帖子ID（UUID） |
| `school_id` | VARCHAR(36) | NOT NULL | - | 所属学校ID |
| `author_id` | VARCHAR(36) | NOT NULL | - | 作者ID |
| `post_type` | VARCHAR(20) | NOT NULL | discussion | 类型：resource-资源贴，discussion-讨论贴 |
| `title` | VARCHAR(200) | NOT NULL | - | 帖子标题 |
| `content` | TEXT | - | NULL | 正文内容/资源描述 |
| `file_url` | VARCHAR(500) | - | NULL | 附件文件URL |
| `file_name` | VARCHAR(200) | - | NULL | 附件文件名 |
| `file_type` | VARCHAR(50) | - | NULL | 附件文件类型（扩展名） |
| `file_size` | BIGINT | - | NULL | 附件文件大小（字节） |
| `view_count` | INT | - | 0 | 浏览次数 |
| `star_count` | INT | - | 0 | 收藏次数 |
| `like_count` | INT | - | 0 | 点赞次数 |
| `comment_count` | INT | - | 0 | 评论次数 |
| `status` | TINYINT | - | 1 | 状态：1-正常，0-删除 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| `deleted` | TINYINT | - | 0 | 逻辑删除标记 |

**索引**：
```sql
INDEX idx_school (school_id)
INDEX idx_author (author_id)
INDEX idx_type (post_type)
INDEX idx_status (status)
INDEX idx_create_time (create_time)
FULLTEXT idx_title_content (title, content)  -- 全文检索
```

**设计说明**：
- 资源贴和讨论贴统一存储，通过 `post_type` 区分
- 文件相关字段（`file_*`）对讨论贴为 NULL（可选）
- 计数字段（`*_count`）用于列表展示，减少 join 查询
- 全文索引用于帖子标题和内容的关键词搜索

---

### 2.6 评论表 `comments`

**说明**：帖子评论与回复

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 评论ID（UUID） |
| `post_id` | VARCHAR(36) | NOT NULL | - | 帖子ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 评论用户ID |
| `parent_id` | VARCHAR(36) | - | NULL | 父评论ID（楼中楼回复） |
| `reply_to_user_id` | VARCHAR(36) | - | NULL | 回复的目标用户ID |
| `content` | TEXT | NOT NULL | - | 评论内容 |
| `like_count` | INT | - | 0 | 点赞次数 |
| `status` | TINYINT | - | 1 | 状态：1-正常，0-删除 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| `deleted` | TINYINT | - | 0 | 逻辑删除标记 |

**索引**：
```sql
INDEX idx_post (post_id)
INDEX idx_user (user_id)
INDEX idx_parent (parent_id)
INDEX idx_create_time (create_time)
```

**设计说明**：
- 通过 `parent_id` 支持两级评论（一级评论 + 楼中楼回复）
- `reply_to_user_id` 用于展示"@xxx"的回复关系

---

### 2.7 帖子收藏表 `post_stars`

**说明**：用户收藏的帖子

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `post_id` | VARCHAR(36) | NOT NULL | - | 帖子ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 用户ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 收藏时间 |

**约束**：
- UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)

**索引**：
```sql
INDEX idx_user (user_id)
```

---

### 2.8 帖子点赞表 `post_likes`

**说明**：用户点赞的帖子

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `post_id` | VARCHAR(36) | NOT NULL | - | 帖子ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 用户ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 点赞时间 |

**约束**：
- UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)

**索引**：
```sql
INDEX idx_user (user_id)
```

---

### 2.9 浏览历史表 `view_history`

**说明**：用户浏览帖子的历史记录

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `post_id` | VARCHAR(36) | NOT NULL | - | 帖子ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 用户ID |
| `view_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 浏览时间 |

**索引**：
```sql
INDEX idx_user (user_id)
INDEX idx_post (post_id)
INDEX idx_view_time (view_time)
```

**设计说明**：
- 仅记录最近100条浏览记录（超出自动清理旧数据）
- 同一用户多次浏览同一帖子，更新 `view_time` 而非新增记录

---

### 2.10 资源表 `resources` (Legacy)

> 此表为早期设计，后续将逐步迁移到 `posts` 表统一管理

---

## 三、Redis 数据设计

### 3.1 验证码缓存

```
Key: campushare:verify:code:{account}
Value: 6位验证码
TTL: 300秒（5分钟）
```

### 3.2 浏览计数（直接落库）

```
策略: 直接在 MySQL 中原子递增 view_count（SET view_count = view_count + 1）
Redis: 不再使用（便于调试观察，保证强一致）
```

> 变更说明：原方案为 Redis 计数 + 每10次异步落库，现已改为直接 DB 原子操作，避免计数丢失和调试困难。

### 3.3 收藏状态缓存（DB为源 + Redis缓存）

```
Key: post:star:{postId}:{userId}
Value: 1
TTL: 30天
策略: DB (post_stars 表) 为数据源，Redis 仅作状态查询缓存
      - 收藏时：先写 DB，再设 Redis
      - 取消时：先删 DB，再删 Redis
      - 查询时：先查 Redis，未命中回源 DB
```

### 3.4 点赞状态缓存（DB为源 + Redis缓存）

```
Key: post:like:{postId}:{userId}
Value: 1
TTL: 30天
策略: DB (post_likes 表) 为数据源，Redis 仅作状态查询缓存
      - 点赞时：先写 DB，再设 Redis
      - 取消时：先删 DB，再删 Redis
      - 查询时：先查 Redis，未命中回源 DB
```

> 变更说明：原方案 Redis 为唯一数据源（7天TTL），数据会丢失且无法查询"我的收藏/点赞"。现已改为 DB 持久化 + Redis 缓存加速。

### 3.5 JWT Token 黑名单

```
Key: campushare:token:blacklist:{token}
Value: 1
TTL: Token 剩余有效期
```

---

## 四、命名规范

### 4.1 表命名
- 小写字母 + 下划线
- 复数形式（如 `users`, `posts`）
- 关联表用下划线连接两个表名（如 `user_roles`）

### 4.2 字段命名
- 小写字母 + 下划线
- 布尔字段：`is_xxx` / `has_xxx`
- 时间字段：`xxx_time`
- 计数字段：`xxx_count`
- ID 字段：`xxx_id`

### 4.3 索引命名
- 普通索引：`idx_字段名`
- 唯一索引：`uk_字段名`
- 联合索引：`idx_字段1_字段2`

---

## 五、数据迁移与变更

### 5.1 版本管理
- 所有 SQL 变更脚本按版本号命名：`V1.0.0__init.sql`, `V1.1.0__add_posts.sql`
- 使用 Flyway 管理数据库版本迁移（待接入）

### 5.2 回滚策略
- 每个变更脚本配套对应的回滚脚本
- 上线前在测试环境验证回滚路径

---

## 六、性能优化建议

### 6.1 读写分离
- 主库：写操作
- 从库：列表查询、统计分析
- 用户量达到 10w 时考虑接入

### 6.2 分库分表
- `comments` 表预估数据量最大，评论数超 5000w 时考虑按 `post_id` 分表
- `view_history` 按月份归档冷数据

### 6.3 缓存策略
| 数据类型 | 缓存方式 | TTL |
|---------|---------|-----|
| 学校列表 | 全量缓存 | 1小时 |
| 帖子详情 | 热点缓存 | 30分钟 |
| 用户信息 | 单Key缓存 | 1小时 |
| 计数类 | Redis+异步落库 | - |
| 验证码 | 强一致 | 5分钟 |
