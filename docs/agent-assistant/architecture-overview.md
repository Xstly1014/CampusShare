# Agent 模块架构总览导航

> **本文档的目的**：用图说话，一张图看懂 Agent 模块的全貌——现在有什么、将来加什么、各种技术概念之间是什么关系。
> 如果你对 RAG / MCP / Tool / Function Calling / 上下文工程 / 提示词工程 / 记忆系统等概念感到模糊，从这里开始。

- **创建日期：** 2026-07-01
- **当前阶段：** MVP Week 2（RAG 已接入，会话分类已完成，Markdown 渲染已上线）

---

## 0. 一句话定位

CampusShare Agent 是平台的**第 5 个一级导航模块**，定位为统一智能入口，覆盖三大场景：

| 场景 | 意图标签 | 示例 |
|------|----------|------|
| 软件使用教程 / 帮助中心 | `HOW_TO` | "怎么发帖？""怎么认证创作者？" |
| 资源 / 讨论帖语义检索 | `SEARCH` | "有没有关于考研复试的帖子？" |
| 平台功能导航 | `NAVIGATE` | "去我的收藏夹" |
| 多轮澄清对话 | `CLARIFY` | "你说的'资源'是指帖子还是知识库？" |
| 超范围拒答 | `OUT_OF_SCOPE` | "帮我写作业" |

---

## 1. 全景架构图（最重要的一张图）

> 这张图展示了 Agent 模块的**所有组件及其关系**。绿色 = 已实现，黄色 = Schema 就绪但无代码，灰色 = 规划中。

```mermaid
flowchart TB
    User([用户])

    subgraph Frontend["前端层"]
        AgentPage["AgentPage.tsx<br/>聊天 UI + 流式渲染"]
        AgentService["agent.ts<br/>SSE 客户端 + 会话 API"]
    end

    subgraph Gateway["网关层"]
        GW["gateway-service<br/>JWT 认证 + 路由 /api/agent/**"]
    end

    subgraph AgentService["agent-service (端口 8083, WebFlux)"]

        subgraph ControllerLayer["控制器层"]
            AC["AgentController<br/>/chat + /sessions + /categories CRUD"]
            IAC["InternalAgentController<br/>/internal/agent/* 内部 API"]
        end

        subgraph ServiceLayer["服务层"]
            ChatService["AgentChatService<br/>上下文构建 + 流式编排 + RAG 注入"]
            SessionService["AgentSessionService<br/>会话生命周期"]
            CategoryService["AgentSessionCategoryService<br/>会话分类 CRUD"]
            RateLimiter["AgentRateLimiter<br/>Redis 限流 10次/分"]
            RetrievalService["RetrievalService<br/>向量+关键词 RRF 融合"]
            KnowledgeService["KnowledgeIngestionService<br/>知识库分块 + 向量化"]
            PostVectorService["PostVectorService<br/>帖子向量同步"]
        end

        subgraph CoreEngine["核心引擎（规划中）"]
            IntentClassifier["意图分类器<br/>5 意图 + JSON 输出"]
            QueryRewriter["查询改写器<br/>同义词 + 指代消解"]
            Orchestrator["Agent 编排器<br/>ReAct 循环"]
            Reflector["反思校验器<br/>在线 V3 + 离线 R1"]
        end

        subgraph RetrievalLayer["检索层（RAG）"]
            HybridRetrieval["混合检索服务<br/>三路并行 + RRF 融合"]
            Reranker["重排序器<br/>bge-reranker-v2-m3"]
            EmbeddingClient["Embedding 客户端<br/>BGE-M3 → 1024维向量"]
        end

        subgraph ToolLayer["工具层"]
            ToolRegistry["工具注册表<br/>10 个工具"]
            ToolExecutor["工具执行器<br/>Function Calling"]
            FeignClients["Feign 客户端<br/>PostFeign / UserFeign"]
        end

        subgraph ContextLayer["上下文层"]
            ContextBuilder["上下文组装器<br/>L0-L5 分层装载"]
            ContextCompressor["上下文压缩器<br/>Rolling Summary"]
            TokenCounter["Token 计数器<br/>jtokkit"]
        end

        subgraph MemoryLayer["记忆层"]
            ShortTermMemory["短期记忆<br/>Redis: 会话轮次/槽位"]
            LongTermMemory["长期记忆<br/>MySQL: 用户画像/偏好"]
        end

        subgraph LLMLayer["LLM 层"]
            DeepSeekClient["DeepSeek 客户端<br/>流式 + 非流式"]
            PromptManager["提示词管理器<br/>L1-L4 四层分层"]
        end

        subgraph ResilienceLayer["弹性层"]
            CircuitBreaker["熔断器<br/>失败率 50% → 30s"]
            RetryHandler["重试器<br/>3 次指数退避"]
        end

        subgraph SafetyLayer["安全层（规划中）"]
            InputGuard["输入护栏<br/>注入检测 + PII 脱敏"]
            OutputGuard["输出护栏<br/>敏感词 + 引用校验"]
        end
    end

    subgraph DataLayer["数据层"]
        MySQL[("MySQL<br/>agent_sessions / agent_turns<br/>user_memory / knowledge_articles")]
        PostgreSQL[("PostgreSQL + pgvector<br/>post_vectors / knowledge_vectors<br/>HNSW 索引")]
        Redis[("Redis<br/>限流 + 短期记忆<br/>SSE 重连缓存")]
    end

    subgraph ExternalServices["外部服务"]
        DeepSeekAPI["DeepSeek API<br/>V3 (生成) + R1 (反思)"]
        BGEService["BGE 服务<br/>TEI 镜像, 端口 8084"]
        UserService["user-service:8081"]
        PostService["post-service:8082"]
    end

    User -->|提问| AgentPage
    AgentPage -->|SSE fetch| AgentService
    AgentService -->|HTTP| GW
    GW -->|转发 + JWT| AC

    AC --> ChatService
    AC --> SessionService
    AC --> CategoryService
    AC --> RateLimiter
    IAC --> KnowledgeService
    IAC --> PostVectorService

    ChatService --> RetrievalService
    ChatService --> CoreEngine
    CoreEngine --> ToolLayer
    ChatService --> ContextLayer
    ChatService --> MemoryLayer
    ChatService --> LLMLayer
    ChatService --> ResilienceLayer
    CoreEngine --> SafetyLayer

    RetrievalService --> EmbeddingClient
    ToolLayer --> FeignClients
    FeignClients --> UserService
    FeignClients --> PostService

    LLMLayer --> DeepSeekClient
    DeepSeekClient --> DeepSeekAPI
    EmbeddingClient --> BGEService

    KnowledgeService --> PostgreSQL
    PostVectorService --> PostgreSQL
    AgentService --> MySQL
    AgentService --> PostgreSQL
    AgentService --> Redis

    %% 已实现标记
    style AgentPage fill:#d4edda,stroke:#28a745
    style AgentService fill:#d4edda,stroke:#28a745
    style GW fill:#d4edda,stroke:#28a745
    style AC fill:#d4edda,stroke:#28a745
    style IAC fill:#d4edda,stroke:#28a745
    style ChatService fill:#d4edda,stroke:#28a745
    style SessionService fill:#d4edda,stroke:#28a745
    style CategoryService fill:#d4edda,stroke:#28a745
    style RateLimiter fill:#d4edda,stroke:#28a745
    style RetrievalService fill:#d4edda,stroke:#28a745
    style KnowledgeService fill:#d4edda,stroke:#28a745
    style PostVectorService fill:#d4edda,stroke:#28a745
    style DeepSeekClient fill:#d4edda,stroke:#28a745
    style CircuitBreaker fill:#d4edda,stroke:#28a745
    style RetryHandler fill:#d4edda,stroke:#28a745
    style TokenCounter fill:#d4edda,stroke:#28a745
    style MySQL fill:#d4edda,stroke:#28a745
    style PostgreSQL fill:#d4edda,stroke:#28a745
    style Redis fill:#d4edda,stroke:#28a745
    style DeepSeekAPI fill:#d4edda,stroke:#28a745
    style BGEService fill:#d4edda,stroke:#28a745
    style HybridRetrieval fill:#d4edda,stroke:#28a745
    style EmbeddingClient fill:#d4edda,stroke:#28a745
    style PromptManager fill:#d4edda,stroke:#28a745

    %% 规划中
    style IntentClassifier fill:#f8f9fa,stroke:#6c757d
    style QueryRewriter fill:#f8f9fa,stroke:#6c757d
    style Orchestrator fill:#f8f9fa,stroke:#6c757d
    style Reflector fill:#f8f9fa,stroke:#6c757d
    style Reranker fill:#f8f9fa,stroke:#6c757d
    style ToolRegistry fill:#f8f9fa,stroke:#6c757d
    style ToolExecutor fill:#f8f9fa,stroke:#6c757d
    style FeignClients fill:#f8f9fa,stroke:#6c757d
    style ContextBuilder fill:#f8f9fa,stroke:#6c757d
    style ContextCompressor fill:#f8f9fa,stroke:#6c757d
    style ShortTermMemory fill:#f8f9fa,stroke:#6c757d
    style LongTermMemory fill:#f8f9fa,stroke:#6c757d
    style InputGuard fill:#f8f9fa,stroke:#6c757d
    style OutputGuard fill:#f8f9fa,stroke:#6c757d
    style UserService fill:#d4edda,stroke:#28a745
    style PostService fill:#d4edda,stroke:#28a745
```

