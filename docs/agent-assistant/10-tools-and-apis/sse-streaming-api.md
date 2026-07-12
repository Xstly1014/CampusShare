# SSE 流式输出协议

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

通过 Server-Sent Events 把 Agent 的实时状态（思考/工具调用/Token 生成/引用/完成）推流给前端，让用户感知"Agent 在做什么"，首 token 延迟目标 ≤1.5s。

## 二、SSE 基础

### 2.1 协议

- Content-Type: `text/event-stream`
- 每个 event 格式：
  ```
  event: <event_type>
  data: <json_payload>

  ```
- 客户端用 `EventSource` 或 `fetch + ReadableStream` 接收。
- 心跳：每 15 秒发送 `:keepalive\n\n` 防止代理超时断连。

### 2.2 连接生命周期

```
POST /api/agent/sessions/{id}/messages
   │
   ├─► 200 text/event-stream 开始
   │
   ├─► event: status      （Agent 状态变化）
   ├─► event: tool        （工具调用开始/结束）
   ├─► event: token       （LLM token 流）
   ├─► event: refs        （引用列表）
   ├─► event: clarify     （澄清问题）
   ├─► event: error       （错误）
   └─► event: done        （完成，连接关闭）
```

## 三、事件类型定义

### 3.1 status（状态变化）

```json
event: status
data: {
  "turn_id": 7,
  "phase": "intent_classifying",
  "message": "正在理解你的问题..."
}
```

phase 枚举：
- `intent_classifying` 意图分类中
- `query_rewriting` 查询改写中
- `retrieving` 检索中
- `tool_calling` 工具调用中
- `generating` 生成回答中
- `reflecting` 后台反思中（不阻塞）

### 3.2 tool（工具调用）

```json
event: tool
data: {
  "tool_name": "search_posts",
  "phase": "start",
  "args_preview": {"query":"关闭通知","category":"通知设置"}
}
```

工具完成后：
```json
event: tool
data: {
  "tool_name": "search_posts",
  "phase": "end",
  "duration_ms": 320,
  "result_summary": "找到 3 篇相关帖子",
  "degraded": false
}
```

### 3.3 token（LLM token 流）

```json
event: token
data: {
  "turn_id": 7,
  "content": "要关闭通知"
}
```

- 每个 token 或 token 组（2-4 个）一条 event，前端拼接显示。
- 前端遇到 `[n]` 标记时渲染为可点击的引用角标。

### 3.4 refs（引用列表）

回答生成完成后、done 之前发送：

```json
event: refs
data: {
  "refs": [
    {"marker":"[1]","type":"post","id":"uuid1","title":"考研真题合集","url":"/post/uuid1"},
    {"marker":"[2]","type":"knowledge","id":12,"title":"如何关闭通知","url":"/help/12"}
  ]
}
```

### 3.5 clarify（澄清问题）

当 Agent 判断需要追问时：

```json
event: clarify
data: {
  "question": "你是想关闭哪种通知？点赞通知/评论通知/私信通知？",
  "options": ["点赞通知","评论通知","私信通知","全部关闭"],
  "allow_free_input": true
}
```

- options 用于前端渲染快捷选项按钮。
- allow_free_input=true 时用户也可手动输入。

### 3.6 error（错误）

```json
event: error
data: {
  "error_code": "LLM_TIMEOUT",
  "message": "助手响应超时，请稍后再试",
  "retryable": true,
  "partial_answer": "已生成的部分回答..."
}
```

- retryable=true 时前端显示"重试"按钮。
- partial_answer 保留已生成的 token，避免用户白等。

### 3.7 done（完成）

```json
event: done
data: {
  "turn_id": 7,
  "total_tokens": 580,
  "latency_ms": 2300,
  "feedback_url": "/api/agent/sessions/.../turns/7/feedback"
}
```

发送后服务端关闭连接。

## 四、前端接入

### 4.1 EventSource 替代方案

由于 `POST` 请求不能用 EventSource（仅支持 GET），改用 `fetch + ReadableStream`：

