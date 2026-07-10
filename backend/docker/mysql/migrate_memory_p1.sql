-- P1阶段迁移：为user_memory表添加访问统计字段
-- 用于衰减增强：高频访问记忆衰减率减半

ALTER TABLE user_memory
    ADD COLUMN access_count INT DEFAULT 0 COMMENT '被访问/使用次数' AFTER last_used_at,
    ADD COLUMN last_accessed_at DATETIME COMMENT '最近一次被访问时间' AFTER access_count;

-- 为已存在的记录设置默认值
UPDATE user_memory SET access_count = 0 WHERE access_count IS NULL;
