# 思维链（CoT）与结构化推理

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、CoT 使用场景

- **生成答案**：复杂检索类问题，让 LLM 先理清思路再答。
- **反思**：分步推理检查。
- **规划**：复杂任务分步。
- **不用于**：意图分类（要快、要 JSON）、简单 HOW_TO（直接答）。

## 二、CoT 与 Function Calling 的结合

ReAct 本身就是 CoT 的变体（Thought → Action → Observation）。本 Agent 用原生 Function Calling，Thought 隐含在 tool_call 决策中，不强求 LLM 输出显式 Thought 文本（省 token）。

显式 CoT 用于：
- 反思时让 LLM 写出检查过程（「1. 检查引用：[1] 对应 post-123 ✓...」）。
- 复杂规划时写出步骤。

## 三、DeepSeek-R1 的推理链

- R1 自动输出 `<think>...</think>` 推理链。
- 在线主流程不用 R1（慢）。
- 离线反思用 R1，可读其推理链定位问题。

## 四、Self-Consistency（进阶）

- 对关键问题，让 LLM 生成多次答案，取多数一致。
- 代价高，仅优化期对高价值 query 用。

## 五、决策记录 (ADR)

### ADR-043: 不强求显式 Thought，用原生 tool_calls
- **理由**：省 token、解析稳定；DeepSeek-V3 的 tool_calls 已隐含推理。
- **反思例外**：反思需可解释，允许显式推理。
