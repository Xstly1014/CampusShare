-- CampusShare 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS campushare 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE campushare;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    bio VARCHAR(200) COMMENT '个人简介',
    school_id VARCHAR(36) COMMENT '所属学校ID',
    status TINYINT DEFAULT 1 COMMENT '账号状态：1-正常，0-禁用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_school (school_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 创建角色表
CREATE TABLE IF NOT EXISTS roles (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(200) COMMENT '角色描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 创建用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(36) NOT NULL,
    role_id INT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 创建学校表
CREATE TABLE IF NOT EXISTS schools (
    id VARCHAR(36) PRIMARY KEY COMMENT '学校ID',
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '学校名称',
    logo_url VARCHAR(500) COMMENT '校徽URL',
    region VARCHAR(100) COMMENT '所属地区',
    description VARCHAR(500) COMMENT '学校简介',
    resource_count INT DEFAULT 0 COMMENT '资源数量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校表';

-- 创建帖子表
CREATE TABLE IF NOT EXISTS posts (
    id VARCHAR(36) PRIMARY KEY COMMENT '帖子ID',
    school_id VARCHAR(36) NOT NULL COMMENT '所属学校ID',
    author_id VARCHAR(36) NOT NULL COMMENT '作者ID',
    post_type VARCHAR(20) NOT NULL DEFAULT 'discussion' COMMENT '帖子类型：resource-资源贴，discussion-讨论贴',
    title VARCHAR(200) NOT NULL COMMENT '帖子标题',
    content TEXT COMMENT '帖子正文内容',
    file_url VARCHAR(500) COMMENT '附件文件URL',
    file_name VARCHAR(200) COMMENT '附件文件名',
    file_type VARCHAR(50) COMMENT '附件文件类型',
    file_size BIGINT COMMENT '附件文件大小（字节）',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    star_count INT DEFAULT 0 COMMENT '收藏次数',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    comment_count INT DEFAULT 0 COMMENT '评论次数',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_school (school_id),
    INDEX idx_author (author_id),
    INDEX idx_type (post_type),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    FULLTEXT idx_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子表';

-- 创建评论表
CREATE TABLE IF NOT EXISTS comments (
    id VARCHAR(36) PRIMARY KEY COMMENT '评论ID',
    post_id VARCHAR(36) NOT NULL COMMENT '帖子ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    parent_id VARCHAR(36) COMMENT '父评论ID（用于回复）',
    reply_to_user_id VARCHAR(36) COMMENT '回复的用户ID',
    content TEXT NOT NULL COMMENT '评论内容',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_post (post_id),
    INDEX idx_user (user_id),
    INDEX idx_parent (parent_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 创建收藏表
CREATE TABLE IF NOT EXISTS post_stars (
    id INT PRIMARY KEY AUTO_INCREMENT,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子收藏表';

-- 创建点赞表
CREATE TABLE IF NOT EXISTS post_likes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

-- 创建评论点赞表
CREATE TABLE IF NOT EXISTS comment_likes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    comment_id VARCHAR(36) NOT NULL COMMENT '评论ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    INDEX idx_user (user_id),
    INDEX idx_comment (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- 创建浏览历史表
CREATE TABLE IF NOT EXISTS view_history (
    id INT PRIMARY KEY AUTO_INCREMENT,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    view_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_post (post_id),
    INDEX idx_view_time (view_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览历史表';

-- 创建用户关注表
CREATE TABLE IF NOT EXISTS follows (
    id INT PRIMARY KEY AUTO_INCREMENT,
    follower_id VARCHAR(36) NOT NULL COMMENT '关注者ID',
    following_id VARCHAR(36) NOT NULL COMMENT '被关注者ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_follower_following (follower_id, following_id),
    INDEX idx_follower (follower_id),
    INDEX idx_following (following_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

-- 创建私信消息表
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) PRIMARY KEY COMMENT '消息ID',
    sender_id VARCHAR(36) NOT NULL COMMENT '发送者ID',
    receiver_id VARCHAR(36) NOT NULL COMMENT '接收者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    sender_hidden TINYINT DEFAULT 0 COMMENT '发送方是否隐藏：0-显示，1-隐藏',
    receiver_hidden TINYINT DEFAULT 0 COMMENT '接收方是否隐藏：0-显示，1-隐藏',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';

-- 创建通知表
CREATE TABLE IF NOT EXISTS notifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(36) NOT NULL COMMENT '接收者ID',
    sender_id VARCHAR(36) NOT NULL COMMENT '触发者ID',
    type VARCHAR(20) NOT NULL COMMENT '类型：LIKE-点赞，STAR-收藏，FOLLOW-关注',
    target_id VARCHAR(36) COMMENT '目标ID（帖子ID，关注类为NULL）',
    target_title VARCHAR(200) COMMENT '目标标题（帖子标题）',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- 创建创作者认证申请表
CREATE TABLE IF NOT EXISTS creator_verifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(36) NOT NULL COMMENT '申请人用户ID',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    id_card VARCHAR(20) NOT NULL COMMENT '身份证号',
    id_card_front VARCHAR(500) COMMENT '身份证正面照片URL',
    id_card_back VARCHAR(500) COMMENT '身份证反面照片URL',
    total_likes INT DEFAULT 0 COMMENT '申请时总获赞数',
    total_posts INT DEFAULT 0 COMMENT '申请时总帖子数',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-审核中，APPROVED-已通过，REJECTED-已驳回',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    review_time TIMESTAMP NULL COMMENT '审核时间',
    reviewer_id VARCHAR(36) COMMENT '审核人ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作者认证申请表';

-- 创建资源表
CREATE TABLE IF NOT EXISTS resources (
    id VARCHAR(36) PRIMARY KEY COMMENT '资源ID',
    title VARCHAR(200) NOT NULL COMMENT '资源标题',
    description TEXT COMMENT '资源描述',
    category VARCHAR(50) COMMENT '资源分类',
    file_url VARCHAR(500) NOT NULL COMMENT '文件URL',
    file_type VARCHAR(50) COMMENT '文件类型',
    file_size BIGINT COMMENT '文件大小（字节）',
    thumbnail_url VARCHAR(500) COMMENT '缩略图URL',
    school_id VARCHAR(36) NOT NULL COMMENT '所属学校ID',
    uploader_id VARCHAR(36) NOT NULL COMMENT '上传者ID',
    download_count INT DEFAULT 0 COMMENT '下载次数',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    comment_count INT DEFAULT 0 COMMENT '评论次数',
    rating DECIMAL(3,2) DEFAULT 0.00 COMMENT '评分',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-下架',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_school (school_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    FULLTEXT idx_title_desc (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表';

-- 插入初始角色数据
INSERT INTO roles (role_name, role_code, description) VALUES
('普通用户', 'USER', '普通注册用户'),
('管理员', 'ADMIN', '系统管理员');

-- 插入示例学校数据
INSERT INTO schools (id, name, logo_url, region, description, resource_count) VALUES
('1', '北京大学', 'https://example.com/pku-logo.png', '北京市', '中国最著名的高等学府之一', 1256),
('2', '清华大学', 'https://example.com/tsinghua-logo.png', '北京市', '中国顶尖综合性大学', 1890),
('3', '深圳大学', 'https://example.com/szu-logo.png', '广东省深圳市', '特区创新型大学', 743),
('4', '复旦大学', 'https://example.com/fudan-logo.png', '上海市', '综合性研究型大学', 1562),
('5', '浙江大学', 'https://example.com/zju-logo.png', '浙江省杭州市', '高水平研究型大学', 2134),
('6', '南京大学', 'https://example.com/nju-logo.png', '江苏省南京市', '历史悠久的名校', 987),
('7', '武汉大学', 'https://example.com/whu-logo.png', '湖北省武汉市', '综合性大学', 1456),
('8', '中山大学', 'https://example.com/sysu-logo.png', '广东省广州市', '教育部直属高校', 1789);

-- 插入测试用户数据（密码为: Test123456）
INSERT INTO users (id, username, email, phone, password_hash, bio, school_id, status) VALUES
('550e8400-e29b-41d4-a716-446655440001', 'testuser', 'test@example.com', '13800138000',
 '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
 '这是一名测试用户', '3', 1);