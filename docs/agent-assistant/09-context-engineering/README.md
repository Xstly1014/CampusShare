# 09 上下文工程

> 状态: 草稿
> 最后更新: 2026-06-30

本目录规划 Agent 的上下文工程：如何把"正确的信息"以"正确的形态"在"正确的时机"放进 LLM 的上下文窗口，并保证多轮对话的连续性、个性化与可控成本。

## 文档清单

| 文档 | 主题 | 核心问题 |
|------|------|---------|
| [context-window-management.md](./context-window-management.md) | 上下文窗口与 Token 预算 | 每一轮请求里 Token 怎么分配？超预算怎么办？ |
| [context-compression.md](./context-compression.md) | 上下文压缩与摘要 | 长对话如何不爆窗口且不丢关键信息？ |
| [conversation-memory.md](./conversation-memory.md) | 短期对话记忆（Redis） | 当前会话的轮次/槽位/引用如何存取？ |
| [long-term-memory.md](./long-term-memory.md) | 长期用户画像记忆（MySQL） | 跨会话的用户偏好/历史如何沉淀与召回？ |
| [state-management.md](./state-management.md) | 会话状态机与字段定义 | 会话有哪些状态？状态如何流转？ |

## 与其它目录的关系

- 与 [04-intent-understanding/multi-turn-context.md](../04-intent-understanding/multi-turn-context.md) 互补：那篇讲"指代消解/意图修正"的业务逻辑，本目录讲其底层存储与 Token 形态。
- 与 [07-agent-design/agent-architecture.md](../07-agent-design/agent-architecture.md) 互补：Agent 状态机在 07 设计，本目录聚焦状态字段在 Redis/MySQL 中的具体结构与生命周期。
- 与 [08-prompt-engineering/system-prompts.md](../08-prompt-engineering/system-prompts.md) 互补：08 定义 Prompt 文本，本目录定义如何把记忆/检索结果"注入"到 Prompt 占位符。

## 核心原则

1. **上下文 ≠ 历史**: 上下文是当前轮次拼给 LLM 的全部输入（含 system、记忆、检索、工具结果、用户输入），不是简单的聊天记录回放。
2. **分层装载**: System Prompt 永驻；用户画像按相关性装载；检索结果按 RRF 分装载；工具结果按引用装载。
3. **Token 是硬约束**: DeepSeek-V3 上下文 128K，但成本与延迟随 token 线性增长，单轮目标 ≤8K input。
4. **可观测**: 每次组装的上下文快照写入 `agent_context_snapshots` 表，便于复盘"为什么答错了"。
