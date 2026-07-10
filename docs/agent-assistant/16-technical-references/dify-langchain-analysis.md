# Dify / LangChain 框架分析

> 状态: 草稿
> 最后更新: 2026-06-30
> 分析对象: Dify (开源 LLM 应用平台) + LangChain (LLM 应用框架)
> 参考: Dify GitHub、LangChain 文档、官方架构图

## 一、产品概述

### 1.1 Dify
- 开源 LLM 应用开发平台(BaaS)。
- 可视化编排 Agent/RAG/Workflow。
- 支持 API 调用和嵌入式部署。
- 定位:让非开发者也能搭建 LLM 应用。

### 1.2 LangChain
- LLM 应用开发框架(Python/JS SDK)。
- 提供组件抽象(Prompt/LLM/Tool/Memory/Retriever)。
- 支持 Agent 编排(AgentExecutor)。
- 定位:开发者的 LLM 应用「胶水层」。

## 二、Dify 架构拆解

### 2.1 整体架构

```
┌──────────────────────────────────────────────┐
│                  Dify 平台                     │
├──────────────────────────────────────────────┤
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ 可视化    │  │ API 层   │  │ 嵌入式    │   │
│  │ 编排 UI   │  │          │  │ Widget   │   │
│  └────┬─────┘  └────┬─────┘  └──────────┘   │
│       │              │                         │
│  ┌────▼──────────────▼─────────────────────┐  │
│  │            应用编排引擎                   │  │
│  │  ┌─────────┐ ┌─────────┐ ┌──────────┐  │  │
│  │  │ Chatflow│ │ Agent   │ │ Workflow │  │  │
│  │  └─────────┘ └─────────┘ └──────────┘  │  │
│  └───────────────────┬─────────────────────┘  │
│                      │                         │
│  ┌──────────┬────────┴────────┬──────────┐   │
│  │ RAG 引擎  │ LLM 抽象层      │ 工具管理  │   │
│  └──────────┘                 └──────────┘   │
│                      │                         │
│  ┌──────────┬────────┴────────┬──────────┐   │
│  │ 向量库    │ 关系数据库       │ 缓存     │   │
│  │(Weaviate)│(PostgreSQL)     │(Redis)   │   │
│  └──────────┘                 └──────────┘   │
│                                                │
└──────────────────────────────────────────────┘
```

### 2.2 RAG 引擎(核心借鉴点)

Dify 的 RAG 流水线:

```
文档上传 → 解析 → 分块 → Embedding → 向量库
                                            ↓
用户提问 → 查询改写 → [向量检索 + 关键词检索] → RRF 融合 → 重排 → LLM 生成
```

**特点**:
- **混合检索**:向量(语义)+ 关键词(精确),RRF 融合。
- **分块策略**:支持固定大小/按段落/按 markdown 标题/自定义。
- **多向量库**:Weaviate/Qdrant/Milvus/PG-Vector 可切换。
- **检索测试**:UI 中可输入 query 实时查看检索结果,调试方便。

### 2.3 Agent 模式

Dify 支持三种 Agent 模式:

| 模式 | 说明 | 适用 |
|------|------|------|
| Chatflow | 可视化对话流,固定节点 | 结构化对话(客服) |
| Agent | LLM 自主决策工具调用 | 开放域问答 |
| Workflow | 无对话的固定流程 | 批处理/ETL |

**Agent 模式的 ReAct 循环**:
```
Thought: 我需要搜索信息
Action: search_knowledge
Action Input: {"query": "考研数学"}
Observation: [检索结果]
Thought: 找到了,可以回答了
Final Answer: ...
```

### 2.4 工具管理

Dify 的工具抽象:

```yaml
# 工具定义 (YAML)
name: search_knowledge
description: 搜索知识库文章
parameters:
  - name: query
    type: string
    required: true
    description: 搜索关键词
```

- 工具可由「API 节点」「代码节点」「HTTP 请求」组成。
- 支持工具市场(社区共享工具)。

## 三、LangChain 架构拆解

### 3.1 核心抽象

```
┌──────────────────────────────────────────────┐
│                LangChain 抽象层                │
├──────────────────────────────────────────────┤
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Prompt   │  │ LLM      │  │ Tool     │   │
│  │ Template │  │ Wrapper  │  │ Definition│  │
│  └──────────┘  └──────────┘  └──────────┘   │
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Memory   │  │ Retriever│  │ Output   │   │
│  │ (历史)   │  │ (检索)   │  │ Parser   │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │         Agent Executor                    │ │
│  │  (编排: ReAct / OpenAI Functions / ...)  │ │
│  └──────────────────────────────────────────┘ │
│                                                │
└──────────────────────────────────────────────┘
```

### 3.2 Agent Executor(核心借鉴点)

LangChain 的 Agent 执行循环:

```python
# LangChain AgentExecutor 伪代码
class AgentExecutor:
    def run(self, input):
        iterations = 0
        while iterations < self.max_iterations:
            # 1. LLM 决定下一步
            action = self.llm.predict(
                prompt=self.build_prompt(input, history)
            )

            # 2. 判断是否完成
            if action.is_final_answer:
                return action.answer

            # 3. 执行工具
            observation = self.tools[action.tool_name].run(action.tool_input)

            # 4. 记录到历史
            history.append((action, observation))
            iterations += 1

        return "达到最大迭代次数"
```