**图例：**
- 🟢 绿色 = 已实现可用
- 🟡 黄色 = Schema/配置就绪，无 Java 代码
- ⬜ 灰色 = 规划中，尚未开始

---

## 2. 核心概念关系图（厘清你提到的所有术语）

> 这张图回答最核心的问题：RAG、MCP、Tool、Function Calling、Skill、上下文工程、提示词工程、记忆系统——这些东西到底是什么关系？

```mermaid
flowchart LR
    subgraph LLM能力["LLM 原生能力"]
        FC["Function Calling<br/>LLM 原生能力<br/>模型知道何时该调工具"]
        Gen["文本生成<br/>流式输出回答"]
        Reason["推理<br/>Chain-of-Thought"]
    end

    subgraph Agent框架["Agent 框架（自研）"]
        ReAct["ReAct 循环<br/>Reason + Act 交替"]
        Plan["规划器<br/>分解复杂任务"]
        Reflect["反思器<br/>校验输出质量"]
        Router["意图路由<br/>分流到不同处理路径"]
    end

    subgraph 工具生态["工具生态"]
        Tool["Tool / 工具<br/>具体的能力实现<br/>如 search_posts"]
        Skill["Skill / 技能<br/>工具的组合封装<br/>如'搜索+总结'"]
        MCP["MCP 协议<br/>Model Context Protocol<br/>工具调用的标准化协议<br/>（远期评估）"]
        ToolRegistry2["Tool Registry<br/>工具注册表<br/>管理工具元数据"]
    end

    subgraph 知识检索["知识检索 (RAG)"]
        RAG["RAG<br/>检索增强生成"]
        Embed["Embedding<br/>文本→向量"]
        VectorDB["向量数据库<br/>PG-Vector"]
        Hybrid["混合检索<br/>向量+关键词+结构化"]
        Rerank["Reranker<br/>重排序"]
        RRF["RRF 融合<br/>多路结果合并"]
    end

    subgraph 上下文管理["上下文管理"]
        CE["上下文工程<br/>决定给 LLM 看什么"]
        PE["提示词工程<br/>决定怎么对 LLM 说"]
        Context["上下文窗口<br/>L0-L5 分层装载"]
        Compress["上下文压缩<br/>滑动窗口+摘要"]
        Memory["记忆系统<br/>短期+长期"]
    end

    %% 关系连线
    FC -->|驱动| Tool
    Tool -->|注册到| ToolRegistry2
    ToolRegistry2 -->|被调用| ReAct
    Skill -->|组合多个| Tool
    MCP -.->|标准化协议, 远期| Tool

    ReAct -->|使用| FC
    ReAct -->|使用| RAG
    Router -->|分流到| ReAct

    RAG -->|流程| Embed
    Embed -->|存入| VectorDB
    VectorDB -->|检索| Hybrid
    Hybrid -->|融合| RRF
    RRF -->|精排| Rerank
    Rerank -->|结果注入| Context

    CE -->|管理| Context
    CE -->|管理| Compress
    CE -->|管理| Memory
    PE -->|设计提示词| Context
    Context -->|输入给| LLM能力

    Memory -->|短期: Redis| Context
    Memory -->|长期: MySQL| Context

    %% 样式
    style FC fill:#e1f5fe,stroke:#0288d1
    style RAG fill:#f3e5f5,stroke:#7b1fa2
    style CE fill:#e8f5e9,stroke:#388e3c
    style PE fill:#e8f5e9,stroke:#388e3c
    style MCP fill:#fff3e0,stroke:#f57c00,stroke-dasharray: 5 5
    style Skill fill:#fce4ec,stroke:#c62828
```

### 概念逐个解释

| 概念 | 一句话解释 | 在本项目中的落地方式 | 当前状态 |
|------|-----------|---------------------|----------|
| **Function Calling** | LLM 原生能力：模型判断"该调工具了"并生成结构化调用参数 | DeepSeek-V3 原生 `tool_calls` 字段，不手写文本解析 | 未实现 |
| **Tool / 工具** | Agent 可调用的具体能力，如搜索帖子、查询知识库 | 10 个工具：search_posts / get_post_detail / search_knowledge 等 | 未实现 |
| **Skill / 技能** | 多个 Tool 的组合封装，实现更复杂的高层能力 | MVP 不引入，远期可考虑（如"搜索+总结+引用"封装为 Skill） | 未规划 |
| **MCP** | Anthropic 提出的工具调用标准化协议，让工具可跨 Agent 复用 | 远期评估，MVP 不引入。当前用自研 ToolRegistry + Function Calling | 未规划 |
| **RAG** | 检索增强生成：先检索相关知识，再让 LLM 基于检索结果回答 | 两路并行检索（知识库向量+关键词 + 帖子向量）→ RRF 融合 → 注入上下文 | ✅ 已实现 |
| **Embedding** | 把文本转成向量，让计算机能算"语义相似度" | BGE-M3 模型，1024 维，SiliconFlow 云端 API | ✅ 已实现 |
| **向量数据库** | 专门存储和检索向量的数据库 | PG-Vector (PostgreSQL + pgvector 插件)，HNSW 索引 | ✅ 已实现 |
| **混合检索** | 多种检索方式并行，取长补短 | 向量(语义) + pg_trgm(关键词) 两路并行 + 帖子向量检索 | ✅ 已实现 |
| **RRF** | Reciprocal Rank Fusion，多路检索结果的融合算法 | `score(d) = Σ 1/(k + rank_i(d))`，k=60 | ✅ 已实现 |
| **Reranker** | 对检索结果做二次精排，提升 Top-K 质量 | bge-reranker-v2-m3，进阶阶段引入 | 未实现 |
| **上下文工程** | 决定给 LLM "看什么"——在有限的 token 预算内放最有价值的信息 | L0-L5 六层分层装载，token 预算分配 | 未实现 |
| **提示词工程** | 决定对 LLM "怎么说"——设计 System Prompt、Few-shot、输出格式 | L1-L4 四层分层，版本管理，意图分类+改写合并调用 | 部分（PromptConstants 已有基础版） |
| **记忆系统** | 让 Agent 跨轮次/跨会话记住用户信息 | 短期(Redis: 会话轮次/槽位) + 长期(MySQL: 用户画像/偏好) | 未实现 |
| **上下文压缩** | 当历史太长时，压缩旧内容避免超出 token 限制 | Rolling Summary + Slot Freezing + Pin Message 三合一 | 未实现 |
| **ReAct** | Reason + Act 循环：LLM 推理→调工具→看结果→再推理 | 单 Agent ReAct，最大 5 步，步数耗尽强制生成答案 | 未实现 |
| **意图路由** | 先理解用户想干什么，再分流到不同处理路径 | 5 意图分类：HOW_TO / SEARCH / NAVIGATE / CLARIFY / OUT_OF_SCOPE | 未实现 |

