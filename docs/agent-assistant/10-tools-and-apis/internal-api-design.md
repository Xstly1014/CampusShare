# agent-service API 设计

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、API 分层

agent-service 暴露三类 API：

| 类型 | 路径前缀 | 调用方 | 鉴权 |
|------|---------|-------|------|
| 公开 API | `/api/agent/` | 前端（经 gateway） | JWT（gateway 解析后透传 user_id） |
| 内部 API | `/api/internal/agent/` | 其他微服务（Feign） | 服务间信任（不对外） |
| 管理 API | `/api/admin/agent/` | 管理后台 | JWT + ADMIN 角色 |

## 二、公开 API（前端调用）

### 2.1 创建会话

```
POST /api/agent/sessions
Authorization: Bearer <jwt>
Body: {"title_hint": "可选，会话标题提示"}
Response: 201
{
  "session_id": "uuid",
  "status": "INIT",
  "welcome_message": "你好，我是校园助手，有什么可以帮你？",
  "created_at": "2026-06-30T10:00:00Z"
}
```

- 单用户单会话约束：若已有 ACTIVE 会话，自动关闭旧的并新建。
- welcome_message 由 LLM 生成（带个性化，引用长期记忆）。

### 2.2 发送消息（SSE 流式）

```
POST /api/agent/sessions/{sessionId}/messages
Authorization: Bearer <jwt>
Content-Type: application/json
Accept: text/event-stream
Body: {
  "request_id": "uuid（前端生成，用于幂等）",
  "content": "怎么关闭通知？",
  "client_meta": {"page": "/home", "scroll": 1200}
}
Response: 200, text/event-stream
```

SSE 事件流定义见 [sse-streaming-api.md](./sse-streaming-api.md)。

### 2.3 中断生成

```
POST /api/agent/sessions/{sessionId}/interrupt
Body: {"request_id": "uuid"}
Response: 200 {"interrupted": true}
```

- 中断后已生成内容保留，标记 interrupted=true。

### 2.4 获取会话历史

```
GET /api/agent/sessions/{sessionId}/messages?limit=20&before_turn=10
Response: 200
{
  "messages": [...],
  "has_more": true
}
```

### 2.5 列出历史会话

```
GET /api/agent/sessions?status=ARCHIVED&page=1&size=20
Response: 200
{
  "sessions": [
    {"session_id":"uuid","title":"关于通知设置","turn_count":5,"last_active_at":"..."}
  ]
}
```

### 2.6 反馈（👍/👎）

```
POST /api/agent/sessions/{sessionId}/turns/{turnId}/feedback
Body: {"feedback": "UP" | "DOWN", "reason": "可选，用户选的预设原因"}
Response: 200
```

### 2.7 获取引用详情

```
GET /api/agent/refs/{refType}/{refId}
例: GET /api/agent/refs/post/uuid
Response: 200 {"type":"post","data":{...}}
```

- 用于前端点击引用 [n] 时弹窗展示详情。

### 2.8 获取会话上下文快照（透明性）

```
GET /api/agent/sessions/{sessionId}/turns/{turnId}/context
Response: 200
{
  "used_memory": [...],
  "used_retrieval": [...],
  "tool_calls": [...],
  "layer_tokens": {...}
}
```

- 让用户看到"这次回答用了什么"，增强信任。

## 三、内部 API（其他微服务调用）

### 3.1 上报行为证据

post-service 调用，上报用户在帖子页的行为：

```
POST /api/internal/agent/behavior-evidence
Body: {
  "user_id": "uuid",
  "session_id": "uuid（若来自 Agent 推荐）",
  "evidences": [
    {"type":"CLICK","payload":{"post_id":"uuid","source":"agent"}},
    {"type":"DWELL","payload":{"post_id":"uuid","seconds":120}}
  ]
}
Response: 200 {"accepted": 2}
```

- 异步处理，写入 user_memory_evidence 表。

### 3.2 知识库变更通知

管理后台或其他服务通知 agent-service 知识库有更新：

```
POST /api/internal/agent/knowledge-updated
Body: {"article_ids": [12, 13], "action": "UPDATED" | "DELETED"}
Response: 200
```

