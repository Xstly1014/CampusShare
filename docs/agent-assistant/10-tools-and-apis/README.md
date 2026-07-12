# 10 工具与 API 设计

> 状态: 草稿
> 最后更新: 2026-06-30

本目录规划 Agent 的工具体系（Function Calling）与 API 接口设计：工具如何注册、如何被 LLM 调用、跨服务如何取数、流式输出如何设计。

## 文档清单

| 文档 | 主题 |
|------|------|
| [tool-registry.md](./tool-registry.md) | 工具注册中心与动态发现 |
| [tool-specifications.md](./tool-specifications.md) | 各工具的详细规格（参数/返回/超时/降级） |
| [internal-api-design.md](./internal-api-design.md) | agent-service 对前端/网关的 API 设计 |
| [feign-clients.md](./feign-clients.md) | 跨服务 Feign 客户端（agent→user, agent→post） |
| [sse-streaming-api.md](./sse-streaming-api.md) | SSE 流式输出协议与事件定义 |
| [error-handling.md](./error-handling.md) | 工具调用错误处理、重试、降级 |

## 设计原则

1. **工具即能力边界**: Agent 能做什么完全由注册的工具决定；没有注册的工具 LLM 调不了。
2. **工具粒度**: 单一职责，一个工具做一件事。复杂操作由 Agent 编排多个工具。
3. **跨服务铁律**: 严格遵守 AGENT-WORKFLOW.md 第六章，工具不直连其他服务的 DB，只走 Feign。
4. **可观测**: 每次工具调用记录 tool_name/args/result/latency/success 到 agent_turns.tools_called。
5. **流式优先**: 长耗时工具（检索/生成）支持流式回传，避免用户等待空白。