```typescript
async function streamMessage(sessionId: string, content: string) {
  const resp = await fetch(`/api/agent/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {'Content-Type':'application/json','Authorization':`Bearer ${token}`},
    body: JSON.stringify({request_id: uuid(), content})
  });
  const reader = resp.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, {stream:true});
    const events = buffer.split('\n\n');
    buffer = events.pop() || '';
    for (const evt of events) {
      handleEvent(evt);
    }
  }
}
```

### 4.2 事件处理

```typescript
function handleEvent(raw: string) {
  const lines = raw.split('\n');
  const eventType = lines.find(l => l.startsWith('event:'))?.slice(6).trim();
  const data = JSON.parse(lines.find(l => l.startsWith('data:'))?.slice(5).trim() || '{}');
  switch (eventType) {
    case 'status': updatePhase(data.phase, data.message); break;
    case 'tool': updateToolStatus(data); break;
    case 'token': appendAnswer(data.content); break;
    case 'refs': setReferences(data.refs); break;
    case 'clarify': showClarify(data); break;
    case 'error': showError(data); break;
    case 'done': finishTurn(data); break;
  }
}
```

### 4.3 中断

```typescript
// 用户点"停止"按钮
async function interrupt() {
  await fetch(`/api/agent/sessions/${sessionId}/interrupt`, {
    method:'POST',
    body: JSON.stringify({request_id})
  });
  reader.cancel();
}
```

## 五、服务端实现

### 5.1 Spring WebFlux SSE

```java
@PostMapping(value = "/sessions/{sessionId}/messages",
             produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Object>> sendMessage(
        @PathVariable String sessionId,
        @RequestBody MessageRequest req) {
    return agentService.processMessage(sessionId, req)
        .map(evt -> ServerSentEvent.builder(evt.getPayload())
            .event(evt.getType())
            .build());
}
```

### 5.2 背压与缓冲

- LLM 生成 token 速度快（50-100 token/s），前端消费速度足够，无需背压。
- 但若客户端网络慢，Spring 默认缓冲 8KB，超出会阻塞 LLM 流。
- 配置 `spring.webflux.response-buffer-size=32KB` 兜底。

### 5.3 超时

- 单次 SSE 连接最长 60 秒（超时主动发 error + done 并关闭）。
- LLM 首 token 超时 8 秒（发 error retryable=true）。

## 六、断线重连

### 6.1 Last-Event-ID

每个 event 带 `id` 字段（递增序号）：
```
id: 42
event: token
data: {...}
```

客户端断线重连时带 `Last-Event-ID: 42` 头，服务端从 43 继续。

### 6.2 实现

agent-service 维护 Redis `agent:sse:seq:{sessionId}:{turnId}` 自增序号，每个 event 发送前 INCR。

### 6.3 限制

- 重连窗口：30 秒内可续传，超出则返回 done（认为用户已离开）。
- 仅 token 事件可续传（其它事件已发生不可重现）。

## 七、决策记录 (ADR)

### ADR-090: SSE 而非 WebSocket
- **理由**：见 [internal-api-design.md](./internal-api-design.md) ADR-080。
- **补充**：SSE 自带重连机制（Last-Event-ID），WebSocket 需自己实现。

### ADR-091: 7 种事件类型
- **理由**：覆盖 Agent 全生命周期可观测点。少了用户感知不到进度，多了 token 浪费。
- **可扩展**：未来增加 `event: plan`（规划步骤可见）等。

### ADR-092: POST + ReadableStream 而非 GET EventSource
- **理由**：消息内容可能较长（用户粘贴长文本），GET 的 URL 长度受限。POST 无此问题。
- **代价**：不能用浏览器原生 EventSource，需手写 fetch 解析。但兼容性已足够（所有现代浏览器支持 ReadableStream）。

### ADR-093: 重连续传仅 token 事件
- **理由**：status/tool/refs 等事件是状态快照，重连后会重新发送当前状态，无需续传。token 是流式增量，必须续传避免丢字。
- **实现**：服务端缓存最近 N 条 token event（Redis List），重连时从 Last-Event-ID+1 回放。

### ADR-094: 单连接 60 秒上限
- **理由**：长连接占用服务端资源，60 秒覆盖绝大多数问答场景。超时主动关闭比僵死好。
- **例外**：复杂多轮反思可能超 60 秒，此时拆分为多次 SSE（前端无感，由 done 事件衔接）。

### ADR-095: 错误时保留 partial_answer
- **理由**：用户已等了几秒，返回空太差体验。partial_answer 让用户至少看到部分结果。
- **实现**：服务端维护已生成 token 的累积缓冲，error 时一并返回。
