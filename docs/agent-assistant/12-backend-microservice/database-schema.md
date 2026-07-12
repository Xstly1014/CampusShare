# agent-service 数据库 Schema

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、数据库归属

agent-service 拥有以下表，**仅 agent-service 可直接读写**：

### MySQL（campushare 数据库，与 user/post 共享实例但表独立）

| 表 | 用途 |
|----|------|
| agent_sessions | 会话主表 |
| agent_turns | 轮次记录 |
| agent_context_snapshots | 上下文快照 |
| agent_tool_errors | 工具错误归档 |
| agent_tool_registry | 工具注册表 |
| agent_session_events | 状态转移事件 |
| user_memory | 用户长期记忆 |
| user_memory_evidence | 行为证据 |
| user_memory_history | 记忆历史（软删除） |
| knowledge_articles | 知识库文档 |
| agent_pending_writes | 异步写入队列 |

### PostgreSQL（独立实例，pgvector）

| 表 | 用途 |
|----|------|
| post_vectors | 帖子向量 |
| knowledge_vectors | 知识文档向量 |

## 二、MySQL 表 DDL

### 2.1 agent_sessions

```sql
CREATE TABLE agent_sessions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  status ENUM('INIT','ACTIVE','TOOL_CALLING','WAITING_CLARIFY','REFLECTING','ARCHIVED','CLOSED','ERROR') NOT NULL,
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at DATETIME,
  turn_count INT DEFAULT 0,
  prompt_version VARCHAR(16),
  llm_model VARCHAR(32),
  intent_summary VARCHAR(256),
  total_input_tokens INT DEFAULT 0,
  total_output_tokens INT DEFAULT 0,
  total_cost_yuan DECIMAL(10,4) DEFAULT 0,
  feedback_positive INT DEFAULT 0,
  feedback_negative INT DEFAULT 0,
  quality_score DECIMAL(3,2),
  error_reason VARCHAR(256),
  INDEX idx_user_active (user_id, status, last_active_at),
  INDEX idx_status_archived (status, last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.2 agent_turns

```sql
CREATE TABLE agent_turns (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  turn_id INT NOT NULL,
  user_query TEXT,
  intent VARCHAR(32),
  intent_confidence DECIMAL(3,2),
  tools_called JSON,
  tool_versions JSON,
  retrieval_refs JSON,
  assistant_answer TEXT,
  input_tokens INT,
  output_tokens INT,
  latency_ms INT,
  cost_yuan DECIMAL(8,4),
  feedback ENUM('UP','DOWN',NULL),
  feedback_reason VARCHAR(64),
  context_snapshot_id BIGINT,
  interrupted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_turn (session_id, turn_id),
  INDEX idx_session (session_id, turn_id),
  INDEX idx_feedback (feedback, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.3 agent_context_snapshots

```sql
CREATE TABLE agent_context_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  turn_id INT NOT NULL,
  messages_json LONGTEXT,
  layer_tokens JSON,
  total_input_tokens INT,
  used_memory_ids JSON,
  truncated TINYINT DEFAULT 0,
  truncation_reason VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_turn (session_id, turn_id),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.4 agent_tool_registry

```sql
CREATE TABLE agent_tool_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tool_name VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(64) NOT NULL,
  description TEXT NOT NULL,
  parameters_schema JSON NOT NULL,
  returns_schema JSON NOT NULL,
  category VARCHAR(32) NOT NULL,
  applicable_intents JSON NOT NULL,
  timeout_ms INT DEFAULT 5000,
  max_retries INT DEFAULT 1,
  enabled TINYINT DEFAULT 1,
  version VARCHAR(16) DEFAULT 'v1',
  feign_target VARCHAR(128),
  handler_class VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.5 agent_tool_errors

```sql
CREATE TABLE agent_tool_errors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36),
  turn_id INT,
  tool_name VARCHAR(64),
  error_code VARCHAR(32),
  error_message TEXT,
  args_json JSON,
  retry_count INT,
  degraded TINYINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tool_created (tool_name, created_at),
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.6 agent_session_events

```sql
CREATE TABLE agent_session_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  reason VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.7 user_memory

```sql
CREATE TABLE user_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  memory_type VARCHAR(32) NOT NULL,
  memory_key VARCHAR(64) NOT NULL,
  memory_value TEXT NOT NULL,
  confidence DECIMAL(3,2) DEFAULT 1.00,
  source VARCHAR(16) NOT NULL,
  evidence_count INT DEFAULT 1,
  conflict_flag TINYINT DEFAULT 0,
  volatile_flag TINYINT DEFAULT 0,
  last_used_at DATETIME,
  deleted_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_type_key (user_id, memory_type, memory_key),
  INDEX idx_user_updated (user_id, updated_at),
  INDEX idx_user_type (user_id, memory_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.8 user_memory_evidence

```sql
CREATE TABLE user_memory_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  memory_id BIGINT,
  session_id VARCHAR(36),
  evidence_type VARCHAR(32),
  evidence_payload JSON,
  processed TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_unprocessed (user_id, processed, created_at),
  INDEX idx_memory (memory_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.9 user_memory_history

```sql
CREATE TABLE user_memory_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  memory_type VARCHAR(32),
  memory_key VARCHAR(64),
  memory_value TEXT,
  confidence DECIMAL(3,2),
  source VARCHAR(16),
  action VARCHAR(16) NOT NULL,    -- UPDATE / DELETE / DECAY
  reason VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.10 knowledge_articles

```sql
CREATE TABLE knowledge_articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  topic VARCHAR(32) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  content_md5 CHAR(32) NOT NULL,
  status ENUM('DRAFT','PUBLISHED','DEPRECATED') DEFAULT 'PUBLISHED',
  version INT DEFAULT 1,
  tags JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_topic_status (topic, status),
  INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.11 agent_pending_writes

```sql
CREATE TABLE agent_pending_writes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_table VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  retries INT DEFAULT 0,
  next_retry_at DATETIME,
  status ENUM('PENDING','SUCCESS','FAILED') DEFAULT 'PENDING',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 三、PostgreSQL 表 DDL（pgvector）

### 3.1 启用扩展

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- 用于关键词 LIKE 加速
```

### 3.2 post_vectors

```sql
CREATE TABLE post_vectors (
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
  embedding vector(1024),   -- BGE-M3 输出 1024 维
  embedding_model VARCHAR(32) DEFAULT 'bge-m3',
  embedding_version INT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 向量索引
CREATE INDEX idx_post_vectors_embedding ON post_vectors
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 结构化过滤索引
CREATE INDEX idx_post_vectors_category ON post_vectors(category);
CREATE INDEX idx_post_vectors_school ON post_vectors(school);
CREATE INDEX idx_post_vectors_type ON post_vectors(post_type);
CREATE INDEX idx_post_vectors_created ON post_vectors(created_at DESC);

-- 关键词 trgm 索引（BM25 替代）
CREATE INDEX idx_post_vectors_title_trgm ON post_vectors USING gin (post_title gin_trgm_ops);
CREATE INDEX idx_post_vectors_content_trgm ON post_vectors USING gin (post_content_excerpt gin_trgm_ops);
```

### 3.3 knowledge_vectors

```sql
CREATE TABLE knowledge_vectors (
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

CREATE INDEX idx_knowledge_vectors_embedding ON knowledge_vectors
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_knowledge_vectors_topic ON knowledge_vectors(topic);
CREATE INDEX idx_knowledge_vectors_title_trgm ON knowledge_vectors USING gin (title gin_trgm_ops);
```

## 四、初始化数据

### 4.1 agent_tool_registry 初始工具

MVP 启用 9 个工具，INSERT 语句见 [tool-registry.md](../10-tools-and-apis/tool-registry.md) 第三节。

### 4.2 knowledge_articles 初始文档

按 [knowledge-sources.md](../05-knowledge-base/knowledge-sources.md) 分类树，初始化 50+ 篇帮助文档。

## 五、表与现有 init.sql 的关系

- agent-service 的表**不写入** `backend/docker/mysql/init.sql`（init.sql 是 user/post 的表）。
- 单独建 `backend/docker/mysql/agent-init.sql`，由 agent-service 启动时通过 Flyway 或手动执行。
- 迁移脚本 `backend/docker/mysql/V20260701__agent_tables.sql` 用于已有库的增量 DDL。

## 六、PostgreSQL 部署

docker-compose 新增 postgres 服务：

```yaml
agent-postgres:
  image: pgvector/pgvector:pg16
  container_name: campushare-agent-postgres
  restart: unless-stopped
  environment:
    POSTGRES_DB: agent_vectors
    POSTGRES_USER: agent
    POSTGRES_PASSWORD: ${AGENT_PG_PASSWORD:-agent123456}
  ports:
    - "5432:5432"
  volumes:
    - agent_pg_data:/var/lib/postgresql/data
    - ./backend/docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U agent"]
    interval: 10s
    timeout: 5s
    retries: 5
  networks:
    - campushare-network
```

## 七、决策记录 (ADR)

### ADR-143: agent 表独立 init.sql 不混入主 init.sql
- **理由**：主 init.sql 是 user/post 的责任，agent 表混入会导致职责模糊。独立文件便于维护与回滚。
- **执行**：agent-service 启动时检查表是否存在，不存在则执行 agent-init.sql。

### ADR-144: PostgreSQL 用 pgvector/pgvector 镜像
- **理由**：官方 pgvector 镜像预装扩展，免手动编译。
- **版本**：pg16 + pgvector 0.7+，支持 HNSW 索引。

### ADR-145: post_vectors 含结构化字段（冗余）
- **理由**：向量检索 + 结构化过滤在同一表内完成，避免 JOIN。冗余字段由 post-service 通过内部 API 通知更新。
- **一致性**：帖子编辑后，post-service 调 `/api/internal/agent/knowledge-updated` 通知，agent-service 重新 embedding 并更新冗余字段。

### ADR-146: trgm 索引替代 BM25
- **理由**：MySQL FULLTEXT 中文支持差，ES 太重。PostgreSQL pg_trgm 支持中文 LIKE 加速，作为 BM25 的轻量替代。
- **效果**：`WHERE post_title ILIKE '%关键词%'` 走 gin 索引，延迟 <50ms。

### ADR-147: 向量索引用 HNSW 而非 IVFFlat
- **理由**：HNSW 在小数据量（<100万）召回率与延迟均优于 IVFFlat，且无需训练。
- **参数**：m=16, ef_construction=64（pgvector 推荐默认值）。

### ADR-148: user_memory_history 软删除审计
- **理由**：用户删除记忆需可恢复（30 天回收站），且变更历史便于审计。
- **代价**：history 表增长，每月归档。

### ADR-149: knowledge_articles 用 content_md5 检测变更
- **理由**：重新 embedding 成本高（API 调用），md5 比对避免无变更时的重复 embedding。
- **实现**：更新文档时先算 md5，与现有 md5 相同则跳过 embedding。
