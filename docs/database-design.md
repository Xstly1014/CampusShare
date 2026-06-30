# CampusShare 数据库设计文档

> **文档版本**: v2.0
> **创建日期**: 2026-06-27
> **更新日期**: 2026-06-30
> **数据库类型**: MySQL 8.0
> **字符集**: utf8mb4_unicode_ci
> **存储引擎**: InnoDB

---

## 一、数据库概览

### 1.1 ER 关系图

```
users ─────────────────────┐
  │ (1:N)                  │ (1:N)
  ├── follows              ├── messages
  ├── notifications        ├── creator_verifications
  │                        │
  │ (1:N)                  │ (1:N)
  ├── posts ───────────────┤
  │     │ (1:N)            │ (1:N)
  │     ├── comments       ├── post_stars
  │     ├── post_likes     ├── post_likes
  │     ├── view_history   ├── comment_likes
  │     └── post_downloads │
  │                        │
  │ (1:N)                  │ (1:N)
  └── comment_likes        └── view_history

categories ───┐
              │ (1:N)
              sub_categories

schools ─── posts (school_id)
```

### 1.2 表清单

| 表名 | 说明 | 所属服务 | 记录数预估 |
|------|------|---------|-----------|
| `users` | 用户表 | user-service | 百万级 |
| `follows` | 用户关注关系表 | user-service | 亿级 |
| `messages` | 私信消息表 | user-service | 亿级 |
| `notifications` | 通知表 | user-service | 亿级 |
| `creator_verifications` | 创作者认证申请表 | user-service | 万级 |
| `schools` | 学校表 | post-service | < 3000 |
| `categories` | 主分类表（收纳袋） | post-service | < 100 |
| `sub_categories` | 子分类表 | post-service | < 1000 |
| `posts` | 帖子表 | post-service | 千万级 |
| `comments` | 评论表 | post-service | 亿级 |
| `post_stars` | 帖子收藏表 | post-service | 千万级 |
| `post_likes` | 帖子点赞表 | post-service | 千万级 |
| `comment_likes` | 评论点赞表 | post-service | 千万级 |
| `view_history` | 浏览历史表 | post-service | 亿级 |
| `post_downloads` | 下载历史表 | post-service | 亿级 |
| `resources` | 资源表(legacy) | post-service | 百万级 |

---

## 二、核心表结构

### 2.1 用户表 `users`

**说明**：存储用户基础信息、角色、隐私设置、通知偏好

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
| `role` | VARCHAR(20) | NOT NULL | USER | 用户角色：USER/CREATOR/ADMIN |
| `creator_level` | VARCHAR(20) | - | NONE | 创作者等级：NONE/JUNIOR/INTERMEDIATE/SENIOR/AUTHORITY |
| `status` | TINYINT | - | 1 | 账号状态：1-正常，0-禁用 |
| `public_posts` | TINYINT | - | 1 | 隐私：是否公开帖子 |
| `public_stars` | TINYINT | - | 0 | 隐私：是否公开收藏 |
| `public_likes` | TINYINT | - | 0 | 隐私：是否公开点赞 |
| `public_history` | TINYINT | - | 0 | 隐私：是否公开浏览历史 |
| `searchable` | TINYINT | - | 1 | 隐私：是否允许被搜索 |
| `notify_messages` | TINYINT | - | 1 | 通知：是否接收新消息通知 |
| `notify_replies` | TINYINT | - | 1 | 通知：是否接收帖子回复通知 |
| `notify_likes` | TINYINT | - | 0 | 通知：是否接收点赞收藏通知 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| `deleted` | TINYINT | - | 0 | 逻辑删除标记 |

**索引**：
```sql
INDEX idx_username (username)
INDEX idx_email (email)
INDEX idx_phone (phone)
INDEX idx_school (school_id)
INDEX idx_role (role)
```

---

### 2.2 创作者认证申请表 `creator_verifications`