- 触发增量重新 embedding。

### 3.3 健康检查

```
GET /api/internal/agent/health
Response: 200
{
  "status": "UP",
  "llm_providers": {"deepseek":"UP","doubao":"UP"},
  "redis": "UP",
  "mysql": "UP",
  "vector_db": "UP"
}
```

## 四、管理 API（管理后台）

### 4.1 工具管理

```
GET    /api/admin/agent/tools                # 列出所有工具
PUT    /api/admin/agent/tools/{name}         # 更新工具配置（timeout/enabled）
POST   /api/admin/agent/tools                # 注册新工具
DELETE /api/admin/agent/tools/{name}         # 禁用工具（软删除）
```

### 4.2 知识库管理

```
GET    /api/admin/agent/knowledge            # 列出知识文档
POST   /api/admin/agent/knowledge            # 新建/更新文档
DELETE /api/admin/agent/knowledge/{id}       # 删除文档
POST   /api/admin/agent/knowledge/reindex    # 触发全量重建索引
```

### 4.3 Prompt 管理

```
GET    /api/admin/agent/prompts              # 列出所有 prompt 版本
PUT    /api/admin/agent/prompts/{name}/active  # 切换活跃版本
POST   /api/admin/agent/prompts/{name}/ab     # 配置 A/B 测试
```

### 4.4 运营数据

```
GET /api/admin/agent/metrics?from=...&to=...
Response: {
  "total_sessions": 1234,
  "avg_turns_per_session": 6.2,
  "feedback_up_rate": 0.72,
  "avg_latency_p50": 1200,
  "avg_latency_p95": 2800,
  "total_cost_yuan": 56.78,
  "intent_distribution": {"SEARCH":0.45,"HOW_TO":0.30,...}
}
```

## 五、网关路由配置

gateway-service 需新增路由规则：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: agent-service
          uri: lb://agent-service
          predicates:
            - Path=/api/agent/**
          filters:
            - StripPrefix=1
            - JwtAuthFilter
          order: 10  # 在 post-service 之前匹配
```

- 白名单：`/api/agent/sessions`（POST 创建会话，需 JWT 但不需会话存在）。
- 其它 `/api/agent/**` 均需 JWT。
- `/api/internal/agent/**` 不走 gateway，仅服务间可达（network 内部）。

## 六、请求限流

| API | 限流策略 |
|-----|---------|
| POST /messages | 单用户 10 次/分钟（防刷） |
| POST /sessions | 单用户 5 次/分钟 |
| GET /sessions | 单用户 30 次/分钟 |
| POST /feedback | 单用户 60 次/分钟 |
| 内部 API | 不限流（服务间信任） |

用 Redis + Lua 实现滑动窗口限流，超限返回 429。

## 七、决策记录 (ADR)

### ADR-080: SSE 而非 WebSocket
- **理由**：SSE 是单向服务器推流，天然契合"LLM 流式输出"场景；WebSocket 双向但 Agent 不需要客户端推流（中断用独立 POST）。
- **代价**：SSE 不支持二进制，但 Agent 只传文本，无影响。

### ADR-081: request_id 幂等
- **理由**：网络抖动导致前端重试，若不幂等会产生重复会话消息。request_id 让服务端识别重试并返回首次结果。
- **实现**：Redis SETNX `agent:request:{request_id}` (TTL 60s)。

### ADR-082: 上下文快照对用户可见
- **理由**：透明性增强信任，也便于用户反馈时附上"用了什么"。
- **代价**：暴露内部结构可能被滥用。仅暴露 memory/retrieval/tool_calls 的摘要，不暴露完整 prompt。

### ADR-083: 内部 API 不走 gateway
- **理由**：gateway 鉴权会增加 5-10ms 延迟，内部服务间调用无需。直接通过 Docker 网络内服务名访问。
- **风险**：若 gateway 被绕过外部直接访问容器端口——Docker 网络隔离保证仅同网络容器可达。

### ADR-084: 单用户消息限流 10/分钟
- **理由**：单轮 LLM 响应 2-5 秒，10/分钟已远超正常使用。防刷 + 控成本。
- **可调**：VIP 用户可放宽到 30/分钟（未来）。
