# 会话状态机与字段定义

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

定义 Agent 会话的全生命周期状态、状态流转规则、触发条件与副作用，确保多轮对话中 Agent 行为可预测、可恢复、可审计。

## 二、会话状态机

### 2.1 状态定义

| 状态 | 含义 | 用户可见行为 |
|------|------|------------|
| `INIT` | 会话刚创建，未发首条消息 | 显示欢迎语 |
| `ACTIVE` | 正常多轮对话中 | 正常流式回答 |
| `TOOL_CALLING` | 正在执行工具调用 | 显示"正在查询..." |
| `WAITING_CLARIFY` | 等待用户回答澄清问题 | 显示澄清问题 + 输入框 |
| `REFLECTING` | 后台反思中（用户已收到首回答） | 不阻塞用户，后台异步 |
| `ARCHIVED` | 会话归档，只读 | 历史会话列表可查看 |
| `CLOSED` | 用户主动关闭 | 不在列表显示 |
| `ERROR` | 不可恢复错误 | 显示"会话异常，请新开" |

### 2.2 状态流转图

```
INIT ──首条消息──► ACTIVE
                     │
                     ├──工具调用──► TOOL_CALLING ──工具返回──► ACTIVE
                     │
                     ├──需要澄清──► WAITING_CLARIFY ──用户回答──► ACTIVE
                     │
                     ├──回答完成──► ACTIVE（后台触发 REFLECTING）
                     │
                     ├──超时30min──► ARCHIVED
                     │
                     ├──用户关闭──► CLOSED
                     │
                     └──不可恢复错误──► ERROR

REFLECTING ──完成──► ACTIVE（更新会话质量分）
ARCHIVED ──用户重新打开──► ACTIVE（新 sessionId，继承长期记忆）
```

### 2.3 流转规则

- 任何状态 → `ERROR`：捕获未处理异常，记录 stacktrace。
- `TOOL_CALLING` / `WAITING_CLARIFY` 不能直接到 `ARCHIVED`，必须先回 `ACTIVE` 或 `ERROR`。
- `REFLECTING` 是伪状态（异步），不阻塞 `ACTIVE`，仅用于监控。
- `ARCHIVED` → `ACTIVE` 必须新建 sessionId，不复活旧会话。

## 三、状态字段定义

### 3.1 会话级字段（agent_sessions 表）