**说明**：存储创作者认证申请与审核记录

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `user_id` | VARCHAR(36) | NOT NULL | - | 申请人用户ID |
| `real_name` | VARCHAR(50) | NOT NULL | - | 真实姓名 |
| `id_card` | VARCHAR(20) | NOT NULL | - | 身份证号 |
| `id_card_front` | VARCHAR(500) | - | NULL | 身份证正面照片URL |
| `id_card_back` | VARCHAR(500) | - | NULL | 身份证反面照片URL |
| `total_likes` | INT | - | 0 | 申请时总获赞数 |
| `total_posts` | INT | - | 0 | 申请时总帖子数 |
| `status` | VARCHAR(20) | NOT NULL | PENDING | 状态：PENDING/APPROVED/REJECTED |
| `reject_reason` | VARCHAR(500) | - | NULL | 驳回原因 |
| `review_time` | TIMESTAMP | - | NULL | 审核时间 |
| `reviewer_id` | VARCHAR(36) | - | NULL | 审核人ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 申请时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**索引**：
```sql
INDEX idx_user (user_id)
INDEX idx_status (status)
INDEX idx_create_time (create_time)
```

---

### 2.3 学校表 `schools`

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

### 2.4 帖子表 `posts`

**说明**：统一存储资源贴和讨论帖

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 帖子ID（UUID） |
| `school_id` | VARCHAR(36) | - | NULL | 所属学校ID（校园分类帖子使用） |
| `category_id` | VARCHAR(36) | - | NULL | 所属分类ID（非校园分类帖子使用） |
| `sub_category_id` | VARCHAR(36) | - | NULL | 所属子分类ID（非校园分类帖子使用） |
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
INDEX idx_type (post_type)
INDEX idx_school_list (school_id, category_id, status, deleted, create_time)  -- 学校帖子列表复合索引
INDEX idx_category_list (category_id, status, deleted, create_time)            -- 分类帖子列表复合索引
INDEX idx_subcategory_list (sub_category_id, status, deleted, create_time)     -- 子分类帖子列表复合索引
INDEX idx_author_list (author_id, deleted, create_time)                        -- 用户帖子列表复合索引
FULLTEXT idx_title_content (title, content)  -- 全文检索
```

**设计说明**：
- 资源贴和讨论贴统一存储，通过 `post_type` 区分
- `school_id` 用于校园分类帖子，`category_id`/`sub_category_id` 用于其他分类帖子
- 文件相关字段（`file_*`）对讨论贴为 NULL（可选）
- 计数字段（`*_count`）用于列表展示，减少 join 查询
- 复合索引覆盖高频列表查询场景，P95延迟<100ms
- 全文索引用于帖子标题和内容的关键词搜索

---

### 2.5 评论表 `comments`

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

### 2.6 帖子收藏表 `post_stars`

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

### 2.7 帖子点赞表 `post_likes`

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

### 2.8 浏览历史表 `view_history`

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

### 2.9 资源表 `resources` (Legacy)

> 此表为早期设计，后续将逐步迁移到 `posts` 表统一管理

---

### 2.10 用户关注表 `follows`

**说明**：记录用户间的关注关系

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `follower_id` | VARCHAR(36) | NOT NULL | - | 关注者ID |
| `following_id` | VARCHAR(36) | NOT NULL | - | 被关注者ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 关注时间 |

**约束**：
- UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`)

**索引**：
```sql
INDEX idx_follower (follower_id)
INDEX idx_following (following_id)
```

**设计说明**：
- `follower_id` 关注 `following_id`，单向关系
- 互相关注 = 两条记录（A→B 和 B→A）
- 唯一约束防止重复关注

---

### 2.11 私信消息表 `messages`

**说明**：存储用户间一对一私信内容

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 消息ID（UUID） |
| `sender_id` | VARCHAR(36) | NOT NULL | - | 发送者ID |
| `receiver_id` | VARCHAR(36) | NOT NULL | - | 接收者ID |
| `content` | TEXT | NOT NULL | - | 消息内容 |
| `is_read` | TINYINT | - | 0 | 是否已读：0-未读，1-已读 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 发送时间 |

