# 长期用户画像记忆（MySQL + PostgreSQL/pgvector）

> 状态: 核心已实现
> 最后更新: 2026-07-10

## 一、目标

跨会话沉淀用户偏好与行为模式，让 Agent 在新会话首轮就能个性化。例如：用户上次问"怎么关闭通知"，这次问"怎么改密码"，Agent 知道该用户是"功能操作型"用户，可以直接给步骤而非解释概念。

## 二、记忆分类

按"显式 vs 隐式" + "稳定 vs 动态"分四象限：

| 类型 | 显式（用户说的） | 隐式（行为推断） |
|------|----------------|----------------|
| 稳定（周/月级） | 偏好声明："我喜欢 PDF 格式"、"我是计算机专业" | 主要访问分类：软件(60%)、面经(20%) |
| 动态（会话级） | 当前任务："我在找考研真题" | 最近 7 天点击帖子的平均阅读时长 |

## 三、表结构设计

### 3.1 user_memory（用户画像主表）

```sql
CREATE TABLE user_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  memory_type VARCHAR(32) NOT NULL,        -- PREFERENCE/BEHAVIOR/FACT/TASK
  memory_key VARCHAR(64) NOT NULL,         -- 如 preferred_format / major / top_category
  memory_value TEXT NOT NULL,              -- JSON 或文本
  confidence DECIMAL(3,2) DEFAULT 1.00,    -- 置信度 0-1
  source VARCHAR(16) NOT NULL,             -- EXPLICIT / INFERRED
  evidence_count INT DEFAULT 1,            -- 证据数（隐式累积）
  last_used_at DATETIME,                   -- 最近一次被装载入上下文的时间
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_type_key (user_id, memory_type, memory_key),
  INDEX idx_user_updated (user_id, updated_at),
  INDEX idx_user_type (user_id, memory_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 user_memory_evidence（证据表，隐式记忆用）

```sql
CREATE TABLE user_memory_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(36) NOT NULL,
  memory_id BIGINT NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  evidence_type VARCHAR(32),                -- CLICK / DWELL / LIKE / SEARCH / FEEDBACK
  evidence_payload JSON,                    -- 如 {"post_id":"...","dwell_sec":120}
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_memory (memory_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 memory_decay_log（衰减日志）

记录每周衰减任务执行的变更，便于审计。

## 四、记忆抽取

### 4.1 显式抽取

会话归档时，LLM 扫描会话摘要，抽取偏好声明：

```
Prompt: 从以下对话摘要中抽取用户的显式偏好与事实声明。
输出 JSON 数组，每项含 {type, key, value, evidence_quote}。
若没有，输出 []。

摘要: {rolling_summary}
```

示例输出：
```json
[
  {"type":"PREFERENCE","key":"preferred_format","value":"PDF","evidence_quote":"我比较喜欢 PDF"},
  {"type":"FACT","key":"major","value":"计算机科学","evidence_quote":"我是计算机专业的"}
]
```

抽取结果 UPSERT 到 user_memory：
- 已存在同 key：`confidence = min(1, confidence + 0.1)`，更新 value。
- 新 key：插入，confidence=1.0，source=EXPLICIT。

### 4.2 隐式推断

定期批处理任务（每小时）扫描 `user_memory_evidence` 累积证据：

- 同一行为出现 ≥3 次（如 3 次点击软件分类帖子）：upsert 隐式记忆，confidence = min(count/10, 0.8)。
- 证据数 ≥10 且与现有显式记忆冲突：不覆盖显式，但记录 `conflict_flag=true` 供人工或 LLM 裁决。

### 4.3 证据来源

| 业务事件 | 证据类型 | 触发点 |
|---------|---------|--------|
| 点击 Agent 推荐的帖子 | CLICK | agent_service 接收 click 事件 |
| 帖子详情页停留 >30s | DWELL | post-service 上报（Feign 内部 API） |
| 点赞/收藏推荐帖 | LIKE | post-service 上报 |
| Agent 会话中搜索词 | SEARCH | agent_service 内部 |
| 用户给回答 👍/👎 | FEEDBACK | agent_service 接收反馈 |

证据上报走异步队列（Redis Stream），批处理消费，避免阻塞主链路。

## 五、记忆召回（装载入上下文）

### 5.1 装载策略

新会话首轮或 L1 装载时，按以下顺序选 Top-K（K=5）：

1. **强相关**：memory_key 与当前意图/槽位匹配的（如意图=SEARCH 且 category=软件 → 装 top_category=软件 的记忆）。
2. **高置信 + 近期使用**：confidence ≥0.7 且 last_used_at 在 7 天内。
3. **偏好类优先于行为类**：PREFERENCE > FACT > BEHAVIOR > TASK。

### 5.2 装载格式

L1 装载的画像摘要文本：

```
[用户画像]
- 偏好格式: PDF（置信 1.0，用户明确声明）
- 专业: 计算机科学（置信 1.0）
- 主要兴趣分类: 软件、面经（置信 0.7，行为推断）
- 最近任务: 找考研真题（3 天前）
```

每条 ≤30 字，总长 ≤300 字（≈200 token）。

### 5.3 隐私边界

- 敏感字段（身份证、密码、私信内容）**永不抽取**为记忆。
- 隐私设置关闭"允许被搜索"的用户：其行为证据不入 evidence 表。
- 用户可在设置页查看/删除自己的 user_memory（透明性）。

## 六、记忆衰减

### 6.1 衰减规则

每周日 02:00 执行衰减任务：

| 记忆类型 | 衰减率 | 规则 |
|---------|--------|------|
| EXPLICIT PREFERENCE/FACT | 0 | 不衰减（用户声明不会过期） |
| INFERRED BEHAVIOR | 0.1/周 | confidence *= 0.9，<0.3 删除 |
| TASK | 0.3/周 | 4 周未更新删除 |
| EXPLICIT revoked | 立即删除 | 用户在新会话中说"我不再偏好 X" |

### 6.2 冲突解决

- 新显式声明覆盖旧显式：直接 UPSERT，旧记录移入 `user_memory_history`。
- 隐式与显式冲突：保留显式，隐式 evidence 累积但不生效，confidence 不增。
- 同 key 多次变更（>3 次/月）：标记 `volatile=true`，装载时降权。

## 七、与现有微服务的边界

- **agent-service 拥有** user_memory / user_memory_evidence 表（不与 user-service 共享）。
- **跨服务取数**：行为证据需要 post-service 的点击/停留数据，通过 Feign 内部 API `POST /api/internal/post/behavior-batch` 拉取（agent-service 主动拉，post-service 不主动推）。
- **不共享表**：user-service 的 users 表是用户基础信息，agent-service 的 user_memory 是画像推断，两者分离。

## 八、可解释性与可控性

### 8.1 用户可见

设置页新增"我的助手记忆"入口：
- 列表展示所有 user_memory 记录（type/key/value/confidence/source/updated_at）。
- 支持单条删除（软删除，移入 user_memory_history）。
- 支持一键清空（全部软删除）。

### 8.2 审计

每次记忆被装载入上下文，记录到 `agent_context_snapshots` 的 `used_memory_ids` 字段。用户可查看"这次回答用到了我的哪些记忆"。

## 九、决策记录 (ADR)

### ADR-059: 长期记忆用 MySQL 而非向量库
- **理由**：画像记忆是结构化键值对，按 user_id + key 精确查询，不需要向量检索。MySQL 索引足够。
- **未来扩展**：若增加"自由文本型记忆"（如用户口述的复杂背景），可另起 user_memory_vectors 表用 pgvector。

### ADR-060: 显式不衰减、隐式周衰减 0.1
- **理由**：用户声明的偏好不会自然过期（除非用户改口）；行为推断会随兴趣转移而失效，需衰减。
- **参数 0.1**：经验值，意味着 7 周未强化的隐式记忆 confidence 降到 0.5 以下。可调。

### ADR-061: 跨服务行为证据走 Feign 拉取
- **理由**：遵循 AGENT-WORKFLOW.md 第六章铁律，禁止跨服务直接访问 DB。post-service 的点击数据必须由 post-service 自己提供内部 API。
- **频率**：每小时拉一次最近 1 小时的增量，agent-service 缓存到 user_memory_evidence。

### ADR-062: 用户可查看/删除记忆
- **理由**：透明性是用户信任的基础。GDPR/PIPL 要求用户对个人画像有控制权。
- **实现**：软删除 + history 表，便于误删恢复（30 天回收站）。

### ADR-063: 记忆装载写入上下文快照
- **理由**：复盘"为什么这次回答个性化了/没个性化"必须看到装载了哪些记忆。
- **副作用**：快照表膨胀，30 天后归档对象存储。

---

## 十、实现详情（已上线）

### 10.1 MemoryVectorStore 向量存储

**数据库**：PostgreSQL + pgvector 扩展，用于语义记忆检索。

**memory_vectors 表结构说明**：
- 主键：`id VARCHAR(36)`（UUID，非 BIGSERIAL）
- 内容字段：`memory_value TEXT`（非 content）
- 向量字段：`embedding vector(1536)`（text-embedding-3-small 维度）
- 衰减字段：`decay_score DECIMAL(3,2) DEFAULT 1.00`
- 访问统计：`access_count INT DEFAULT 0`, `last_accessed_at DATETIME`
- 软删除：`is_active BOOLEAN DEFAULT true`

**核心方法**：
- `upsert()`：插入或更新记忆向量
- `search()`：HNSW 索引余弦相似度检索
- `keywordSearch()`：pg_trgm 关键词模糊匹配
- `updateDecayScore()`：同步衰减分数
- `recordAccess()`：记录访问次数和时间
- `softDelete()`：软删除（is_active=false）

### 10.2 双路召回 + RRF 融合

MemoryRetrievalService 实现混合检索策略：

1. **PROFILE 全量装载**：PREFERENCE + FACT 类型，confidence ≥ 0.5 的记忆优先加载
2. **向量召回**：HNSW 余弦相似度检索 Top-K 语义相关记忆
3. **关键词召回**：pg_trgm 模糊匹配记忆键值
4. **RRF 融合重排**：Reciprocal Rank Fusion（k=60）合并两路结果
5. **批量更新访问统计**：异步更新 access_count + last_accessed_at
6. **衰减过滤**：decay_score < 阈值的记忆不返回

### 10.3 ConflictResolver 冲突仲裁服务

**仲裁规则**：
- 显式记忆（EXPLICIT）优先级 > 隐式记忆（IMPLICIT/INFERRED）
- 同类型冲突：按时间（新 > 旧）和置信度（高 > 低）仲裁
- LLM 失败降级规则：EXPLICIT > INFERRED，新 > 旧
- 冲突结果写入 `user_memory_history`（action=CONFLICT_RESOLVED）
- 冲突记忆标记 conflictFlag 降权

### 10.4 user_memory_history 审计日志

所有记忆变更操作均记录审计：
- INSERT：新增记忆
- UPDATE：更新记忆内容/置信度
- DELETE：软删除记忆
- DECAY：衰减执行
- CONFLICT_RESOLVED：冲突仲裁
- ACCESSED：记忆被检索使用

通过 `logMemoryHistory()` 统一封装方法写入。

### 10.5 类型安全枚举

为避免魔法字符串，实现三个枚举类：

| 枚举类 | 值 | 说明 |
|--------|-----|------|
| MemoryType | PREFERENCE, FACT, BEHAVIOR, TASK, SKILL, EVENT | 记忆类型 |
| MemorySource | EXPLICIT, IMPLICIT, INFERRED | 记忆来源 |
| MemoryAction | INSERT, UPDATE, DELETE, DECAY, CONFLICT_RESOLVED, ACCESSED | 操作类型 |

数据库保持 String 类型兼容，代码层面使用枚举保证类型安全。

### 10.6 差异化衰减策略

**每周衰减率**：

| 类型/来源 | 基础衰减率（每周） | 剩余系数 | 说明 |
|----------|-----------------|---------|------|
| EXPLICIT（所有类型） | 0.02 | ×0.98 | 用户明确声明的记忆接近永久 |
| PREFERENCE/FACT（隐式） | 0.03 | ×0.97 | 稳定偏好/事实缓慢变化 |
| BEHAVIOR（隐式） | 0.10 | ×0.90 | 行为模式中等衰减 |
| TASK（隐式） | 0.30 | ×0.70 | 任务类记忆快速衰减（4周≈0.24阈值） |

**访问频率增强**：
- 近 7 天内 `access_count ≥ 3` 次：衰减率减半（×0.5 系数）
- 每次被检索使用：access_count+1，last_accessed_at=now()

**衰减同步**：
- `decay_score` 同步到 `memory_vectors` 表
- 软删除阈值：`decay_score < 0.2` → `is_active = false`