---

## 3. 问答完整流程序列图

> 用户问"有没有关于考研复试的帖子？"，Agent 内部发生了什么？

```mermaid
sequenceDiagram
    actor U as 用户
    participant FE as 前端 AgentPage
    participant GW as Gateway
    participant AC as AgentController
    participant Chat as AgentChatService
    participant Intent as 意图分类器
    participant Rewrite as 查询改写器
    participant RAG as 混合检索
    participant Agent as ReAct 编排器
    participant LLM as DeepSeek API
    participant DB as MySQL
    participant Redis as Redis

    U->>FE: "有没有关于考研复试的帖子？"
    FE->>GW: POST /api/agent/chat (SSE)
    GW->>GW: JWT 认证 → 提取 userId
    GW->>AC: 转发请求

    AC->>Redis: 限流检查 (10次/分)
    Redis-->>AC: 通过

    AC->>Chat: chat(userId, message)

    Note over Chat: 1. 准备上下文
    Chat->>DB: 获取/创建会话
    Chat->>DB: 加载最近 10 轮历史
    Chat->>DB: 创建 Turn 记录 (status=STREAMING)

    Note over Chat: 2. 意图分类 (规划中)
    Chat->>Intent: classify(query)
    Intent->>LLM: [意图分类 Prompt, temperature=0, JSON 输出]
    LLM-->>Intent: {"intent":"SEARCH","slots":{"topic":"考研复试"}}
    Intent-->>Chat: SEARCH 意图

    Note over Chat: 3. 查询改写 (规划中)
    Chat->>Rewrite: rewrite(query, history)
    Rewrite->>LLM: [改写 Prompt, 补充指代/同义词]
    LLM-->>Rewrite: "考研复试 经验 帖子 讨论"
    Rewrite-->>Chat: 改写后的 query

    Note over Chat: 4. RAG 检索 (规划中)
    Chat->>RAG: hybrid_search(query, slots)

    par 三路并行检索
        RAG->>RAG: 向量检索 (PG-Vector HNSW, top-20)
    and
        RAG->>RAG: 关键词检索 (MySQL FULLTEXT, top-20)
    and
        RAG->>RAG: 结构化检索 (SQL slots, top-20)
    end

    RAG->>RAG: RRF 融合 → top-10
    RAG->>RAG: Reranker 精排 → top-5 (进阶)
    RAG-->>Chat: 检索结果 [post1, post2, ...]

    Note over Chat: 5. ReAct 循环 (规划中)
    Chat->>Agent: orchestrate(query, context, tools)

    loop ReAct 循环 (最大 5 步)
        Agent->>LLM: [System + 检索结果 + 工具列表 + 用户问题]
        LLM-->>Agent: {thought, action: search_posts, args: {...}}

        alt 需要调工具
            Agent->>RAG: execute_tool(search_posts, args)
            RAG-->>Agent: {summary, data, refs}
            Agent->>Agent: 将工具结果加入上下文
        else 已有足够信息
            Agent->>LLM: 生成最终回答 (stream=true)
            LLM-->>Agent: 流式 token 输出
        end
    end

    Note over Chat: 6. SSE 流式输出
    loop 逐 token 推送
        Agent-->>Chat: delta token
        Chat-->>AC: SSE event: delta
        AC-->>GW: SSE event: delta
        GW-->>FE: SSE event: delta
        FE-->>U: 逐字渲染
    end

    Chat->>Chat: 反思校验 (可选, 规划中)
    Chat-->>AC: SSE event: refs (引用列表)
    AC-->>FE: SSE event: refs
    Chat-->>AC: SSE event: done
    AC-->>FE: SSE event: done [DONE]

    Note over Chat: 7. 持久化
    Chat->>DB: 更新 Turn (status=COMPLETED, assistantMessage, tokensUsed)
    Chat->>DB: 更新 Session (messageCount, totalTokens, lastMessageAt)
```

> **注意：** 上图展示的是**完整目标流程**。当前已实现步骤 1、4（RAG 检索）、6、7——意图分类、查询改写、ReAct 循环尚未实现。当前流程是：用户消息 → RAG 检索（知识库向量+关键词 + 帖子向量）→ 检索结果注入上下文 → DeepSeek 流式生成 → 持久化。

---

## 4. RAG 检索流程详解

> RAG 是 Agent 最核心的"知识"来源。下图展示了一条用户查询如何被检索处理。

```mermaid
flowchart TD
    Q["用户查询<br/>'考研复试经验帖子'"]

    Q --> Embed["Embedding 客户端<br/>BGE-M3 → 1024维向量"]
    Q --> Fulltext["关键词检索<br/>MySQL FULLTEXT (BM25 近似)"]
    Q --> Struct["结构化检索<br/>SQL WHERE (slots 过滤)"]

    subgraph 向量检索路["向量检索路 (语义匹配)"]
        Embed --> PG["PG-Vector HNSW<br/>余弦相似度<br/>ORDER BY embedding <=> query_vec"]
        PG --> VF["结构化过滤下推<br/>WHERE category='讨论' AND school_id=X"]
        VF --> VTop["Top-20 结果"]
    end

    subgraph 关键词检索路["关键词检索路 (精确匹配)"]
        Fulltext --> MySQL2["MySQL MATCH AGAINST<br/>BM25 打分"]
        MySQL2 --> FTop["Top-20 结果"]
    end

    subgraph 结构化检索路["结构化检索路 (精确过滤)"]
        Struct --> SQL["SQL 查询<br/>按 slots (category/school/type)<br/>计数加权排序"]
        SQL --> STop["Top-20 结果"]
    end

    VTop --> RRF
    FTop --> RRF
    STop --> RRF

    RRF["RRF 融合<br/>score(d) = Σ 1/(60 + rank_i(d))<br/>无需调权重，对分数量纲不敏感"]
    RRF --> Top10["Top-10 融合结果"]

    Top10 --> RerankCheck{有 Reranker?}
    RerankCheck -->|是, 进阶阶段| Reranker["bge-reranker-v2-m3<br/>交叉编码精排"]
    Reranker --> Top5["Top-5 精排结果"]
    RerankCheck -->|否, MVP 阶段| Top5Direct["直接用 Top-10"]

    Top5 --> Inject["注入上下文<br/>作为 L4 层检索结果"]
    Top5Direct --> Inject

    Inject --> LLM["送给 LLM 生成回答"]

    style RRF fill:#e1f5fe,stroke:#0288d1,stroke-width:2px
    style Embed fill:#f3e5f5,stroke:#7b1fa2
    style PG fill:#f3e5f5,stroke:#7b1fa2
```