**索引**：
```sql
INDEX idx_sender (sender_id)
INDEX idx_receiver (receiver_id)
INDEX idx_create_time (create_time)
```

**设计说明**：
- 消息为单向记录（sender → receiver）
- `is_read` 用于未读消息统计
- 会话查询通过 `sender_id + receiver_id` 双向匹配（A→B 或 B→A）

---

### 2.12 通知表 `notifications`

**说明**：存储点赞、收藏、关注等通知记录

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `user_id` | VARCHAR(36) | NOT NULL | - | 接收者ID（帖子作者或被关注者） |
| `sender_id` | VARCHAR(36) | NOT NULL | - | 触发者ID |
| `type` | VARCHAR(20) | NOT NULL | - | 类型：LIKE-点赞，STAR-收藏，FOLLOW-关注 |
| `target_id` | VARCHAR(36) | - | NULL | 目标ID（帖子ID，关注类为NULL） |
| `target_title` | VARCHAR(200) | - | NULL | 目标标题（帖子标题） |
| `is_read` | TINYINT | - | 0 | 是否已读：0-未读，1-已读 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |

**索引**：
```sql
INDEX idx_user (user_id)
INDEX idx_user_type (user_id, type)
INDEX idx_create_time (create_time)
```

**设计说明**：
- 每次点赞/收藏/关注都会创建一条通知记录
- `user_id` 是通知的接收者（帖子作者或被关注者）
- `sender_id` 是执行操作的用户
- 私信通知不写入此表，直接从 `messages` 表查询

---

### 2.13 评论点赞表 `comment_likes`

**说明**：用户对评论的点赞记录

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `comment_id` | VARCHAR(36) | NOT NULL | - | 评论ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 点赞用户ID |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |

**索引**：
```sql
UNIQUE KEY uk_comment_user (comment_id, user_id)
INDEX idx_user (user_id)
INDEX idx_comment (comment_id)
```

---

### 2.14 下载历史表 `post_downloads`

**说明**：用户下载帖子的历史记录

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | INT | PRIMARY KEY AUTO_INCREMENT | - | 主键 |
| `post_id` | VARCHAR(36) | NOT NULL | - | 帖子ID |
| `user_id` | VARCHAR(36) | NOT NULL | - | 下载用户ID |
| `download_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 下载时间 |

**索引**：
```sql
INDEX idx_user_time (user_id, download_time)
INDEX idx_post (post_id)
```

---

### 2.15 主分类表 `categories`

**说明**：首页收纳袋主分类（校园/音乐/影视等12个分类）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 分类ID |
| `name` | VARCHAR(50) | NOT NULL | - | 分类名称 |
| `icon` | VARCHAR(50) | NOT NULL | - | 图标名称（lucide-react图标名） |
| `color` | VARCHAR(20) | - | NULL | 主题色（tailwind颜色类） |
| `type` | VARCHAR(20) | NOT NULL | category | 类型：school-校园，category-普通分类 |
| `description` | VARCHAR(200) | - | NULL | 分类描述 |
| `sort_order` | INT | - | 0 | 排序权重 |
| `post_count` | INT | - | 0 | 帖子数量 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**索引**：
```sql
INDEX idx_sort (sort_order)
INDEX idx_type (type)
```

---

### 2.16 子分类表 `sub_categories`

**说明**：普通分类下的子板块（如音乐→华语/欧美/K-POP）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | 子分类ID |
| `category_id` | VARCHAR(36) | NOT NULL | - | 所属主分类ID |
| `name` | VARCHAR(50) | NOT NULL | - | 子分类名称 |
| `icon` | VARCHAR(50) | NOT NULL | Hash | 图标名称 |
| `sort_order` | INT | - | 0 | 排序权重 |
| `post_count` | INT | - | 0 | 帖子数量 |
| `create_time` | TIMESTAMP | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | TIMESTAMP | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**索引**：
```sql
INDEX idx_category (category_id)
INDEX idx_sort (sort_order)
```

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
- 关联表用下划线连接两个表名（如 `comment_likes`）

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
