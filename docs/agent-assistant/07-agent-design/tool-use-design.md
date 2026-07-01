# 工具调用设计

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、工具调用总流程

```
LLM 输出 tool_calls: [{name, arguments}]
  │
  ▼
ToolRegistry.dispatch(name, arguments)
  │
  ├─ 参数校验(JSON Schema)
  ├─ 权限校验(只读工具，禁止写)
  ├─ 超时控制(单工具 ≤ 5s)
  ├─ 执行工具 → 返回 observation
  └─ 记录 trace(agent_tool_calls 表)
  │
  ▼
observation 追加到上下文(tool role)
```

## 二、工具返回格式

统一返回结构化 observation：
```json
{
  "status": "success|error|empty",
  "summary": "找到 5 条相关帖子",
  "data": [...],
  "refs": [{"ref_id":"post:uuid","type":"post","title":"...","jump_url":"/school/1/post/123"}]
}
```
- `summary` 给 LLM 看（简洁）。
- `data` 给 LLM 看（详细，用于生成）。
- `refs` 给前端渲染引用卡（LLM 不一定需要）。

## 三、工具调用并发

- 单轮内 LLM 可一次输出多个 tool_calls → 并发执行（CompletableFuture）。
- 例：用户「找清华 OS 资料 + 看看怎么发帖」→ 并发调 search_posts + search_knowledge。

## 四、工具失败处理

- 工具抛异常 → observation 返回 `{status:"error", summary:"检索暂时不可用"}`。
- LLM 收到 error 后应：尝试别的工具 or 用已有信息作答 or 告知用户。
- 不让工具异常导致 Agent 整体崩溃。

## 五、工具调用可见性

- 工具调用过程通过 SSE `tool` 事件推给前端：
  ```
  event: tool
  data: {"tool":"search_posts","args":{"query":"操作系统"},"status":"running"}
  ...
  event: tool
  data: {"tool":"search_posts","status":"done","summary":"找到5条"}
  ```
- 前端可折叠展示「已检索帖子(5条)」，增强可解释性。

## 六、工具调用成本控制

- 单次问答工具调用次数 ≤ 5。
- 同一工具同参数在单次问答内不重复调用（缓存）。
- 超限强制 finalize。

## 七、决策记录 (ADR)

### ADR-034: 工具返回 summary + data + refs 三段
- **理由**：summary 控制 LLM 上下文膨胀；data 提供细节；refs 供前端引用卡。
- **实现**：工具实现者按此结构返回。

### ADR-035: 多 tool_calls 并发执行
- **理由**：并发省延迟；LLM 一次输出多 tool_calls 是 Function Calling 原生能力。
- **风险**：并发工具间有依赖时需顺序 → LLM 应分轮调用，依赖工具不在同轮输出。