```sql
CREATE TABLE agent_sessions (
  id VARCHAR(36) PRIMARY KEY,              -- sessionId (UUID)
  user_id VARCHAR(36) NOT NULL,
  status ENUM('INIT','ACTIVE','TOOL_CALLING','WAITING_CLARIFY','REFLECTING','ARCHIVED','CLOSED','ERROR') NOT NULL,
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at DATETIME,
  turn_count INT DEFAULT 0,
  prompt_version VARCHAR(16),
  llm_model VARCHAR(32),
  intent_summary VARCHAR(256),             -- 本会话意图分布，如 "SEARCH:5,HOW_TO:2"
  total_input_tokens INT DEFAULT 0,
  total_output_tokens INT DEFAULT 0,
  total_cost_yuan DECIMAL(10,4) DEFAULT 0,
  feedback_positive INT DEFAULT 0,
  feedback_negative INT DEFAULT 0,
  quality_score DECIMAL(3,2),              -- 反思后质量分 0-1
  error_reason VARCHAR(256),
  INDEX idx_user_active (user_id, status, last_active_at),
  INDEX idx_status_archived (status, last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 轮次级字段（agent_turns 表）

```sql
CREATE TABLE agent_turns (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  turn_id INT NOT NULL,
  user_query TEXT,
  intent VARCHAR(32),
  intent_confidence DECIMAL(3,2),
  tools_called JSON,                       -- ["search_posts","get_post_detail"]
  retrieval_refs JSON,                     -- ["post:uuid1","knowledge:12"]
  assistant_answer TEXT,
  input_tokens INT,
  output_tokens INT,
  latency_ms INT,
  cost_yuan DECIMAL(8,4),
  feedback ENUM('UP','DOWN',NULL),
  context_snapshot_id BIGINT,              -- 关联 agent_context_snapshots.id
  interrupted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_turn (session_id, turn_id),
  INDEX idx_session (session_id, turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 上下文快照（agent_context_snapshots 表）

```sql
CREATE TABLE agent_context_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  turn_id INT NOT NULL,
  messages_json LONGTEXT,                  -- 完整 messages 数组
  layer_tokens JSON,                       -- {"L0":1024,"L1":280,...}
  total_input_tokens INT,
  used_memory_ids JSON,                    -- 装载的长期记忆 ID
  truncated TINYINT DEFAULT 0,
  truncation_reason VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_turn (session_id, turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 四、状态转移触发器

### 4.1 进入 TOOL_CALLING

- 触发：LLM 输出 tool_call。
- 动作：HSET meta status=TOOL_CALLING；执行工具；成功后回 ACTIVE。
- 超时：单工具调用 >10s 视为失败，回 ACTIVE 并让 LLM 决定降级。

### 4.2 进入 WAITING_CLARIFY

- 触发：LLM 输出 `{"action":"clarify","question":"..."}`。
- 动作：HSET meta status=WAITING_CLARIFY；HSET slots pending_clarify=question。
- 退出：用户下一条消息到达时，先把消息作为 clarify 的回答喂给 LLM，再清空 pending_clarify，回 ACTIVE。

### 4.3 进入 ARCHIVED

- 触发：last_active_at > 30 分钟，由定时任务发现。
- 动作：
  1. 把 Redis 5 Key 的数据持久化到 MySQL（agent_sessions 更新 + agent_turns 批量插入 + 上下文快照已实时写入）。
  2. 抽取长期记忆（见 [long-term-memory.md](./long-term-memory.md)）。
  3. HSET meta status=ARCHIVED。
  4. DEL 5 个 Redis Key（延迟 5 分钟执行，避免边界竞态）。

### 4.4 进入 ERROR

- 触发：未捕获异常、连续 3 次 LLM 调用失败、上下文组装失败。
- 动作：HSET meta status=ERROR + error_reason；用户侧显示友好错误 + "新开对话"按钮；不自动恢复。

## 五、并发与幂等

### 5.1 状态变更幂等

所有状态转移用 Redis Lua 脚本保证原子：

```lua
-- 期望从 from_status 转移到 to_status
local cur = redis.call('HGET', KEYS[1], 'status')
if cur == ARGV[1] then
  redis.call('HSET', KEYS[1], 'status', ARGV[2])
  return 1
else
  return 0
end
```

避免并发请求把 ACTIVE 误改为 CLOSED 又改回 ACTIVE。

### 5.2 轮次 ID 生成

- turn_id 由 Redis `HINCRBY meta turn_count 1` 生成，保证单会话内严格递增。
- 不用数据库自增，避免 DB 写入延迟导致 turn_id 不连续。

### 5.3 重复请求去重

- 每个用户请求带 `request_id`（前端生成 UUID）。
- Redis SETNX `agent:request:{request_id}` (TTL 60s) 去重，重复请求返回上次结果。

## 六、状态可见性

### 6.1 对用户

- 前端只感知 4 种状态：`正常`（ACTIVE/TOOL_CALLING/REFLECTING）、`等待澄清`（WAITING_CLARIFY）、`已归档`（ARCHIVED）、`异常`（ERROR/CLOSED）。
- TOOL_CALLING 在前端显示为"正在查询..."动画，不暴露具体状态名。

### 6.2 对监控

- Prometheus 指标 `agent_session_status_gauge` 按状态分维度暴露当前会话数。
- Grafana 面板：状态分布堆叠图、状态转移桑基图、ERROR 会话 Top10 原因。

### 6.3 对审计

- 所有状态转移写入 `agent_session_events` 表（session_id, from_status, to_status, reason, ts）。
- 保留 90 天，便于排查"会话为什么挂了"。

## 七、边界情况

### 7.1 用户多端登录

- 同一 user_id 在 Web 和小程序同时打开助手：两端共享同一 ACTIVE 会话（单会话约束）。
- 一端发消息，另一端通过 SSE 订阅同一 session_id 收到推送。
- 实现复杂度高，MVP 阶段先不支持，仅允许单端活跃（另一端提示"助手已在其他设备打开"）。

### 7.2 服务重启

- agent-service 重启时，Redis 中的 ACTIVE 会话不丢失（TTL 2h）。
- 重启后定时任务发现 status=ACTIVE 但 last_active_at > 30 分钟的，正常归档。
- 正在处理的请求：SSE 断开，前端检测到断开后重连，重连后从 Redis 取最近一轮 assistant 消息补全显示。

### 7.3 数据库写入失败

- agent_turns 写入失败：不阻塞用户回答，记录到 `agent_pending_writes` 队列，异步重试。
- agent_context_snapshots 写入失败：同上，但优先级低（仅影响复盘，不影响业务）。
- agent_sessions 状态更新失败：告警，人工介入，因为状态不一致会导致后续逻辑错乱。

## 八、决策记录 (ADR)

### ADR-064: 8 状态机 + 严格转移规则
- **理由**：状态过少无法表达 TOOL_CALLING/WAITING_CLARIFY 等关键中间态；状态过多增加维护成本。8 个状态覆盖所有业务场景。
- **替代方案**：5 状态（合并 TOOL_CALLING/REFLECTING 到 ACTIVE）——丢失中间态可观测性。

### ADR-065: 轮次 ID 用 Redis HINCRBY 而非 DB 自增
- **理由**：DB 自增要求每轮先写 DB 才能拿到 ID，延迟高且失败处理复杂。Redis 原子递增 <1ms。
- **代价**：DB 与 Redis 可能短暂不一致（Redis 递增成功但 DB 写入失败），由 `agent_pending_writes` 队列补偿。

### ADR-066: 状态转移用 Lua 脚本保证原子
- **理由**：CAS 语义（compare-and-set）需要原子性，Lua 脚本在 Redis 单线程内保证。
- **替代方案**：WATCH/MULTI/EXEC——更复杂且乐观锁失败需重试。

### ADR-067: 上下文快照实时写入而非异步
- **理由**：快照是复盘"为什么答错"的唯一证据，异步写入可能丢失。实时写虽有延迟（≈2ms），但可接受。
- **优化**：批量插入（每 5 条一次）减少 DB 往返。

### ADR-068: 会话事件表保留 90 天
- **理由**：90 天覆盖一个完整学期，足够排查周期性问题。更长保留成本高且价值递减。
- **归档**：90 天后导出到对象存储（S3/MinIO），冷查询走对象存储。

### ADR-069: MVP 不支持多端同时活跃
- **理由**：多端会话同步涉及 SSE 多播、状态强一致，复杂度高。校园问答场景单端足够。
- **未来**：通过 session_group 概念扩展，每个 group 内多 session 共享长期记忆但独立短期记忆。
