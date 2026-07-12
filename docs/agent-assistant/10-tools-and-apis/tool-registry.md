# 工具注册中心与动态发现

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

统一管理 Agent 可用的工具（Function Calling），支持：动态注册、按意图过滤、版本管理、热更新、权限控制。避免把所有工具 schema 都塞进每次 LLM 请求（浪费 token + 干扰选择）。

## 二、工具注册表

### 2.1 注册表结构

每个工具一条记录，存于 `agent_tool_registry` 表（MySQL）+ Redis 缓存：

```sql
CREATE TABLE agent_tool_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tool_name VARCHAR(64) NOT NULL UNIQUE,       -- search_posts / get_post_detail / ...
  display_name VARCHAR(64) NOT NULL,
  description TEXT NOT NULL,                   -- 给 LLM 看的工具说明
  parameters_schema JSON NOT NULL,             -- JSON Schema 参数定义
  returns_schema JSON NOT NULL,                -- 返回值 Schema
  category VARCHAR(32) NOT NULL,               -- RETRIEVAL/NAVIGATION/USER/KNOWLEDGE
  applicable_intents JSON NOT NULL,            -- ["SEARCH","NAVIGATE"]
  timeout_ms INT DEFAULT 5000,
  max_retries INT DEFAULT 1,
  enabled TINYINT DEFAULT 1,
  version VARCHAR(16) DEFAULT 'v1',
  feign_target VARCHAR(128),                   -- 如 "post-service:/api/internal/post/..."
  handler_class VARCHAR(128),                  -- 本地 handler 全限定名
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.2 Redis 缓存

```
Key: agent:tools:registry
Value: Hash, field=tool_name, value=JSON(完整记录)
TTL: 1 小时，DB 更新时主动 DEL 触发重载
```

## 三、工具分类与清单（MVP）

### 3.1 检索类（RETRIEVAL）

| 工具名 | 用途 | 适用意图 | Feign 目标 |
|--------|------|---------|-----------|
| `search_posts` | 搜索帖子（向量+BM25+结构化） | SEARCH | post-service 内部 API |
| `get_post_detail` | 获取帖子详情（标题/正文/附件/评论数） | SEARCH, NAVIGATE | post-service 内部 API |
| `get_hot_posts` | 获取某分类/学校的热门帖 | SEARCH, NAVIGATE | post-service 内部 API |
| `search_knowledge` | 搜索知识库（HowTo 文档） | HOW_TO | 本地（agent-service 自有表） |

### 3.2 导航类（NAVIGATION）

| 工具名 | 用途 | 适用意图 |
|--------|------|---------|
| `list_categories` | 列出所有主分类与子分类 | NAVIGATE |
| `list_schools` | 列出所有学校 | NAVIGATE |
| `get_category_path` | 根据关键词推断分类路径 | NAVIGATE, SEARCH |

### 3.3 用户类（USER）

| 工具名 | 用途 | 适用意图 |
|--------|------|---------|
| `get_user_profile` | 获取用户基础信息（昵称/认证/简介） | SEARCH（作者信息） |
| `get_user_stats` | 获取用户统计（发帖/获赞/粉丝） | SEARCH |

### 3.4 知识类（KNOWLEDGE）

| 工具名 | 用途 | 适用意图 |
|--------|------|---------|
| `get_help_article` | 根据 key 获取指定帮助文档全文 | HOW_TO |
| `list_help_topics` | 列出帮助主题树 | HOW_TO, NAVIGATE |

### 3.5 反思类（仅进阶期启用）

| 工具名 | 用途 |
|--------|------|
| `verify_citation` | 校验引用 [n] 是否对应真实检索结果 |
| `self_critique` | 调用 R1 对已生成回答做反思 |

## 四、按意图过滤的工具集

每次 LLM 请求只装载与当前意图相关的工具 schema，节省 token：

| 意图 | 装载工具 | 估算 token |
|------|---------|----------|
| HOW_TO | search_knowledge, get_help_article, list_help_topics | 350 |
| SEARCH | search_posts, get_post_detail, get_hot_posts, get_user_profile | 500 |
| NAVIGATE | list_categories, list_schools, get_category_path, get_hot_posts | 400 |
| CLARIFY | （无工具，纯文本回应） | 0 |
| OUT_OF_SCOPE | （无工具） | 0 |
| 反思阶段 | verify_citation, self_critique | 200 |

总装载 token 控制在 L2 预算（500）内，超出则按工具优先级裁剪。

## 五、动态注册与热更新

### 5.1 启动时加载

agent-service 启动时从 MySQL 读取所有 `enabled=1` 的工具，写入 Redis 缓存。

### 5.2 热更新

通过管理后台修改工具配置（如调整 timeout、禁用某工具），DB 更新后：
1. 主动 DEL Redis `agent:tools:registry` Key。
2. 下次请求触发懒加载，从 DB 重新读取。
3. 1 秒内全集群生效（Redis 是共享的）。

### 5.3 版本管理

- 工具 schema 变更（如新增参数）必须升 version（v1→v2）。
- 旧 version 保留 30 天，灰度切换期间两版本共存。
- agent_turns 表记录 `tool_versions` JSON，便于回溯"当时调用的是哪个版本"。

## 六、工具执行流程

```
LLM 输出 tool_call(name, args)
        │
        ├─► 1. 校验: tool_name 是否在注册表 + enabled
        │
        ├─► 2. 校验: args是否符合 parameters_schema
        │
        ├─► 3. 鉴权: 当前用户是否有权调用（如管理类工具仅 ADMIN）
        │
        ├─► 4. 路由:
        │       ├─ feign_target 非空 → Feign 调用其他服务
        │       └─ handler_class 非空 → 本地反射调用
        │
        ├─► 5. 超时/重试: 按 timeout_ms / max_retries 控制
        │
        ├─► 6. 结果校验: 是否符合 returns_schema
        │
        └─► 7. 返回: {summary, data, refs} 给 LLM
