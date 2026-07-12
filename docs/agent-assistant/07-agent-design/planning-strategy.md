# 规划策略

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、规划层级

### 1.1 无规划（MVP 默认）
- 简单 query 直接 ReAct，LLM 自行决定调什么工具。
- 适用于 90% 单步场景（一次检索 + 生成）。

### 1.2 轻规划（进阶）
- 复杂 query 先让 LLM 输出 plan（步骤列表），再逐步执行。
- 触发条件：
  - query 含「并且/同时/对比/分别」（多任务）。
  - 上一轮工具结果不满足（需多轮检索）。
- plan 示例：
  ```json
  {"plan":["search_posts(清华,OS)","search_posts(北大,OS)","compare_and_answer"]}
  ```

### 1.3 深规划（优化期，多 Agent）
- Planner Agent 拆解任务，分派 Specialist，见 `agent-architecture.md` 第四节。

## 二、Plan-Execute-Reflect 模式（进阶）

```
Query → Plan(LLM) → Execute(按 plan 调工具) → Reflect(检查是否完成)
  ↑                                                    │
  └────────────── 未完成则 Replan ─────────────────────┘
  │
  ▼ 完成
Answer
```
- 反思发现信息不足时重新规划，而非硬走原 plan。

## 三、规划与 ReAct 的关系

- ReAct 是「边想边做」，每步 LLM 决策。
- Plan-Execute 是「先想好再做」， upfront 规划。
- 本 Agent 采用**混合**：
  - 简单 query：纯 ReAct。
  - 复杂 query：先轻 plan（1 次额外 LLM 调用），再 ReAct 执行。

## 四、规划成本

- 轻规划增加 1 次 LLM 调用（~500ms，~¥0.001）。
- 仅对复杂 query 触发，整体成本可控。

## 五、决策记录 (ADR)

### ADR-036: 规划条件触发
- **理由**：简单 query 规划是浪费；复杂 query 不规划会乱。
- **触发**：检测多任务信号词或上一轮工具结果不足。
