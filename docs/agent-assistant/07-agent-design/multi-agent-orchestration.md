# 多 Agent 编排（优化期）

> 状态: 草稿  
> 最后更新: 2026-06-30

> 本章为优化期方案，MVP 不实现。

## 一、为何上多 Agent

- 单 Agent 在复杂多步任务上下文膨胀（检索结果 + 工具结果 + 历史）。
- 不同子任务需不同 system prompt（检索专家 vs 综合专家）。
- 多 Agent 可并行子任务。

## 二、角色定义

### 2.1 Planner（规划者）
- 输入：用户 query + 上下文。
- 输出：任务分解 + 分派。
- 不直接调工具，只规划。

### 2.2 Retrieval Specialist（检索专家）
- system prompt 专注检索策略。
- 可调用 search_posts / search_knowledge / locate_section。
- 输出：结构化检索结果。

### 2.3 Navigation Specialist（导航专家）
- 专注 NAVIGATE 类子任务。
- 调 locate_section / get_user_profile。
- 输出：跳转卡。

### 2.4 Synthesis Agent（综合者）
- 输入：各 Specialist 结果 + 用户 query。
- 输出：最终答案（流式）。
- 唯一能生成面向用户自然语言的 Agent。

## 三、编排模式

### 3.1 顺序编排
```
Planner → Retrieval → Synthesis
```
简单任务用。

### 3.2 并行编排
```
Planner → ┬─ Retrieval(帖子)
          ├─ Retrieval(知识库)
          └─ Navigation
        → Synthesis(等全部完成)
```
多源任务用。

### 3.3 反思编排
```
Synthesis → Reflector → (不通过) → Synthesis 重生成
```

## 四、Agent 间通信

- 通过结构化 message（JSON）传递，非自然语言。
- 每个 Agent 独立 LLM 调用，独立上下文。
- 共享黑板（Redis）：各 Agent 写入中间结果。

## 五、成本与延迟

- 多 Agent 调用次数 2-4 倍，成本相应增加。
- 并行编排延迟可控（取最慢 Agent）。
- 仅对复杂 query 启用，简单 query 走单 Agent。

## 六、决策记录 (ADR)

### ADR-039: 多 Agent 仅优化期
- **理由**：MVP 单 Agent 够用；多 Agent 复杂度高，待单 Agent 触瓶颈再上。
- **参考**：AutoGen / CrewAI 的多 Agent 模式（见 `16-technical-references`）。
