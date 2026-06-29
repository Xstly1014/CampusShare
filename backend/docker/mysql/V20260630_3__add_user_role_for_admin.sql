-- 为users表添加role字段，支持管理员角色
-- 为creator_verifications表添加审核人字段
-- 执行时间：2026-06-30

-- users表添加role字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色：USER-普通用户，ADMIN-管理员' AFTER school_id;
ALTER TABLE users ADD INDEX IF NOT EXISTS idx_role (role);

-- 更新现有用户role为USER（防止NULL）
UPDATE users SET role='USER' WHERE role IS NULL OR role='';

-- creator_verifications表添加审核相关字段（如果不存在）
ALTER TABLE creator_verifications ADD COLUMN IF NOT EXISTS reviewer_id VARCHAR(36) COMMENT '审核人ID' AFTER review_time;

-- 插入默认管理员账号（手机号: NOkT4YYwjyD, 密码: Test123456）
-- 密码哈希: $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2
INSERT IGNORE INTO users (id, username, email, phone, password_hash, bio, role, status, avatar_url) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'admin', '4fYga@PjXkDek.h7v', 'rLdwD4Vkpa9', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'ADMIN', 1, 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin');

-- 更新已有admin用户：设置手机号、邮箱，重置密码hash（修复前导空格问题）
UPDATE users SET phone='rLdwD4Vkpa9', email='4fYga@PjXkDek.h7v', password_hash='$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2' WHERE username='admin';

-- 更新testuser：手机号改为有效格式，重置密码hash（修复前导空格问题）
UPDATE users SET phone='pv8wl3rkgdE', password_hash='$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2' WHERE username='testuser';