### RAG 各组件说明

| 组件 | 作用 | 技术选型 | 部署方式 | 状态 |
|------|------|----------|----------|------|
| Embedding 模型 | 文本 → 1024维向量 | BGE-M3 (智源/BAAI), SiliconFlow API | SiliconFlow 云端 API | ✅ 已接入 |
| 向量数据库 | 存储和检索向量 | PG-Vector (PostgreSQL 16 + pgvector) | agent-postgres 容器 (端口 5432) | ✅ 已接入 |
| 向量索引 | 加速近似最近邻搜索 | HNSW (m=16, ef_construction=64, ef_search=40) | PG-Vector 内建 | ✅ 已使用 |
| 关键词检索 | 精确匹配关键词 | pg_trgm + GIN 索引 (MVP) → Elasticsearch (远期) | PostgreSQL 容器 | ✅ 已使用 (pg_trgm) |
| 结构化检索 | 按分类/学校/类型精确过滤 | SQL WHERE + 计数加权 | PostgreSQL 容器 | 未实现 |
| RRF 融合 | 多路结果合并 | `score = Σ 1/(60 + rank)` | agent-service 内 | ✅ 已使用 |
| Reranker | 二次精排 | bge-reranker-v2-m3 | bge-service 容器 | 未实现 (进阶) |

---

## 5. Agent ReAct 循环详解

> ReAct = Reason + Act。LLM 先"想"（推理），再"做"（调工具），看结果后再"想"，循环直到能回答。

```mermaid
flowchart TD
    Start["用户问题 + 上下文"] --> Init["初始化 ReAct<br/>step=0, max_steps=5"]

    Init --> Loop{step < max_steps?}

    Loop -->|是| Think["LLM 推理<br/>输入: System + 检索结果<br/>+ 工具列表 + 历史动作<br/>+ 用户问题"]
    Think --> Decision{LLM 输出类型?}

    Decision -->|tool_calls| Act["执行工具<br/>Function Calling"]
    Act --> ToolSelect{选择工具}
    ToolSelect -->|search_posts| SearchPosts["搜索帖子<br/>HybridRetrievalService"]
    ToolSelect -->|get_post_detail| GetPost["获取帖子详情<br/>PostFeignClient"]
    ToolSelect -->|search_knowledge| SearchKB["搜索知识库<br/>向量检索"]
    ToolSelect -->|list_categories| ListCat["列出分类<br/>Redis 缓存"]
    ToolSelect -->|其他 6 个工具| Other["..."]

    SearchPosts --> ToolResult["工具返回<br/>{summary, data, refs}"]
    GetPost --> ToolResult
    SearchKB --> ToolResult
    ListCat --> ToolResult
    Other --> ToolResult

    ToolResult --> UpdateCtx["将工具结果加入上下文"]
    UpdateCtx --> StepInc["step += 1"]
    StepInc --> Loop

    Decision -->|final_answer| Generate["生成最终回答<br/>流式输出"]
    Generate --> SSE["SSE 推送 token"]

    Loop -->|否, 步数耗尽| ForceGen["强制用已有信息<br/>生成最终回答"]
    ForceGen --> Generate

    SSE --> Reflect{需要反思?<br/>confidence < 阈值?}
    Reflect -->|是, 在线| OnlineReflect["V3 快速反思<br/>+500ms<br/>检查引用/幻觉"]
    OnlineReflect --> ReflectResult{反思通过?}
    ReflectResult -->|否| Regenerate["重新生成<br/>(最多 1 次)"]
    Regenerate --> SSE
    ReflectResult -->|是| Done["完成"]
    Reflect -->|否| Done

    OnlineReflect --> OfflineQueue["(异步) 离线 R1 深度反思<br/>写入评估日志"]

    style Think fill:#e8f5e9,stroke:#388e3c
    style Act fill:#e3f2fd,stroke:#1976d2
    style Generate fill:#fff3e0,stroke:#f57c00
    style ForceGen fill:#fce4ec,stroke:#c62828
    style OnlineReflect fill:#f3e5f5,stroke:#7b1fa2
```

### ReAct 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| max_steps | 5 | 最大推理步数，防止无限循环 |
| tools per turn | 3 | 单轮最多调用 3 次工具 |
| 反思触发条件 | confidence < 阈值 | 仅低置信度触发在线反思 |
| 在线反思延迟 | +500ms | 用 V3 快速反思 |
| 离线反思 | 异步 | 用 R1 深度反思，写入评估日志 |

---

## 6. 上下文工程分层架构

> 上下文工程回答一个问题：**在有限的 token 预算内，给 LLM 看什么最有价值？**

```mermaid
flowchart TB
    subgraph Token预算["Token 预算: 单轮 ≤ 8K input"]
        L0["L0: System Prompt<br/>平台级固定前缀<br/>预算: 1000 tokens<br/>★ 命中 DeepSeek 缓存, 价格 1/10"]
        L1["L1: 用户画像<br/>长期记忆摘要<br/>预算: 300 tokens<br/>★ Top-K=5, 周衰减 0.1"]
        L2["L2: 会话上下文<br/>槽位 + 最近轮次<br/>预算: 500 tokens<br/>★ 短期记忆 Redis"]
        L3["L3: 检索结果<br/>RAG 召回的帖子/知识<br/>预算: 3000 tokens<br/>★ 最大块, 含引用编号"]
        L4["L4: 工具结果<br/>ReAct 工具返回<br/>预算: 2500 tokens<br/>★ 动态, 按步累积"]
        L5["L5: 用户当前问题<br/>+ 改写后的 query<br/>预算: 700 tokens<br/>★ 最后注入"]
    end

    L0 --> Assemble["上下文组装器"]
    L1 --> Assemble
    L2 --> Assemble
    L3 --> Assemble
    L4 --> Assemble
    L5 --> Assemble

    Assemble --> Count["Token 计数 (jtokkit)"]
    Count --> Check{总 token ≤ 预算?}
    Check -->|是| Send["发送给 LLM"]
    Check -->|否| Compress["上下文压缩"]

    Compress --> Strategy{压缩策略选择}
    Strategy -->|历史过长| Rolling["Rolling Summary<br/>旧轮次摘要为 1-2 句"]
    Strategy -->|槽位冗余| Freeze["Slot Freezing<br/>冻结已确认的槽位"]
    Strategy -->|重要信息| Pin["Pin Message<br/>钉住关键消息不压缩"]

    Rolling --> Recount["重新计数"]
    Freeze --> Recount
    Pin --> Recount
    Recount --> Check

    Assemble --> Snapshot["写入 agent_context_snapshots<br/>便于调试回放"]

    style L0 fill:#e3f2fd,stroke:#1976d2
    style L3 fill:#e8f5e9,stroke:#388e3c
    style L5 fill:#fff3e0,stroke:#f57c00
    style Compress fill:#fce4ec,stroke:#c62828
```

