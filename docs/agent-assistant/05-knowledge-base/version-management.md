# 版本管理

> 状态: 草稿  
> 最后更新: 2026-07-06

知识库文档版本管理，支持历史快照查看与一键回滚。

## 一、SemVer 版本号格式

```
vMAJOR.MINOR.PATCH
```
- 初始版本：`v1.0.0`
- 文档更新：`nextPatch` → `v1.0.1`（默认）
- 结构性变更：`nextMinor` → `v1.1.0`（手动触发）
- 重大重写：`nextMajor` → `v2.0.0`（手动触发）

**实现**：`SemVer` 工具类（parse / initial / parseOrInitial / nextPatch / nextMinor / nextMajor / compareTo）

## 二、完整快照历史表

### 2.1 表设计
```sql
CREATE TABLE knowledge_article_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    title VARCHAR(256),
    topic VARCHAR(64),
    content TEXT,
    content_md5 VARCHAR(32),
    version VARCHAR(16) NOT NULL,
    chunk_count INT,
    quality_score DOUBLE,
    tags VARCHAR(256),
    snapshot_reason VARCHAR(32),  -- UPDATE / ROLLBACK / DEPRECATED
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_article_id (article_id),
    INDEX idx_created_at (created_at)
);
```

### 2.2 快照时机
| 事件 | snapshot_reason | 说明 |
|------|----------------|------|
| 文档更新前 | UPDATE | 保存旧内容，便于回滚 |
| 回滚前 | ROLLBACK | 保存当前内容（回滚也是新版本） |
| 废弃前 | DEPRECATED | 保存最后状态 |

## 三、回滚流程

```
rollback(articleId, targetVersion)
  1. 查询目标版本快照（knowledge_article_versions）
  2. snapshot 当前文章（reason=ROLLBACK）
  3. 用目标快照内容更新主表
  4. version = nextPatch(current)  -- 回滚后版本递增，不回退
  5. 重新摄入 PG 向量（reingestArticle）
```

**关键设计**：回滚后版本号递增（不回退），表示产生了新版本。这避免了版本号回退造成的混淆。

### 3.1 回滚后 PG 同步
回滚仅更新 MySQL 主表，PG 向量需通过 `KnowledgeIngestionService.reingestArticle(articleId)` 重新生成：
1. 从 MySQL 读取回滚后的内容
2. 重新分块 + embedding
3. 更新 PG `knowledge_vectors`（先删后插）

## 四、反馈更新

```
updateFeedback(articleId, positive)
  1. articleMapper.selectById(articleId)
  2. feedbackScore += positive ? 0.05 : -0.05
  3. clamp [0, 1]
  4. qualityScore = qualityScorer.score(...)  -- 重算质量评分
  5. articleMapper.updateById(article)
  6. knowledgeVectorStore.updateQualityScore(articleId, qualityScore)  -- 同步 PG
```

## 五、API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/internal/agent/knowledge/articles/{id}/versions` | GET | 查看版本历史 |
| `/api/internal/agent/knowledge/articles/{id}/rollback` | POST | 回滚到指定版本 |
| `/api/internal/agent/knowledge/articles/{id}/feedback` | POST | 提交反馈（点赞/点踩） |
| `/api/internal/agent/knowledge/articles/{id}/deprecate` | POST | 废弃文档 |

## 六、决策记录 (ADR)

### ADR-023: 完整快照 vs diff 的取舍
- **选择**：完整快照（存储整篇文档内容）。
- **理由**：
  1. 回滚 O(1) 读取：无需计算 diff 反向应用，直接用快照覆盖主表。
  2. 存储成本低：知识文档小（200-800 字），快照表不会过大。
  3. 实现简单：无需 diff 算法，无逆应用冲突。
- **代价**：存储冗余（同一内容可能存多份），但知识库规模有限（< 100 篇文档），可接受。
