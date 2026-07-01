# AutoGen 多 Agent 分析

> 状态: 草稿
> 最后更新: 2026-06-30
> 分析对象: Microsoft AutoGen (多 Agent 对话框架)
> 参考: AutoGen GitHub、微软研究院论文、官方文档

## 一、产品概述

AutoGen 是微软研究院开源的多 Agent 对话框架,核心思想:**用多个角色化 Agent 协作完成任务,而非单个 Agent 独立完成**。

```
用户请求
    │
    ▼
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Planner  │ ←→  │ Coder    │ ←→  │ Critic   │
│ (规划)   │     │ (编码)   │     │ (审查)   │
└──────────┘     └──────────┘     └──────────┘
    │                                  │
    └────────── 协作完成 ──────────────┘
```

- 每个 Agent 有角色(system prompt)、工具集、记忆。
- Agent 间通过自然语言对话协作。
- 支持人类参与(human-in-the-loop)。

## 二、架构拆解

### 2.1 核心概念

**ConversableAgent**:
```python
class ConversableAgent:
    def __init__(self, name, system_message, llm_config, tools):
        self.name = name
        self.system_message = system_message
        self.llm = LLM(llm_config)
        self.tools = tools

    def generate_reply(self, messages):
        # 基于 system_message + history + tools 生成回复
        return self.llm.chat(self.system_message, messages)
```

**GroupChat**(多 Agent 群聊):
```python
class GroupChat:
    def __init__(self, agents, messages, max_round=10):
        self.agents = agents  # [Planner, Coder, Critic]
        self.messages = messages
        self.max_round = max_round

    def run(self, user_input):
        self.messages.append({"role": "user", "content": user_input})

        for round in range(self.max_round):
            # 选择下一个发言的 Agent
            next_agent = self.select_next_speaker()

            # 该 Agent 生成回复
            reply = next_agent.generate_reply(self.messages)
            self.messages.append({"role": next_agent.name, "content": reply})

            # 检查是否完成
            if "TERMINATE" in reply:
                break

        return self.messages
```

### 2.2 Agent 角色设计(核心借鉴点)

AutoGen 的典型角色配置:

| 角色 | 职责 | System Prompt 要点 |
|------|------|-------------------|
| User Proxy | 代理用户,执行代码/调用工具 | 「你是用户代理,执行请求并反馈结果」 |
| Assistant | 主对话 Agent | 「你是助手,回答问题或委派任务」 |
| Planner | 任务分解与规划 | 「将复杂任务分解为子任务」 |
| Coder | 代码编写 | 「编写清晰、可运行的代码」 |
| Critic | 审查与反馈 | 「审查其他 Agent 的输出,指出问题」 |
| Executor | 执行代码 | 「执行代码并返回结果/错误」 |

### 2.3 对话模式

**模式 1: 两 Agent 对话(N+1)**
```
User: "帮我分析这段代码的性能"
Assistant: "我来看看... [分析]"
User: "能优化吗?"
Assistant: "可以,优化方案是... [方案]"
```

**模式 2: 群聊(N+N)**
```
User: "开发一个 Web 爬虫"
Planner: "分解为: 1.设计架构 2.编写代码 3.测试"
Coder: "[编写爬虫代码]"
Critic: "代码有内存泄漏问题,建议修复"
Coder: "[修复后代码]"
Critic: "TERMINATE (通过)"
```

**模式 3: 嵌套对话(Sequential)**
```
Manager: "这个任务需要研究和编码"
  ├── Research Agent: "[研究结果]"
  └── Coder Agent: "[基于研究的代码]"
Manager: "综合结果: ..."
```

### 2.4 人类参与(Human-in-the-Loop)

```python
# User Proxy 可配置为需要人类确认
user_proxy = UserProxyAgent(
    name="user",
    human_input_mode="ALWAYS",  # ALWAYS / TERMINATE / NEVER
    # ALWAYS: 每次 Agent 回复后等人类输入
    # TERMINATE: 仅在任务完成前等人类确认
    # NEVER: 全自动
)
```

## 三、可借鉴点