### 上下文工程 vs 提示词工程 vs 记忆系统

```mermaid
flowchart LR
    subgraph 三者关系["三者关系"]
        direction TB
        PE["提示词工程<br/>━━━━━━━━━━<br/>决定'怎么说'<br/><br/>• System Prompt 设计<br/>• Few-shot 示例<br/>• 输出格式约束<br/>• 温度/采样参数<br/><br/>关注点: 指令质量"]
        CE["上下文工程<br/>━━━━━━━━━━<br/>决定'看什么'<br/><br/>• 分层装载 L0-L5<br/>• Token 预算分配<br/>• 压缩策略<br/>• 检索结果注入<br/><br/>关注点: 信息价值"]
        ME["记忆系统<br/>━━━━━━━━━━<br/>决定'记住什么'<br/><br/>• 短期: 会话轮次/槽位<br/>• 长期: 用户画像/偏好<br/>• 记忆衰减/冲突解决<br/>• 用户可查看/删除<br/><br/>关注点: 跨轮次/跨会话"]
    end

    PE -->|设计的内容| Context["最终上下文"]
    CE -->|组装的信息| Context
    ME -->|提供的记忆| Context
    Context -->|输入给| LLM["LLM"]

    style PE fill:#e8f5e9,stroke:#388e3c
    style CE fill:#e3f2fd,stroke:#1976d2
    style ME fill:#f3e5f5,stroke:#7b1fa2
```

---

## 7. 记忆系统架构

> 记忆系统让 Agent "记住"用户，分短期和长期两层。

```mermaid
flowchart TB
    subgraph 短期记忆["短期记忆 (Redis)"]
        ST1["会话轮次<br/>key: session:{id}:turns<br/>最近 N 轮的 user/assistant 消息"]
        ST2["槽位信息<br/>key: session:{id}:slots<br/>已确认的 school/category/topic"]
        ST3["引用缓存<br/>key: session:{id}:refs<br/>当前轮次的引用列表"]
        ST4["SSE 重连缓存<br/>key: session:{id}:sse_cache<br/>最近 N 条 token event, 30s 过期"]
    end

    subgraph 长期记忆["长期记忆 (MySQL: user_memory 表)"]
        LT1["用户画像<br/>• 学校/专业/年级<br/>• 兴趣分类<br/>• 活跃时间段"]
        LT2["偏好设置<br/>• 偏好回答风格<br/>• 常问话题<br/>• 拒绝过的内容"]
        LT3["行为证据<br/>user_memory_evidence 表<br/>• 点赞/收藏/搜索行为<br/>• 推断记忆的依据"]
    end

    subgraph 记忆生命周期["记忆生命周期"]
        Collect["收集证据<br/>Feign 拉取用户行为<br/>点赞/收藏/搜索"]
        Infer["推断记忆<br/>LLM 从行为推断偏好<br/>置信度评分"]
        Store["存储记忆<br/>写入 user_memory<br/>带置信度+时间戳"]
        Decay["衰减机制<br/>周衰减系数 0.1<br/>低置信度记忆自动淘汰"]
        Conflict["冲突解决<br/>新记忆 vs 旧记忆矛盾<br/>标记冲突, 取高置信度"]
        Forget["遗忘机制<br/>用户可查看/删除记忆<br/>软删除 + 回收站"]
    end

    Collect --> Infer --> Store --> Decay
    Store --> Conflict
    Decay --> Forget

    ST1 -->|会话结束时| LT1
    ST2 -->|槽位确认后| LT1

    LT1 -->|注入上下文 L1 层| Context["给 LLM 的上下文"]
    LT2 -->|注入上下文 L1 层| Context

    style 短期记忆 fill:#e3f2fd,stroke:#1976d2
    style 长期记忆 fill:#f3e5f5,stroke:#7b1fa2
    style Collect fill:#e8f5e9,stroke:#388e3c
```

---

## 8. SSE 事件流详解

> 前端如何消费 Agent 的流式输出？7 种事件类型分别是什么？

```mermaid
sequenceDiagram
    participant FE as 前端
    participant GW as Gateway
    participant AC as AgentController
    participant Chat as AgentChatService

    FE->>GW: POST /api/agent/chat (SSE)
    GW->>AC: 转发

    AC->>Chat: chat(userId, request)

    Chat-->>AC: event: session\ndata: {"sessionId":"xxx"}
    AC-->>GW: SSE: session
    GW-->>FE: SSE: session

    Note over Chat: 意图分类 + 检索中...
    Chat-->>AC: event: status\ndata: {"stage":"retrieving"}
    AC-->>FE: SSE: status

    Note over Chat: ReAct 调用工具
    Chat-->>AC: event: tool\ndata: {"name":"search_posts","status":"start"}
    AC-->>FE: SSE: tool (start)
    Chat-->>AC: event: tool\ndata: {"name":"search_posts","status":"end","summary":"找到3篇"}
    AC-->>FE: SSE: tool (end)

    Note over Chat: LLM 流式生成
    loop 逐 token 推送
        Chat-->>AC: event: delta\ndata: {"content":"考"}
        AC-->>FE: SSE: delta
        FE->>FE: 逐字渲染
    end

    Chat-->>AC: event: refs\ndata: [{"n":1,"type":"post","id":"xxx","title":"..."}]
    AC-->>FE: SSE: refs
    FE->>FE: 渲染引用角标 [1] [2]

    Chat-->>AC: event: done\ndata: [DONE]
    AC-->>FE: SSE: done
    FE->>FE: 关闭流, 标记完成

    Note over FE,Chat: 如果出错
    Chat-->>AC: event: error\ndata: {"message":"...","partial_answer":"已生成的部分..."}
    AC-->>FE: SSE: error
```

### SSE 事件类型速查

| 事件类型 | data 内容 | 时机 | 当前状态 |
|----------|-----------|------|----------|
| `session` | `{sessionId}` | 会话创建/确认 | ✅ 已实现 |
| `status` | `{stage: "retrieving"}` | 阶段状态更新 | 未实现 |
| `tool` | `{name, status, summary}` | 工具调用开始/结束 | 未实现 |
| `delta` | 纯文本 token | LLM 逐 token 输出 | ✅ 已实现 |
| `refs` | `[{n, type, id, title}]` | 引用列表 | 未实现 |
| `clarify` | `{message, options}` | 需要用户澄清 | 未实现 |
| `error` | `{message}` | 错误 | ✅ 已实现 |
| `done` | `[DONE]` | 流结束 | ✅ 已实现 |

---

## 9. 数据流图：数据从哪来，到哪去

> 展示 Agent 模块涉及的**所有数据源、数据流向和数据格式**。

