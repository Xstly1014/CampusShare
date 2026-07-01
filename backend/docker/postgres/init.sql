-- ========================================
-- CampusShare Agent Service - PostgreSQL 向量库初始化
-- ========================================
-- 用途：agent-service 专属向量表（帖子向量 + 知识向量）
-- 引擎：PostgreSQL 16 + pgvector 扩展
-- 执行方式：由 docker-compose 挂载到 /docker-entrypoint-initdb.d/ 自动执行（仅首次启动）
-- 数据库：agent_vectors（由 POSTGRES_DB 环境变量创建）
-- ========================================

-- ========================================
-- 1. 启用扩展
-- ========================================
-- pgvector：向量类型与相似度检索
CREATE EXTENSION IF NOT EXISTS vector;

-- pg_trgm：三元组模糊匹配，加速 ILIKE 关键词检索（替代 BM25）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ========================================
-- 2. 帖子向量表
-- ========================================
-- 冗余结构化字段（category/school/like_count 等）用于向量检索时过滤
-- 避免检索后再查 post-service 的 N+1 问题（ADR-145）
-- 帖子数据由 post-service 通过内部 API 通知 agent-service 更新
CREATE TABLE IF NOT EXISTS post_vectors (
  post_id VARCHAR(36) PRIMARY KEY,
  post_title VARCHAR(255) NOT NULL,
  post_content_excerpt TEXT,
  post_type VARCHAR(32),
  category VARCHAR(64),
  school VARCHAR(64),
  author_id VARCHAR(36),
  author_verified BOOLEAN,
  like_count INT DEFAULT 0,
  view_count INT DEFAULT 0,
  created_at TIMESTAMP,
  embedding vector(1024),              -- BGE-M3 输出 1024 维
  embedding_model VARCHAR(32) DEFAULT 'bge-m3',
  embedding_version INT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 向量索引（余弦相似度）
-- m=16：每个节点最大连接数，越大召回率越高
-- ef_construction=64：构建时搜索宽度，越大索引质量越高
CREATE INDEX IF NOT EXISTS idx_post_vectors_embedding ON post_vectors
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 结构化过滤索引（向量检索 + 条件过滤）
CREATE INDEX IF NOT EXISTS idx_post_vectors_category ON post_vectors(category);
CREATE INDEX IF NOT EXISTS idx_post_vectors_school ON post_vectors(school);
CREATE INDEX IF NOT EXISTS idx_post_vectors_type ON post_vectors(post_type);
CREATE INDEX IF NOT EXISTS idx_post_vectors_created ON post_vectors(created_at DESC);

-- 关键词 trgm 索引（pg_trgm 替代 BM25，加速 ILIKE 模糊查询）
CREATE INDEX IF NOT EXISTS idx_post_vectors_title_trgm ON post_vectors USING gin (post_title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_post_vectors_content_trgm ON post_vectors USING gin (post_content_excerpt gin_trgm_ops);

-- ========================================
-- 3. 知识文档向量表
-- ========================================
CREATE TABLE IF NOT EXISTS knowledge_vectors (
  article_id BIGINT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  topic VARCHAR(32) NOT NULL,
  content_excerpt TEXT,
  content_md5 CHAR(32),
  status VARCHAR(16),
  version INT,
  embedding vector(1024),
  embedding_model VARCHAR(32) DEFAULT 'bge-m3',
  embedding_version INT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 向量索引
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding ON knowledge_vectors
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 结构化过滤索引
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_topic ON knowledge_vectors(topic);

-- 关键词 trgm 索引
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_title_trgm ON knowledge_vectors USING gin (title gin_trgm_ops);
