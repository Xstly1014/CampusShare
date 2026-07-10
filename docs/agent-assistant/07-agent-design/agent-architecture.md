# Agent 架构（单 Agent ReAct 为主，多 Agent 进阶）

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、架构演进路径

```
MVP：单 Agent + ReAct + 工具调用
  │
  ▼
进阶：单 Agent + ReAct + 反思自校验
  │
  ▼
优化：多 Agent 编排（Planner + 检索/知识/导航 Specialist）
```

## 二、MVP：单 Agent ReAct

### 2.1 ReAct 循环
```
User Query + Context
  │
  ▼
LLM(系统提示 + 工具描述 + 历史) 
  → 输出: Thought + Action(tool_call)
  │
  ▼
执行 tool → Observation
  │
  ▼
LLM(追加 Observation) → Thought + Action or Final Answer
  │ (循环直到 Final Answer 或达步数上限)
  ▼
Final Answer (流式输出给用户)
```

### 2.2 实现方式
- 用 DeepSeek-V3 的原生 Function Calling（`tools` + `tool_choice="auto"`）。
- 不手写 Thought/Action 文本解析，用结构化 `tool_calls`。
- 每轮把 `tool` 返回的 `observation` 作为 `tool` role message 追加到上下文。

### 2.3 步数限制
- 最大 5 步（防止无限循环与成本失控）。
- 达上限强制 LLM 用已有信息生成 Final Answer。

### 2.4 工具集（MVP）
见 `10-tools-and-apis`：
- `search_knowledge`：知识库检索
- `search_posts`：帖子混合检索
- `locate_section`：板块/分类定位
- `get_user_profile`：取当前用户公开统计
- `finalize`：结束并生成答案

## 三、进阶：反思自校验

在 Final Answer 生成后，加一个**反思步骤**（离线或低优先级）：
```
Final Answer + 检索结果 + Query
  │
  ▼
LLM(R1 或 V3) 反思:
  - 答案是否 grounded（每条事实有引用）？
  - 是否回答了用户问题？
  - 是否超范围？
  - 是否有幻觉？
  │
  ├─ 通过 → 输出
  └─ 不通过 → 修正后输出 / 触发澄清
```
- 在线用 V3 快速反思（增加 1 次调用，+500ms）。
- 离线用 R1 深度反思（批量，不阻塞用户）。

## 四、优化：多 Agent 编排

### 4.1 角色分工
```
Planner Agent（规划）
  ├─ Retrieval Agent（检索专家：调用 search_posts/search_knowledge）
  ├─ Navigation Agent（导航专家：调用 locate_section）
  └─ Synthesis Agent（综合：生成最终答案）
```

### 4.2 工作流
- Planner 分析 query，决定调用哪些 Specialist、顺序。
- Specialist 各自完成子任务，返回结果。
- Synthesis 综合所有结果生成答案。

### 4.3 何时上多 Agent
- 单 Agent 在复杂多步任务（如「找清华 OS 资料 + 找北大 OS 资料 + 对比」）上下文爆炸时。
- MVP 先单 Agent，验证后再拆。

## 五、Agent 状态机

```
IDLE → UNDERSTANDING(意图+改写) → PLANNING(可选) → EXECUTING(ReAct循环)
  → REFLECTING(反思,可选) → ANSWERING(流式生成) → DONE
  │
  └─ 任何状态可 → CLARIFYING(向用户提问) → 等用户回复 → UNDERSTANDING
```

## 六、决策记录 (ADR)

### ADR-031: MVP 用单 Agent + 原生 Function Calling
- **理由**：原生 tool_calls 比手写 ReAct 文本解析稳定；单 Agent 够覆盖当前场景。
- **备选**：LangChain/LangGraph 编排 → 引入重框架依赖，MVP 不必。

### ADR-032: 反思分在线(V3) + 离线(R1)
- **理由**：在线反思保实时性，离线深度反思保质量。
- **风险**：在线反思增加延迟 → 仅对低 confidence 答案触发。

### ADR-033: 多 Agent 放优化期
- **理由**：多 Agent 编排复杂、成本高；单 Agent 在 90% 场景够用。
- **触发**：单 Agent 上下文超限或步数频繁触顶时拆分。