```mermaid
flowchart LR
    subgraph 数据来源["数据来源"]
        PostSvc["post-service<br/>帖子/评论/点赞数据"]
        UserSvc["user-service<br/>用户信息/行为数据"]
        Knowledge["知识库文档<br/>FAQ/使用教程<br/>手动编写"]
    end

    subgraph 数据处理["数据处理管道"]
        Chunk["分块<br/>Recursive Character Splitter<br/>512 token/块"]
        Embed2["向量化<br/>BGE-M3 → 1024维"]
        Index["建索引<br/>HNSW + FULLTEXT + trgm"]
    end

    subgraph 向量存储["向量存储 (PostgreSQL)"]
        PV["post_vectors<br/>帖子向量 + 结构化字段<br/>category/school/type"]
        KV["knowledge_vectors<br/>知识库向量 + topic"]
    end

    subgraph 业务存储["业务存储 (MySQL)"]
        Sess["agent_sessions<br/>会话主表"]
        Turn["agent_turns<br/>轮次记录"]
        KA["knowledge_articles<br/>知识库原文"]
        UM["user_memory<br/>长期记忆"]
        UME["user_memory_evidence<br/>行为证据"]
        ToolReg["agent_tool_registry<br/>工具注册表"]
        Snapshot["agent_context_snapshots<br/>上下文快照"]
    end

    subgraph 缓存["缓存 (Redis)"]
        RateLimit["限流计数器<br/>10次/分/用户"]
        ShortMem["短期记忆<br/>会话轮次/槽位"]
        SSECache["SSE 重连缓存<br/>30s 过期"]
        ToolCache["工具结果缓存<br/>list_categories 1h<br/>list_schools 30min"]
    end

    PostSvc -->|Feign 拉取/推送通知| Chunk
    Knowledge -->|手动录入| Chunk
    Chunk --> Embed2
    Embed2 --> Index
    Index --> PV
    Index --> KV

    PostSvc -->|Feign| ToolReg
    UserSvc -->|Feign| ToolReg

    UserSvc -->|Feign 定时拉取| UME
    UME -->|LLM 推断| UM

    subgraph 运行时["运行时数据流"]
        Query["用户查询"]
        Query -->|检索| PV
        Query -->|检索| KV
        PV -->|结果| Context2["上下文组装"]
        KV -->|结果| Context2
        Context2 --> Snapshot
        Context2 -->|生成| LLMOut["LLM 输出"]
        LLMOut -->|写入| Turn
        LLMOut -->|更新| Sess
        LLMOut -->|缓存| ShortMem
    end

    Query -->|限流| RateLimit
    Query -->|读缓存| ToolCache

    style PV fill:#f3e5f5,stroke:#7b1fa2
    style KV fill:#f3e5f5,stroke:#7b1fa2
    style Embed2 fill:#f3e5f5,stroke:#7b1fa2
    style UM fill:#e8f5e9,stroke:#388e3c
    style RateLimit fill:#e3f2fd,stroke:#1976d2
```

---

## 10. 技术栈全景表

> 所有技术选型一览，含状态和演进方向。

### 10.1 LLM 与模型

| 角色 | 技术选型 | 版本/型号 | 用途 | 状态 | 演进方向 |
|------|----------|-----------|------|------|----------|
| 主生成模型 | DeepSeek-V3 | deepseek-v4-flash | 意图/改写/ReAct/答案生成 | ✅ 已接入 | — |
| 推理反思模型 | DeepSeek-R1 | deepseek-reasoner | 离线深度反思 | 未实现 | 进阶阶段 |
| 兜底模型 | 豆包 doubao-pro | — | 主模型连续失败时切换 | 未实现 | 进阶阶段 |
| Embedding 模型 | BGE-M3 | BAAI/bge-m3 | 文本→1024维向量 | ✅ 已接入 (SiliconFlow) | 远期可本地 TEI |
| Reranker 模型 | bge-reranker-v2-m3 | — | 检索结果精排 | 未实现 | 进阶阶段 |

### 10.2 数据存储

| 存储 | 技术 | 用途 | 端口 | 状态 |
|------|------|------|------|------|
| MySQL | 8.0 | 业务表（会话/轮次/记忆/知识库/工具注册/会话分类） | 3306 | ✅ 已接入 |
| PostgreSQL | 16 + pgvector | 向量存储（帖子向量/知识向量） | 5432 | ✅ 已接入 |
| Redis | 7 | 限流/短期记忆/SSE缓存/工具缓存 | 6379 | ✅ 已接入 |

### 10.3 框架与中间件

| 类别 | 技术 | 用途 | 状态 | 演进方向 |
|------|------|------|------|----------|
| Web 框架 | Spring WebFlux | 响应式 + SSE 流式 | ✅ 已使用 | — |
| ORM | MyBatis Plus | MySQL 数据访问 | ✅ 已使用 | — |
| 跨服务调用 | OpenFeign | 调用 user/post-service | 配置就绪 | → Nacos 服务发现 |
| 熔断器 | Resilience4j | LLM 调用熔断 | ✅ 已使用 | → Sentinel (架构升级) |
| 重试 | Spring Retry | LLM 调用重试 | ✅ 已使用 | — |
| Token 计数 | jtokkit | 本地 token 估算 | ✅ 已使用 | — |
| 监控 | Micrometer + Prometheus | 指标采集 | ✅ 已使用 | — |
| 链路追踪 | OpenTelemetry | 分布式追踪 | ✅ 已使用 | → Tempo |
| 限流 | Redis INCR | 用户级限流 | ✅ 已使用 | — |
| 向量检索 | pgvector 0.1.6 | PostgreSQL 向量操作 + HNSW 索引 | ✅ 已使用 | → Milvus (远期) |

### 10.4 前端

| 技术 | 用途 | 状态 |
|------|------|------|
| React + TypeScript | 聊天 UI | ✅ 已使用 |
| fetch + ReadableStream | SSE 客户端 | ✅ 已使用 |
| Zustand | 状态管理（规划） | 未实现 |
| Markdown 渲染 (react-markdown) | AI 回复格式化 | ✅ 已使用 |
| SwipeToDelete 组件 | 左滑删除/移动会话 | ✅ 已使用 |
| 会话分类管理 | 文件夹分组管理会话 | ✅ 已使用 |
| 引用角标 | [1] [2] 可点击引用 | 未实现 |

---

## 11. 演进路线图

> MVP → 进阶 → 远期，每个阶段做什么。

```mermaid
flowchart LR
    subgraph MVP["MVP 阶段 (4-6 周)"]
        M1["Week 1<br/>后端骨架 + DB<br/>✅ 已完成"]
        M2["Week 2<br/>RAG + 会话分类<br/>Markdown 渲染<br/>✅ 已完成"]
        M3["Week 3<br/>检索 + 意图<br/>Agent ReAct 核心"]
        M4["Week 4<br/>SSE 流式<br/>前端联调"]
        M5["Week 5<br/>安全护栏<br/>监控看板"]
        M6["Week 6<br/>测试 + 上线"]

        M1 --> M2 --> M3 --> M4 --> M5 --> M6
    end

    subgraph 进阶["进阶阶段 (4-6 周)"]
        A1["Reranker<br/>bge-reranker-v2-m3"]
        A2["反思自校验<br/>R1 离线反思"]
        A3["上下文压缩<br/>Rolling Summary"]
        A4["长期记忆<br/>用户画像/偏好"]
        A5["评估体系<br/>黄金集 + LLM-Judge"]
        A6["A/B 框架"]
    end

    subgraph 远期["远期阶段 (持续)"]
        F1["多 Agent 编排<br/>Planner + Specialist<br/>+ Critic"]
        F2["Milvus 迁移<br/>帖子量 >50万时"]
        F3["MCP 协议评估<br/>工具标准化"]
        F4["Fine-tune 评估"]
        F5["多模态<br/>图片理解"]
        F6["知识图谱<br/>Neo4j"]
    end

    MVP -->|验收: P95≤10s, 带引用| 进阶
    进阶 -->|验收: P95≤8s, 采纳率≥60%| 远期

    style M1 fill:#d4edda,stroke:#28a745
    style M2 fill:#d4edda,stroke:#28a745
    style M3 fill:#f8f9fa,stroke:#6c757d
    style M4 fill:#f8f9fa,stroke:#6c757d
    style M5 fill:#f8f9fa,stroke:#6c757d
    style M6 fill:#f8f9fa,stroke:#6c757d
```