### 3.1 角色化 Agent 设计(部分借鉴)
→ 见 [07-agent-design/multi-agent.md](../07-agent-design/multi-agent.md)
- CampusShare 进阶阶段考虑多 Agent:
  - **Router Agent**:意图分类与路由。
  - **Search Agent**:资源/帖子搜索专家。
  - **Help Agent**:使用教程专家。
  - **Critic Agent**:反思与校验。
- MVP 不采用(单 Agent + 工具调用足够),但架构预留。

### 3.2 Critic 审查模式(✅ 已借鉴)
→ 见 [07-agent-design/reflection-self-validation.md](../07-agent-design/reflection-self-validation.md)
- 反思阶段即「Critic Agent」角色。
- 用 R1 做反思,检查 V3 的回答。
- 与 AutoGen 的 Critic 设计一致。

### 3.3 任务分解(进阶借鉴)
- AutoGen 的 Planner 将复杂任务分解为子任务。
- CampusShare 进阶阶段,对复杂多步查询可引入 Planner Agent。
- MVP 用单 Agent + 多工具调用替代(ReAct 循环中 LLM 自行分解)。

### 3.4 最大轮次限制(✅ 已借鉴)
→ 见 [10-tools-and-apis/error-handling.md](../10-tools-and-apis/error-handling.md)
- AutoGen 的 `max_round` 防止无限对话。
- CampusShare 的工具调用上限(单轮 ≤3 次)和 LLM 调用上限(单会话 ≤5 次)同理念。

## 四、不适合借鉴点

### 4.1 多 Agent 群聊(MVP 不采用)
- 群聊模式增加 LLM 调用次数(每个 Agent 都调用 LLM)。
- 单次对话成本从 ¥0.004 涨到 ¥0.02-0.05(4-10 Agent)。
- 对校园问答场景过重,单 Agent + 工具足够。

### 4.2 代码执行能力
- AutoGen 的 Executor 可执行代码,适合开发场景。
- CampusShare 是资源分享平台,无需代码执行。
- 且代码执行有安全风险(沙箱逃逸)。

### 4.3 完全自动化(NEVER 模式)
- 全自动多 Agent 可能产生意外行为(幻觉级联)。
- CampusShare 保留人工审核环节(安全事件)。

### 4.4 嵌套对话
- 嵌套增加复杂度和延迟。
- 对简单问答场景,线性流程更合适。

## 五、单 Agent vs 多 Agent 的决策

| 维度 | 单 Agent + 工具 | 多 Agent 协作 |
|------|-----------------|---------------|
| 成本 | 低(1 次 LLM/轮) | 高(N 次 LLM/轮) |
| 延迟 | 低(串行调用) | 高(多轮对话) |
| 质量 | 中等(单视角) | 高(多视角审查) |
| 复杂度 | 低 | 高(编排/状态/通信) |
| 适用 | 明确意图、单步任务 | 开放域、复杂多步任务 |

**CampusShare 的选择**:
- **MVP**:单 Agent + 工具调用(ReAct)。90% 场景足够。
- **进阶**:引入 Critic Agent(反思),但不做完整群聊。
- **远期**:复杂查询(如「帮我规划考研复习计划」)考虑 Planner + 多专家 Agent。

## 六、AutoGen 对 CampusShare 的核心启发

AutoGen 的核心思想:**Agent 不必全能,可以是专家**。

这对 CampusShare 的启示:
- 不要试图让一个 prompt 解决所有问题。
- 按意图路由到不同的「专家 prompt」(见 04-intent-understanding)。
- 反思阶段用「Critic 视角」而非「自我重复」。

## 七、决策记录

### ADR-192: AutoGen 借鉴 - Critic 反思模式, MVP 不做多 Agent
- **背景**:AutoGen 的多 Agent 协作强大,但成本和复杂度高。
- **决策**:
  - 借鉴:Critic 审查模式(反思阶段)、角色化设计思想、最大轮次限制。
  - MVP 不采用:多 Agent 群聊、代码执行、嵌套对话。
  - 进阶:引入 Critic Agent(反思),远期考虑 Planner + 专家 Agent。
- **理由**:校园问答场景单 Agent + 工具足够;多 Agent 成本高 5-10 倍,且编排复杂。
- **状态**:采纳
