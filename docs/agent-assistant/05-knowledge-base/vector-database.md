# 向量库选型

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、候选对比

| 方案 | 部署 | 性能(10万级) | 性能(百万级) | 运维 | 过滤能力 | 与现有栈 |
|------|------|:---:|:---:|:---:|:---:|:---:|
| **PG-Vector** | PostgreSQL 扩展 | 好(HNSW) | 中 | 低(SQL) | 强(SQL WHERE) | 引入 PG |
| Milvus | 独立集群 | 优 | 优 | 高 | 中(标量字段) | 新组件 |
| Qdrant | 独立服务 | 优 | 优 | 中 | 强(payload filter) | 新组件 |
| Elasticsearch kNN | ES 集群 | 优 | 优 | 高 | 强 | 新组件 |
| Redis Stack(RediSearch) | Redis 扩展 | 中 | 中 | 低(已有Redis) | 强 | 复用 |
| SQLite-vss | 内嵌 | 中 | 差 | 极低 | 弱 | 内嵌 |

## 二、选型决策

### MVP：PG-Vector（PostgreSQL 16 + pgvector）
- **理由**：
  - 数据量 10-50 万级，HNSW 性能足够（查询 < 50ms）。
  - SQL 原生过滤（`WHERE school_id=? AND post_type=?`）与结构化过滤需求完美契合。
  - 运维简单（一个 PG 容器），无需独立向量集群。
  - 事务支持，便于向量与 metadata 一致性。
- **表设计**：
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;

  CREATE TABLE post_vectors (
    post_id VARCHAR(36) PRIMARY KEY,
    embedding vector(1024),
    school_id VARCHAR(36),
    category_id VARCHAR(36),
    sub_category_id VARCHAR(36),
    post_type VARCHAR(20),
    like_count INT,
    comment_count INT,
    view_count INT,
    status INT,
    deleted INT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
  );
  CREATE INDEX ON post_vectors USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
  CREATE INDEX ON post_vectors (school_id);
  CREATE INDEX ON post_vectors (category_id, sub_category_id);

  CREATE TABLE knowledge_vectors (
    doc_id VARCHAR(64) PRIMARY KEY,
    chunk_id VARCHAR(64),
    embedding vector(1024),
    title VARCHAR(200),
    category VARCHAR(50),
    jump_url VARCHAR(200)
  );
  CREATE INDEX ON knowledge_vectors USING hnsw (embedding vector_cosine_ops);
  ```

### 进阶：迁移 Milvus
- 触发条件：帖子量 > 50 万，或 PG-Vector 查询 P95 > 200ms。
- 迁移路径：抽象 `VectorStore` 接口，切实现类即可。

## 三、检索查询示例（PG-Vector）

```sql
-- 带过滤的向量检索
SELECT post_id, title, 1 - (embedding <=> $1) AS score
FROM post_vectors
WHERE deleted = 0 AND status = 1
  AND ($2::varchar IS NULL OR school_id = $2)
  AND ($3::varchar IS NULL OR category_id = $3)
ORDER BY embedding <=> $1
LIMIT 20;
```

## 四、索引构建与更新

- 全量：批量向量化所有帖子，`COPY` 导入。
- 增量：帖子创建/更新事件 → 向量化 → `UPSERT` post_vectors。
- HNSW 索引在数据导入后构建（先插数据再建索引更快）。

## 五、决策记录 (ADR)

### ADR-003 重申: PG-Vector 作为 MVP
- 已在 `02-architecture/system-architecture.md` 决策。
- 补充：抽象 `VectorStore` 接口为未来切换留口子。
