-- 记忆向量表（ADR-059~063）
-- 用于长期记忆的向量存储与检索

CREATE TABLE IF NOT EXISTS memory_vectors (
    id              BIGSERIAL PRIMARY KEY,
    memory_id       BIGINT NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    memory_type     VARCHAR(32) NOT NULL,
    memory_key      VARCHAR(128) NOT NULL,
    content         TEXT NOT NULL,
    confidence      DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    source          VARCHAR(32) NOT NULL,
    embedding       vector(1024) NOT NULL,
    embedding_model VARCHAR(64) NOT NULL DEFAULT 'bge-m3',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引（HNSW，余弦距离）
CREATE INDEX IF NOT EXISTS idx_memory_vectors_embedding
    ON memory_vectors USING hnsw (embedding vector_cosine_ops);

-- 用户ID索引
CREATE INDEX IF NOT EXISTS idx_memory_vectors_user_id
    ON memory_vectors (user_id);

-- pg_trgm 关键词索引
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_memory_vectors_content_trgm
    ON memory_vectors USING gin (content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_memory_vectors_key_trgm
    ON memory_vectors USING gin (memory_key gin_trgm_ops);

-- 更新时间触发器
CREATE OR REPLACE FUNCTION update_memory_vectors_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_memory_vectors_updated_at ON memory_vectors;
CREATE TRIGGER trg_memory_vectors_updated_at
    BEFORE UPDATE ON memory_vectors
    FOR EACH ROW
    EXECUTE FUNCTION update_memory_vectors_updated_at();
