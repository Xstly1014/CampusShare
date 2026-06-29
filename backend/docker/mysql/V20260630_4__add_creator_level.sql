-- 添加创作者等级字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS creator_level VARCHAR(20) DEFAULT 'NONE' COMMENT '创作者等级：NONE-无，JUNIOR-初级，INTERMEDIATE-中级，SENIOR-高级，AUTHORITY-权威';

-- 给已有CREATOR角色用户设置初始等级为JUNIOR
UPDATE users SET creator_level = 'JUNIOR' WHERE role = 'CREATOR' AND (creator_level IS NULL OR creator_level = 'NONE');

-- 给creator_verifications表添加权威申请相关字段
ALTER TABLE creator_verifications ADD COLUMN IF NOT EXISTS verification_type VARCHAR(20) DEFAULT 'INITIAL' COMMENT '认证类型：INITIAL-初次认证，AUTHORITY-权威认证';
ALTER TABLE creator_verifications ADD COLUMN IF NOT EXISTS review_note VARCHAR(500) COMMENT '审核备注';
