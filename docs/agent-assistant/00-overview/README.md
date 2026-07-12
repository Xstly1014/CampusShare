# CampusShare 智能问答助手 - 规划文档总索引

> 本目录是 CampusShare 平台「智能问答助手(Agent Assistant)」模块的**完整规划文档树**。
> 本阶段只做技术路线预研与搭建规划，**不包含任何业务代码**。
> 所有文档遵循从底层到成型的递进结构，便于后续按图索骥地实现。

---

## 一、这个 Agent 是什么

CampusShare 智能问答助手是平台第 5 个一级导航模块（位于「首页」与「仓库」之间），定位为**平台的统一智能入口**：

- **软件使用教程 / 帮助中心**：用户问「怎么成为创作者」「怎么关闭通知」「怎么隐藏私信」等，返回精准操作指引并可直接跳转。
- **资源 / 讨论帖语义检索**：用户用自然语言描述需求（如「求清华操作系统期末复习资料」「想讨论 2026 秋招面经」），Agent 定位到合适板块并返回高匹配度帖子。
- **平台功能导航**：用户不知道功能在哪，Agent 帮他找到入口。
- **多轮上下文对话**：支持追问、澄清、指代消解，而非一问一答的孤儿问答。

**明确不做**：普通的「多轮对话 + 一个 RAG 知识库」的廉价 Agent。本 Agent 目标是具备**意图分流、混合检索、工具调用、规划反思、上下文工程、引用溯源、安全护栏**的工程化智能体。

---

## 二、文档树导航

| 目录 | 主题 | 核心问题 |
|------|------|----------|
| [00-overview](./00-overview) | 总览与愿景 | 做什么、为什么、成功标准 |
| [01-requirements](./01-requirements) | 需求分析 | 用户场景、功能/非功能需求、KPI |
| [02-architecture](./02-architecture) | 系统架构 | 整体架构、与现有微服务集成、数据流 |
| [03-llm-strategy](./03-llm-strategy) | LLM 选型与策略 | 用哪个模型、API、成本、降级 |
| [04-intent-understanding](./04-intent-understanding) | 意图理解与路由 | 意图分类、查询改写、路由分发 |
| [05-knowledge-base](./05-knowledge-base) | 知识库构建 | 知识来源、分块、Embedding、向量库 |
| [06-retrieval](./06-retrieval) | 高级检索 | 混合检索、重排、查询扩展、评估 |
| [07-agent-design](./07-agent-design) | Agent 核心设计 | 单/多 Agent、工具调用、规划反思 |
| [08-prompt-engineering](./08-prompt-engineering) | 提示词工程 | 系统提示、Few-shot、CoT、结构化输出 |
| [09-context-engineering](./09-context-engineering) | 上下文工程 | 窗口管理、压缩、短期/长期记忆 |
| [10-tools-and-apis](./10-tools-and-apis) | 工具与 API | 工具目录、JSON Schema、调用契约 |
| [11-frontend-integration](./11-frontend-integration) | 前端集成与 UI | 聊天 UI、NavBar 集成、流式渲染 |
| [12-backend-microservice](./12-backend-microservice) | 后端微服务 | campushare-agent 服务设计 |
| [13-evaluation](./13-evaluation) | 评估与质量 | 评估框架、测试集、指标、A/B |
| [14-safety-and-guardrails](./14-safety-and-guardrails) | 安全与护栏 | 防幻觉、范围限制、内容审核 |
| [15-observability](./15-observability) | 可观测性 | 日志、追踪、指标、Grafana 面板 |
| [16-technical-references](./16-technical-references) | 技术参考 | Claude/字节/DeepSeek 等开源方案 |
| [17-roadmap](./17-roadmap) | 实施路线图 | MVP/进阶/优化三阶段 |
| [18-glossary.md](./18-glossary.md) | 术语表 | 统一术语定义 |

---

## 三、阅读顺序建议

**第一次阅读（建立全局认知，约 30 分钟）**：
1. `00-overview/vision-and-goals.md` —— 愿景与成功标准
2. `01-requirements/user-scenarios.md` —— 具体用户故事
3. `02-architecture/system-architecture.md` —— 整体架构图
4. `17-roadmap/phases.md` —— 三阶段路线图

**深入实现某模块前（按需精读）**：
- 要做检索 → `06-retrieval/` 全部 + `05-knowledge-base/`
- 要做 Agent 编排 → `07-agent-design/` + `10-tools-and-apis/`
- 要做前端 → `11-frontend-integration/`
- 要做后端服务 → `12-backend-microservice/`
- 要做评估 → `13-evaluation/`

---

## 四、文档约定

1. **语言**：中文为主，技术术语保留英文原词（如 ReAct、RAG、Re-ranking）。
2. **版本**：每个文档头部标注 `状态: 草稿/评审中/定稿` 与 `最后更新`。
3. **交叉引用**：用相对路径链接，不写绝对路径。
4. **决策记录**：关键技术选型在文档末尾用 `## 决策记录(ADR)` 记录选项与理由。
5. **与 AGENT-WORKFLOW.md 的关系**：本目录是 Agent 模块的专属规划；通用开发规范仍以根目录 `AGENT-WORKFLOW.md` 为准。当 Agent 模块进入实现阶段后，需在 `AGENT-WORKFLOW.md` 的「微服务划分」中追加 `campushare-agent` 并更新端口映射表。
