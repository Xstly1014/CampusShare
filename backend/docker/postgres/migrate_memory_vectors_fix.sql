-- P1阶段修复：重建memory_vectors表（修正字段名和结构）
-- 之前的表结构有误（BIGSERIAL主键、memory_id冗余、content字段名等），需要重建

DROP TABLE IF EXISTS memory_vectors CASCADE;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE memory_vectors (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    memory_type     VARCHAR(32) NOT NULL,
    memory_key      VARCHAR(128) NOT NULL,
    memory_value    TEXT NOT NULL,
    confidence      DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    source          VARCHAR(32) NOT NULL DEFAULT 'IMPLICIT',
    embedding       vector(1024),
    access_count    INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    decay_score     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 向量索引（余弦距离）
CREATE INDEX idx_memory_vectors_embedding ON memory_vectors
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 用户ID + is_active 索引
CREATE INDEX idx_memory_vectors_user_id ON memory_vectors(user_id, is_active);

-- 记忆类型索引
CREATE INDEX idx_memory_vectors_memory_type ON memory_vectors(memory_type);

-- pg_trgm 关键词索引
CREATE INDEX idx_memory_vectors_value_trgm ON memory_vectors USING gin (memory_value gin_trgm_ops);
CREATE INDEX idx_memory_vectors_key_trgm ON memory_vectors USING gin (memory_key gin_trgm_ops);

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
