-- ========================================
-- CampusShare Agent Service - PostgreSQL 向量库迁移脚本
-- 用途：为 post_vectors 表添加 category_name 和 school_name 中文字段
-- 执行方式：在已部署的环境中手动执行
-- ========================================

-- 添加 category_name 和 school_name 列（IF NOT EXISTS 防止重复执行报错）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'post_vectors' AND column_name = 'category_name') THEN
        ALTER TABLE post_vectors ADD COLUMN category_name VARCHAR(64);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'post_vectors' AND column_name = 'school_name') THEN
        ALTER TABLE post_vectors ADD COLUMN school_name VARCHAR(64);
    END IF;
END $$;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_post_vectors_category_name ON post_vectors(category_name);
CREATE INDEX IF NOT EXISTS idx_post_vectors_school_name ON post_vectors(school_name);
