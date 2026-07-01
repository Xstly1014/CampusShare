# 短期对话记忆（Redis）

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

为单次会话提供低延迟（<5ms）的读写，支撑：多轮指代消解、槽位累积、压缩摘要、上下文快照。会话结束或超时后归档到 MySQL 长期存储。

## 二、Redis 数据结构

一个会话在 Redis 中占用 5 个 Key，统一前缀 `agent:session:{sessionId}`：

### 2.1 会话元数据（Hash）

```
Key: agent:session:{sessionId}:meta
Fields:
  user_id          : 用户ID
  status           : ACTIVE | WAITING_CLARIFY | CLOSED | ARCHIVED
  intent_history   : JSON 数组，如 ["SEARCH","SEARCH","CLARIFY"]
  current_intent   : 最近一次意图
  turn_count       : 当前轮次
  started_at       : 会话开始时间戳
  last_active_at   : 最近活动时间戳
  prompt_version   : 当前使用的 prompt 版本号
  llm_model        : 当前使用的模型
TTL: 2 小时（每次活跃自动续期）
```

### 2.2 短期对话窗口（List，左进右出）

```
Key: agent:session:{sessionId}:messages
Element: JSON 序列化的单轮消息
  {
    "turn_id": 7,
    "role": "user|assistant|tool",
    "content": "消息正文",
    "tokens": 156,
    "tool_name": "search_posts",   // 仅 role=tool
    "tool_args": {...},             // 仅 role=tool
    "refs": ["post:uuid1"],         // 引用资源
    "pinned": false,                // 是否 Pin
    "ts": 1719700000
  }
MaxLength: 20（超出触发压缩）
```

### 2.3 滚动摘要（String）

```
Key: agent:session:{sessionId}:rolling_summary
Value: 摘要文本（≤300 字）
TTL: 2 小时
```

### 2.4 槽位（Hash）

```
Key: agent:session:{sessionId}:slots
Fields:
  confirmed_intent
  target_category
  target_school
  mentioned_post_ids     : JSON 数组
  rejected_post_ids      : JSON 数组
  user_constraints       : JSON 对象
  pending_clarify        : 待澄清问题 JSON
TTL: 2 小时
```

### 2.5 Pinned 消息（List）

```
Key: agent:session:{sessionId}:pinned
Element: 完整消息 JSON
MaxLength: 5
TTL: 2 小时
```

## 三、读写时序

### 3.1 用户提问时（读）

```
1. GET meta（确认 status=ACTIVE、续期 TTL）
2. GET messages（最近 20 条）+ rolling_summary + slots + pinned
3. 拼装为 L4 历史：
   [rolling_summary]
   [slots JSON]
   [pinned messages]
   [最近 N 条原文消息]  ← N 由 token 预算决定
4. 进入 ContextAssembler
```

### 3.2 LLM 回答后（写）

```
1. RPUSH messages 本轮 user + assistant + tool 消息
2. 若 messages 长度 > 10，触发压缩：
   a. 调 LLM 生成新 rolling_summary + 更新 slots + 检测 pin
   b. LTRIM messages 保留最近 5 条
   c. SET rolling_summary / HSET slots / RPUSH pinned
3. HINCRBY meta turn_count 1
4. HSET meta last_active_at / current_intent / intent_history
5. EXPIRE 所有 5 个 Key 续期 2 小时
```

### 3.3 会话关闭时（归档）

```
1. HSET meta status=CLOSED
2. 异步任务：把 messages + rolling_summary + slots 持久化到 MySQL agent_sessions_detail 表
3. 抽取长期记忆（见 long-term-memory.md）写入 user_memory 表
4. DEL 5 个 Key
```

## 四、并发与一致性

### 4.1 单用户串行

- 同一 user_id 同时只允许一个 ACTIVE 会话（新会话开启时关闭旧的）。
- 用 Redis 分布式锁 `agent:user_lock:{userId}`（TTL 10s）保证创建会话的原子性。