```

## 七、工具调用频次控制

### 7.1 单轮上限

- 单次用户提问，同一工具最多调用 3 次（防止 LLM 死循环）。
- 单次用户提问，工具调用总次数 ≤5。
- 超出上限：中断 Agent 循环，返回已收集的结果让 LLM 综合回答。

### 7.2 成本控制

- 每次工具调用后累加 cost，超出单会话预算（¥0.5）时拒绝后续工具调用。
- 工具本身的成本（如 Embedding API 调用）也计入会话成本。

## 八、决策记录 (ADR)

### ADR-070: 工具注册表用 MySQL + Redis 双层
- **理由**：MySQL 是权威源（持久化/审计），Redis 提供低延迟读取（每次请求都要查工具列表）。
- **一致性**：DB 更新后主动 DEL Redis，触发懒加载。1 秒内生效。

### ADR-071: 按意图过滤工具集
- **理由**：全工具 schema 约 2000 token，每次都装浪费预算且干扰 LLM 选择。按意图过滤后单次 ≤500 token。
- **风险**：意图分类错误会导致工具缺失。通过 CLARIFY 兜底（让 LLM 主动追问）。

### ADR-072: 单轮工具调用 ≤5 次
- **理由**：ReAct 循环容易陷入"调一个工具不够再调一个"的死循环，硬上限防止成本失控。
- **可调**：复杂任务（如多 Agent 编排）可放宽到 10 次，但需在会话级配置。

### ADR-073: 工具结果三段式（summary + data + refs）
- **理由**：summary 给 LLM 自然语言理解，data 给结构化字段，refs 给引用溯源。三者分离便于前端渲染。
- **代价**：data 可能较大，按 L3 预算截断。

### ADR-074: 工具 schema 版本化 + 灰度
- **理由**：工具 schema 变更影响 LLM 调用方式，必须可回滚。灰度降低风险。
- **实现**：版本号写入 agent_turns.tool_versions，便于 A/B 对比。