**关键设计**:
- **最大迭代数**:防死循环(默认 15)。
- **历史记录**:每次工具调用结果加入 context。
- **多种 Agent 类型**:ReAct / OpenAI Functions / Structured Chat。

### 3.3 Memory 抽象

LangChain 的对话记忆:

| 类型 | 说明 |
|------|------|
| ConversationBufferMemory | 保留全部历史(简单但 token 爆炸) |
| ConversationBufferWindowMemory | 只保留最近 K 轮 |
| ConversationSummaryMemory | 旧消息摘要 |
| ConversationKGMemory | 知识图谱记忆(实体关系) |
| VectorStoreRetrieverMemory | 向量检索历史(按相关性) |

### 3.4 LCEL(LangChain Expression Language)

```python
# LCEL 链式调用
chain = prompt | llm | output_parser

# 等价于
# 1. prompt.format(input)
# 2. llm.predict(formatted_prompt)
# 3. output_parser.parse(llm_output)

# 支持流式
chain.stream(input)  # 逐 token 输出
```

## 四、可借鉴点

### 4.1 RAG 混合检索 + RRF(✅ 已借鉴)
→ 见 [06-retrieval/hybrid-retrieval.md](../06-retrieval/hybrid-retrieval.md)
- 向量检索 + 关键词检索,RRF 融合。
- 与 Dify 的 RAG 引擎设计一致。

### 4.2 ReAct Agent 循环(✅ 已借鉴)
→ 见 [07-agent-design/agent-architecture.md](../07-agent-design/agent-architecture.md)
- Thought → Action → Observation 循环。
- 最大迭代数限制(防死循环)。

### 4.3 工具抽象(✅ 已借鉴)
→ 见 [10-tools-and-apis/tool-specifications.md](../10-tools-and-apis/tool-specifications.md)
- JSON Schema 定义工具参数。
- 统一返回格式 `{summary, data, refs}`。

### 4.4 对话记忆策略(✅ 已借鉴)
→ 见 [09-context-engineering/conversation-memory.md](../09-context-engineering/conversation-memory.md)
- 借鉴 LangChain 的 Summary Memory(rolling summary)。
- 但不直接用 LangChain 的 Memory 类,自行实现(Redis 5 Key 结构更可控)。

### 4.5 检索调试 UI(进阶借鉴)
- Dify 的「检索测试」功能:输入 query 实时看检索结果。
- CampusShare 可在管理后台实现类似功能,便于调试检索质量。

### 4.6 LCEL 流式链(部分借鉴)
- LangChain 的 `chain.stream()` 思路优雅。
- 但 CampusShare 用 Java(Spring WebFlux),不用 LangChain SDK。
- 借鉴「链式流式」思想,用 Reactor 的 Flux 实现。

## 五、不适合借鉴点

### 5.1 直接使用 Dify 平台
- Dify 是完整平台,CampusShare 已有微服务架构。
- 引入 Dify = 架构冲突,且增加运维负担。
- 只借鉴设计,不引入平台。

### 5.2 直接使用 LangChain SDK
- LangChain 是 Python/JS,CampusShare 后端是 Java。
- LangChain4j 存在但生态不如 Python 版成熟。
- 自行实现等效抽象,与 Spring 生态更好集成。

### 5.3 可视化编排 UI
- Dify 的可视化编排适合非开发者。
- CampusShare 由开发团队维护,prompt/流程进 Git 更合适。
- 可视化编排增加复杂度但收益低。

### 5.4 ConversationKGMemory
- 知识图谱记忆需要实体抽取 + 图数据库。
- 对校园问答场景过重,rolling summary 足够。

## 六、自实现 vs 框架的权衡

| 维度 | 用 Dify/LangChain | 自实现 |
|------|-------------------|--------|
| 开发速度 | 快(开箱即用) | 慢 |
| 定制性 | 低(框架约束) | 高 |
| 性能 | 一般(通用抽象有开销) | 优(针对性优化) |
| 运维 | 低(框架维护) | 高(自己维护) |
| 技术栈匹配 | Python(不匹配 Java) | Java(匹配) |
| 学习成本 | 需学框架 | 需学底层原理 |

**决策**:自实现。理由:
1. Java 技术栈与 Python 框架不匹配。
2. Agent 质量取决于底层优化(检索/prompt/上下文),框架抽象反而阻碍优化。
3. 微服务架构需与 Spring 生态深度集成。
4. 学习成本:自实现需理解原理,但这正是用户要求的「驾驭工程」。

## 七、决策记录

### ADR-191: Dify/LangChain 借鉴设计但不引入框架
- **背景**:Dify/LangChain 是成熟的 LLM 应用框架,但直接引入有技术栈冲突。
- **决策**:
  - 借鉴:RAG 混合检索+RRF、ReAct Agent 循环、工具抽象、对话记忆策略、检索调试 UI。
  - 不引入:不直接使用 Dify 平台或 LangChain SDK。
  - 理由:Java 技术栈不匹配;框架抽象阻碍底层优化;微服务需 Spring 集成。
- **状态**:采纳