### 4.2 单会话串行

- 同一 session 的请求用 `agent:session_lock:{sessionId}`（TTL 30s）串行化，避免并发写 messages 导致乱序。
- 锁失败返回 429，提示用户"上一条消息还在处理"。

### 4.3 SSE 中断处理

- 流式输出过程中用户断连：保留已生成的 assistant 消息到 messages，但标记 `interrupted=true`。
- 下轮上下文装入时，对 interrupted 消息加注 `[此条回复曾中断]`，让 LLM 知道。

## 五、TTL 与过期策略

| Key | TTL | 过期动作 |
|-----|-----|---------|
| meta | 2h（续期） | 过期后认为会话僵尸，由定时任务归档 |
| messages | 2h（续期） | 跟随 meta |
| rolling_summary | 2h | 跟随 meta |
| slots | 2h | 跟随 meta |
| pinned | 2h | 跟随 meta |
| user_lock | 10s | 自动释放 |
| session_lock | 30s | 自动释放 |

### 5.1 僵尸会话清理

- 每分钟扫描 `agent:session:*:meta`（用 SCAN，避免 KEYS 阻塞）。
- 发现 `last_active_at` > 30 分钟前且 status=ACTIVE 的：标记为 ARCHIVED，触发归档。
- 归档失败重试 3 次，仍失败记录到 `agent_archive_failures` 表人工介入。

## 六、容量估算

- 单会话 5 个 Key，平均内存：messages 20KB + summary 1KB + slots 1KB + pinned 10KB + meta 1KB ≈ 33KB。
- 同时在线 1000 会话：33MB，远低于 Redis 默认 maxmemory。
- 配置 `maxmemory-policy=allkeys-lru`，极端情况下淘汰最久未活跃的会话（先归档再淘汰）。

## 七、与 MySQL 的边界

| 数据 | Redis | MySQL |
|------|-------|-------|
| 当前活跃会话 | ✅ 主存 | ❌ 不写 |
| 历史会话摘要 | ❌ 不存 | ✅ agent_sessions 表 |
| 会话详细消息 | ❌ 不存（仅最近 20 条） | ✅ agent_session_messages 表（归档后） |
| 长期用户画像 | ❌ 不存 | ✅ user_memory 表 |
| 上下文快照 | ❌ 不存 | ✅ agent_context_snapshots 表 |

Redis 只负责"热数据 + 低延迟"，MySQL 负责"冷数据 + 持久化 + 分析"。

## 八、决策记录 (ADR)

### ADR-054: 单会话 5 Key 结构而非单一 JSON
- **理由**：单 JSON 每次读写全量序列化，messages 增大后延迟上升。5 Key 分离后只读需要的部分（如压缩只读 messages+summary，不读 slots）。
- **代价**：TTL 续期要 5 次 EXPIRE，用 pipeline 合并为 1 次 RTT。

### ADR-055: 单用户单会话约束
- **理由**：多会话并行会导致上下文分裂、画像更新冲突。校园问答场景单会话足够。
- **例外**：未来支持"多窗口对话"时，通过 session_group 概念扩展，但不打破单会话原子性。

### ADR-056: 流式中断保留 partial 消息
- **理由**：丢弃 partial 会让用户多轮上下文断裂（用户问"刚才那个继续"时 LLM 不知道"那个"是什么）。
- **风险**：partial 消息可能不完整，通过 `[此条回复曾中断]` 标记让 LLM 自行判断。

### ADR-057: 僵尸会话由定时任务归档而非依赖 TTL
- **理由**：TTL 过期后数据直接消失，无法归档到 MySQL。定时任务可在归档后再 DEL。
- **频率**：每分钟扫描，30 分钟未活跃触发归档。

### ADR-058: Redis 用 SCAN 而非 KEYS
- **理由**：KEYS 在大库下阻塞主线程。SCAN 增量迭代，单次 <1ms。
- **实现**：每次扫描 100 个 cursor，间隔 100ms，全库扫描 <30s。