### 各阶段技术栈增量

| 阶段 | 新增技术 | 移除/替换 | 验收标准 |
|------|----------|-----------|----------|
| **MVP** | BGE-M3, PG-Vector, RAG, ReAct, Function Calling, SSE 7 事件, 输入/输出护栏, Prometheus | — | P95 ≤ 10s, 带引用, 覆盖 3 意图 |
| **进阶** | Reranker, R1 反思, 上下文压缩, 长期记忆, 评估体系, A/B, LLM 语义安全, 5 个 Grafana 看板 | — | TTFB P95 ≤ 2.5s, E2E P95 ≤ 8s, 采纳率 ≥ 60% |
| **远期** | 多 Agent 编排, Milvus, MCP 协议, Fine-tune, 多模态, Neo4j 知识图谱 | PG-Vector → Milvus, Resilience4j → Sentinel, MySQL FULLTEXT → ES | 日活 >1000 时 2+ 实例 |

---

## 12. 部署拓扑图

```mermaid
flowchart TB
    subgraph Docker网络["Docker Compose 网络"]
        subgraph 前端容器["前端容器"]
            Frontend["frontend<br/>Nginx :80"]
        end

        subgraph 网关容器["网关容器"]
            Gateway["gateway-service<br/>:8080"]
        end

        subgraph 后端服务["后端服务"]
            UserService["user-service<br/>:8081"]
            PostService["post-service<br/>:8082"]
            AgentService["agent-service<br/>:8083<br/>WebFlux, 1536m"]
            BGEService["BGE Embedding<br/>SiliconFlow 云端 (当前)<br/>→ TEI 本地 (远期)"]
        end

        subgraph 数据基础设施["数据基础设施"]
            MySQL3[("MySQL<br/>:3306<br/>共享实例")]
            PostgreSQL3[("agent-postgres<br/>:5432<br/>pgvector/pg16")]
            Redis3[("Redis<br/>:6379")]
        end

        subgraph 监控["监控基础设施"]
            Prom["Prometheus<br/>:9090"]
            Grafana["Grafana<br/>:3000"]
            Tempo["Tempo<br/>:3200 (链路追踪)"]
        end
    end

    Internet["外部网络"]
    DeepSeekCloud["DeepSeek API<br/>api.deepseek.com"]

    Internet -->|用户访问| Frontend
    Frontend -->|/api/**| Gateway
    Gateway -->|/api/agent/**| AgentService
    Gateway -->|/api/auth/**, /api/users/**| UserService
    Gateway -->|/api/posts/**, /api/files/**| PostService

    AgentService -->|Feign| UserService
    AgentService -->|Feign| PostService
    AgentService -->|WebClient| DeepSeekCloud
    AgentService -->|HTTP| BGEService

    AgentService --> MySQL3
    AgentService --> PostgreSQL3
    AgentService --> Redis3

    AgentService -.->|Metrics| Prom
    AgentService -.->|Traces| Tempo
    Prom --> Grafana
    Tempo --> Grafana

    style AgentService fill:#d4edda,stroke:#28a745,stroke-width:2px
    style BGEService fill:#d4edda,stroke:#28a745
    style PostgreSQL3 fill:#d4edda,stroke:#28a745
```

### 容器资源分配

| 容器 | 内存限制 | CPU | start_period | 特殊配置 |
|------|----------|-----|-------------|----------|
| agent-service | 1536m | — | 90s | DNS: 8.8.8.8, 114.114.114.114 |
| agent-postgres | — | — | 10s | pgvector/pg16 镜像 |
| BGE Embedding | 云端 (SiliconFlow) | — | — | 当前用云端 API，远期可本地部署 TEI |
| mysql | — | — | 30s | 共享实例 |
| redis | — | — | 10s | — |

---

## 13. 已有文档导航表

> `docs/agent-assistant/` 下有 93 份详细设计文档，这里是快速导航。

| 你想了解... | 去看这个文档 |
|------------|-------------|
| Agent 的整体愿景和目标 | [00-overview/vision-and-goals.md](./00-overview/vision-and-goals.md) |
| 做什么、不做什么 | [00-overview/scope-and-non-goals.md](./00-overview/scope-and-non-goals.md) |
| 系统架构图 | [02-architecture/system-architecture.md](./02-architecture/system-architecture.md) |
| 数据流设计 | [02-architecture/data-flow.md](./02-architecture/data-flow.md) |
| LLM 选型决策 | [03-llm-strategy/model-selection.md](./03-llm-strategy/model-selection.md) |
| 意图分类体系 | [04-intent-understanding/intent-taxonomy.md](./04-intent-understanding/intent-taxonomy.md) |
| 查询改写策略 | [04-intent-understanding/query-rewriting.md](./04-intent-understanding/query-rewriting.md) |
| 知识库来源 | [05-knowledge-base/knowledge-sources.md](./05-knowledge-base/knowledge-sources.md) |
| 分块策略 | [05-knowledge-base/chunking-strategy.md](./05-knowledge-base/chunking-strategy.md) |
| Embedding 选型 | [05-knowledge-base/embedding-strategy.md](./05-knowledge-base/embedding-strategy.md) |
| 向量库选型 | [05-knowledge-base/vector-database.md](./05-knowledge-base/vector-database.md) |
| 混合检索 + RRF | [06-retrieval/hybrid-retrieval.md](./06-retrieval/hybrid-retrieval.md) |
| 重排序 | [06-retrieval/re-ranking.md](./06-retrieval/re-ranking.md) |
| Agent 架构 (ReAct) | [07-agent-design/agent-architecture.md](./07-agent-design/agent-architecture.md) |
| 工具使用设计 | [07-agent-design/tool-use-design.md](./07-agent-design/tool-use-design.md) |
| 规划策略 | [07-agent-design/planning-strategy.md](./07-agent-design/planning-strategy.md) |
| 反思与校验 | [07-agent-design/reflection-and-verification.md](./07-agent-design/reflection-and-verification.md) |
| 多 Agent 编排 | [07-agent-design/multi-agent-orchestration.md](./07-agent-design/multi-agent-orchestration.md) |
| System Prompt 设计 | [08-prompt-engineering/system-prompts.md](./08-prompt-engineering/system-prompts.md) |
| Few-shot 示例 | [08-prompt-engineering/few-shot-examples.md](./08-prompt-engineering/few-shot-examples.md) |
| 上下文工程总览 | [09-context-engineering/README.md](./09-context-engineering/README.md) |
| 上下文窗口管理 | [09-context-engineering/context-window-management.md](./09-context-engineering/context-window-management.md) |
| 上下文压缩 | [09-context-engineering/context-compression.md](./09-context-engineering/context-compression.md) |
| 短期记忆 (Redis) | [09-context-engineering/conversation-memory.md](./09-context-engineering/conversation-memory.md) |
| 长期记忆 (MySQL) | [09-context-engineering/long-term-memory.md](./09-context-engineering/long-term-memory.md) |
| 工具规格 (10 个工具) | [10-tools-and-apis/tool-specifications.md](./10-tools-and-apis/tool-specifications.md) |
| SSE 流式 API 协议 | [10-tools-and-apis/sse-streaming-api.md](./10-tools-and-apis/sse-streaming-api.md) |
| Feign 客户端设计 | [10-tools-and-apis/feign-clients.md](./10-tools-and-apis/feign-clients.md) |
| 后端微服务结构 | [12-backend-microservice/service-structure.md](./12-backend-microservice/service-structure.md) |
| 数据库 Schema | [12-backend-microservice/database-schema.md](./12-backend-microservice/database-schema.md) |
| 配置设计 | [12-backend-microservice/configuration.md](./12-backend-microservice/configuration.md) |
| MVP 路线图 | [17-roadmap/mvp-phase.md](./17-roadmap/mvp-phase.md) |
| 进阶路线图 | [17-roadmap/advanced-phase.md](./17-roadmap/advanced-phase.md) |
| 远期路线图 | [17-roadmap/future-phase.md](./17-roadmap/future-phase.md) |
| 术语表 | [18-glossary.md](./18-glossary.md) |

