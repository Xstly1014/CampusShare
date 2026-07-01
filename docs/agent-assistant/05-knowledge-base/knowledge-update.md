# 知识库更新机制

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、知识库（帮助文档）更新

### 1.1 源文件管理
- 知识文档存仓库 `docs/agent-assistant/knowledge-docs/`，Git 版本化。
- 修改走 PR 评审，确保内容准确。

### 1.2 热更新流程
```
PR 合并 → 部署 agent-service → 启动时扫描 knowledge-docs/
  → 解析 frontmatter + 正文
  → 向量化（BGE-M3）
  → UPSERT knowledge_vectors 表
```
- 运行时定时（每小时）检测文件变更（mtime），变更则重新向量化对应文档。
- 提供管理接口 `POST /api/internal/agent/reload-knowledge` 手动触发。

### 1.3 向量重建
- 文档删除：从 knowledge_vectors 删除对应 doc_id。
- 文档更新：先删后插（避免 chunk 残留）。

## 二、帖子向量更新

### 2.1 全量初始化
- 服务首次上线，批量向量化所有 posts 表帖子（通过 Feign 分页拉取）。
- 估算：2 万帖 × 200ms/批(64条) ≈ 1 分钟。

### 2.2 增量同步
**方案 A：事件驱动（推荐）**
- post-service 在帖子创建/更新/删除后，发事件到 Redis Stream `post.events`。
- agent-service 消费者订阅，向量化并 UPSERT/DELETE。
- 延迟：秒级。

**方案 B：定时扫描（兜底）**
- 每 5 分钟扫描 `posts.update_time > last_scan_time` 的帖子，增量同步。
- 作为事件丢失的兜底。

### 2.3 一致性
- 帖子逻辑删除（deleted=1）→ 向量库标记或删除。
- 帖子状态变更（status）→ 同步更新 post_vectors.status，检索时过滤。
- 计数（like_count 等）定期刷新（用户检索时实时性要求不高，可 5 分钟刷一次）。

## 三、向量版本管理

- Embedding 模型升级（如 BGE-M3 → 新模型）需重建全部向量。
- 提供 `POST /api/internal/agent/rebuild-vectors` 管理接口（仅管理员）。
- 重建期间双索引并存，切换后删旧。

## 四、决策记录 (ADR)

### ADR-022: 帖子向量同步用事件驱动 + 定时兜底
- **理由**：事件驱动延迟低；定时兜底防事件丢失。
- **实现**：Redis Stream（已有 Redis，无新组件）。