---

## 14. 当前实现状态总览（截至 2026-07-02）

### ✅ 已实现（MVP Week 1 + Week 2）

#### 对话与会话

| 能力 | 实现文件 | 说明 |
|------|----------|------|
| SSE 流式对话 | `AgentController` + `AgentChatService` + `DeepSeekClient` | 用户发消息 → RAG 检索 → DeepSeek 流式返回 → 前端逐字渲染 |
| 会话管理 | `AgentSessionService` + `AgentController` | 创建/查询/列表/归档/删除/历史轮次 |
| 多轮上下文 | `AgentChatService.buildMessages` | 自动携带最近 10 轮历史 |
| LLM 弹性 | `ResilienceConfig` + `DeepSeekClient` | 熔断(50%→30s) + 重试(3次指数退避) + 连接池 |
| 用户限流 | `AgentRateLimiter` | Redis 固定窗口 10 次/分/用户 |
| Token 计量 | `AgentChatService` (jtokkit) | prompt/completion/total tokens |
| 鉴权 | `AgentController` (jwtUtils) | JWT 解析 + 会话归属校验 |

#### 会话分类管理

| 能力 | 实现文件 | 说明 |
|------|----------|------|
| 分类 CRUD | `AgentSessionCategoryService` + `AgentController` | 创建/重命名/删除分类，删除时自动移出会话 |
| 会话移动 | `AgentSessionService` + 左滑操作 | 左滑"分类"按钮移动会话到分类，可移出分类 (categoryId = null) |
| 前端分组展示 | `AgentPage.tsx` `useMemo` 分组 | 按 categoryId 客户端分组，文件夹折叠展开 |
| 左滑交互 | `SwipeToDelete.tsx` | Pointer Events 手势系统，支持"分类"+"删除"双按钮，受控 openSwipeId |

#### RAG 检索

| 能力 | 实现文件 | 说明 |
|------|----------|------|
| 向量检索 | `RetrievalService` + `post_vectors` / `knowledge_vectors` | PG-Vector HNSW 索引，余弦距离，top-10 |
| 关键词检索 | `RetrievalService` + pg_trgm GIN 索引 | 知识库标题+内容的 trgm 相似度检索，top-10 |
| RRF 融合 | `RetrievalService.reciprocalRankFusion` | 三路结果（帖子向量 + 知识向量 + 知识关键词）RRF 融合，k=60，top-5 |
| Embedding | `EmbeddingService` + SiliconFlow BGE-M3 API | 1024 维向量，批量向量化 |
| 知识库向量化 | `KnowledgeIngestionService` + `KnowledgeScheduler` | 定时同步 FAQ/教程，分块+向量化写入 knowledge_vectors |
| 帖子向量化 | `PostVectorService` + `PostVectorScheduler` | 定时同步新帖，向量化写入 post_vectors |
| 内部 API | `InternalAgentController` | `/internal/agent/knowledge/*` + `/internal/agent/posts/*` 内部同步接口 |

#### 前端 UI

| 能力 | 实现文件 | 说明 |
|------|----------|------|
| 聊天界面 | `AgentPage.tsx` + `agent.ts` | 侧边栏会话列表 + 主区对话 + 输入框 |
| Markdown 渲染 | `AgentPage.tsx` + `react-markdown` | AI 消息支持 Markdown（标题/粗体/列表/代码块/链接） |
| 流式渲染 | `agent.ts` `fetch` + `ReadableStream` | SSE delta 事件逐字追加 |
| 新建对话反馈 | `AgentPage.tsx` | 按钮按压动画 + Toast 提示 + 消息渐入 |
| 响应式布局 | `AgentPage.tsx` | 移动端底部弹窗 / 桌面端居中 Modal |

### 🟡 Schema 就绪，无 Java 代码

| 能力 | 数据库表 | 说明 |
|------|----------|------|
| 长期记忆 | `user_memory`, `user_memory_evidence`, `user_memory_history` | 三表已建，无 MemoryService |
| 上下文快照 | `agent_context_snapshots` | 表已建，AgentChatService 未写入 |
| 工具注册表 | `agent_tool_registry` | 表已建，无工具执行框架 |
| 知识库原文 | `knowledge_articles` | 表已建，向量同步走内部 API 直写，无 CRUD 服务 |
| 会话审计 | `agent_session_events` | 表已建，归档/删除时未记录 |
| 异步写入队列 | `agent_pending_writes` | 表已建，无队列处理器 |
| 工具错误归档 | `agent_tool_errors` | 表已建，无错误记录 |

### ⬜ 规划中，尚未开始

| 能力 | 规划阶段 | 依赖 |
|------|----------|------|
| 意图分类器 | Week 3 | DeepSeek-V3 JSON 输出 |
| 查询改写器 | Week 3 | DeepSeek-V3 |
| Agent ReAct 循环 | Week 3 | Function Calling + 工具执行器 |
| Feign 跨服务调用 | Week 3 | PostFeignClient + UserFeignClient |
| 工具执行框架 | Week 3 | ToolRegistry + Function Calling |
| 上下文工程 L0-L5 | Week 3-4 | ContextBuilder + TokenCounter |
| 上下文压缩 | 进阶 | Rolling Summary + Slot Freezing |
| Reranker | 进阶 | bge-reranker-v2-m3 |
| 反思校验 | 进阶 | V3 在线 + R1 离线 |
| 安全护栏 | Week 5 | 输入/输出护栏 |
| 评估体系 | 进阶 | 黄金集 + LLM-Judge |
| 多 Agent 编排 | 远期 | Planner + Specialist + Critic |
| MCP 协议 | 远期 | 评估是否引入 |
| 前端引用角标 | Week 4 | [1] [2] 可点击 |
| SSE status/tool/clarify 事件 | Week 3-4 | 后端阶段状态推送 |


---

*本文档是 Agent 模块的架构总览导航，配合 `docs/agent-assistant/` 下的 93 份详细设计文档使用。*
*如有疑问，先看本文档的图，再按导航表去查详细文档。*
