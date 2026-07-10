# CampusShare Agent 全链路工作解读

> **本文档目的**：一张图 + 一条线，讲清楚从用户发消息到AI回复、记忆沉淀的完整过程。SystemPrompt、意图识别、RAG知识库、上下文工程、长期记忆不是孤立的模块，而是一条流水线——每个环节都为上一环节服务，为下一环节提供输入。
>
> 最后更新：2026-07-10

***

## 一、全景时序图：一次完整对话发生了什么

当用户在聊天框输入一句话按下发送，到AI回复逐字出现在屏幕上，整个后端经历了以下流程：

```mermaid
sequenceDiagram
    actor User as 用户
    participant FE as 前端 AgentPage
    participant GW as Gateway
    participant Ctrl as AgentController
    participant RL as AgentRateLimiter
    participant Chat as AgentChatService
    participant SM as SessionStateMachine
    participant Sess as AgentSessionService
    participant Rule as RuleShortCircuitFilter
    participant Intent as IntentClassifier
    participant Router as IntentRouter
    participant Mem as MemoryRetrievalService
    participant RAG as RetrievalService
    participant PA as PromptAssembler
    participant PVM as PromptVersionManager
    participant CA as ContextAssembler
    participant Snap as ContextSnapshotService
    participant LLM as DeepSeekClient
    participant Compress as ContextCompressionService
    participant DB as MySQL
    participant Redis as Redis
    participant PG as PostgreSQL(pgvector)

    User->>FE: 输入消息并发送
    FE->>GW: POST /api/agent/chat (SSE, JWT)
    GW->>Ctrl: chat(userId, request)

    Note over Ctrl: === 阶段0：入口防护 ===
    Ctrl->>RL: checkRateLimit(userId)
    RL->>Redis: INCR agent:rate_limit:{uid}
    alt 超限(>10次/分)
        RL-->>Ctrl: false
        Ctrl-->>FE: SSE: error "请求过于频繁"
    end

    Note over Chat: === 阶段1：会话管理与状态机 ===
    Chat->>Sess: getOrCreateSession(userId, sessionId)
    alt 新会话
        Sess->>DB: INSERT agent_sessions (status=INIT)
        Sess->>Redis: initSession 5个Key + TTL=2h
        Sess->>SM: transition(INIT→ACTIVE)
    else 已有会话
        Sess->>SM: canTransition→transition→CAS原子更新(Redis+DB)
    end
    Sess->>Redis: renewTTL(5个Key 续期2h)
    Chat->>DB: createTurn (status=STREAMING)

    Note over Chat: === 阶段2：意图识别三层漏斗 ===
    Chat->>Rule: filter(query) — 规则匹配 <5ms
    Rule-->>Chat: 命中? 直接返回IntentResult

    alt 规则未命中
        Chat->>Intent: classify(query) — LLM分类
        Intent->>LLM: 结构化意图分类Prompt(temperature=0)
        LLM-->>Intent: {intent, subIntent, confidence, slots, rewrittenQuery}
        Intent-->>Chat: IntentResult
        alt LLM失败/低置信度
            Chat->>Chat: EmbeddingIntentFallback 语义兜底
        end
    end
    Chat->>Redis: recordIntent + incrementTurnCount

    Note over Chat: === 阶段2.5：安全护栏（输入侧）===
    Chat->>Chat: shouldHardBlock? → 硬拦截(403)
    Chat->>Chat: detectInjection? → log+meter(不阻断)
    Chat->>Chat: SchoolNameUtils.extractFromQuery → 正则提取学校名

    Note over Chat: === 阶段3：快/慢路径分流 ===
    Chat->>Router: tryShortCircuit(intent)

    alt 快路径（OUT_OF_SCOPE / NAVIGATE）
        Router-->>Chat: RouteDecision(shortCircuit=true)
        Note over Chat: 不调LLM，不检索，模板直返
        Chat-->>FE: SSE: session → delta(模板回复) → navigate(可选)
        Chat->>DB: updateTurn(status=COMPLETED)
        Chat->>Redis: appendMessage(user+assistant)
    else 慢路径（HOW_TO / SEARCH / CLARIFY）
        Router-->>Chat: empty() → 走RAG管线

        Note over Chat: === 阶段4：Prompt版本选择 ===
        Chat->>PVM: getCurrentVersion(userId)
        Note over PVM: Redis读版本号+灰度比例
        Note over PVM: userId哈希分流(灰度用户用上一版)
        Note over PVM: Redis→DB→硬编码三级降级
        PVM-->>Chat: PromptVersion

        Note over Chat: === 阶段5：记忆检索（并行）===
        par 并行
            Chat->>Mem: loadProfileMemories(userId) — 用户画像
        and
            Chat->>Mem: retrieveRelevantMemories(userId, query) — 相关记忆
        end
        Mem->>PG: 向量+关键词双路RRF检索 memory_vectors
        Mem-->>Chat: 用户画像文本 + 相关记忆列表

        Note over Chat: === 阶段6：RAG混合检索 ===
        Chat->>RAG: retrieve(rewrittenQuery, intent, previousResults)
        RAG->>LLM: embed(query) → 1024维(BGE-M3)
        par 四路并行（按意图动态topK）
            RAG->>PG: ① 知识库向量 (knowledge_vectors HNSW)
        and
            RAG->>PG: ② 知识库关键词 (pg_trgm GIN)
        and
            RAG->>PG: ③ 帖子向量 (post_vectors + slots过滤)
        and
            RAG->>PG: ④ 帖子关键词 (按意图启用)
        end
        Note over RAG: CLARIFY意图合并上轮结果(score×0.5)
        RAG->>RAG: filterByThreshold → aggregateByArticle → rrfFusion
        RAG->>RAG: applyQualityWeight → crossSourceDedup → truncateByTokenBudget
        RAG->>Redis: 缓存检索结果(5min TTL)
        RAG-->>Chat: List<RetrievalResult>

        Note over Chat: === 阶段7：System Prompt装配 ===
        Chat->>PA: assemble(intent, allResults, promptVersion)
        Note over PA: L1平台级(命中Prefix Cache) + L2任务级(按意图)
        Note over PA: + L3 Few-shot + <context>检索结果+记忆 + L4护栏
        PA-->>Chat: 完整System Prompt

        Note over Chat: === 阶段8：对话历史加载 ===
        Chat->>Redis: loadSummary + loadSlots + loadPinned + loadMessages
        alt Redis miss
            Chat->>DB: 回源MySQL → 回写Redis
        end
        Chat->>Chat: 装配虚拟轮次（摘要/槽位/Pin + 最近N轮）

        Note over Chat: === 阶段9：上下文工程L0-L5分层装载 ===
        Chat->>CA: assemble(sessionId, turnId, query, intent, systemPrompt, history, userProfile)
        Note over CA: Token预算 ~8000tok
        Note over CA: L0永驻~5000 | L1~300 | L4~1500-4000 | L5~700
        Note over CA: 三级降级：L4→2轮 → 丢L1 → L4→1轮
        CA-->>Chat: List<Message> + token统计 + snapshot
        Chat->>Snap: saveSnapshot(snapshot) — fire-and-forget写MySQL

        Note over Chat: === 阶段10：LLM流式生成 ===
        Chat-->>FE: SSE: session {sessionId}
        Chat->>LLM: chatCompletionStream(messages)
        loop 逐token (Resilience4j熔断保护)
            LLM-->>Chat: delta token
            Chat-->>FE: SSE: delta {content}
            FE->>FE: 逐字渲染Markdown
        end

        Note over Chat: === 阶段11：后处理 ===
        Chat->>Chat: buildRefsJson → refsEvent
        Chat-->>FE: SSE: refs [{index,id,type,title,url}]
        FE->>FE: 渲染引用卡片

        Note over Chat: === 阶段12：持久化与短期记忆写入 ===
        Chat->>DB: updateTurn(status=COMPLETED, tokens, responseTime)
        Chat->>DB: updateSession(messageCount, totalTokens)
        Chat->>Chat: Constitutional AI输出验证(仅log+meter)
        Chat->>Redis: appendMessage(user+assistant) + renewTTL

        alt 消息数>10 → 触发上下文压缩
            Chat->>Compress: compress(oldSummary, oldMsgs, existingSlots)
            Compress->>LLM: 三合一压缩（摘要+槽位+Pin）
            alt LLM压缩失败
                Compress-->>Chat: fallback=true
                Chat->>Redis: LTRIM保留最近4条
            else 成功
                Chat->>Redis: updateSummary + updateSlots + pinMessage + LTRIM
            end
        end
    end

    Note over Chat,Sess: === 异步背景：会话归档与长期记忆抽取 ===
    Note over Sess: SessionArchivalService每分钟扫描
    Sess->>Redis: SCAN 检测last_active>30min的僵尸会话
    alt 发现僵尸会话/用户主动关闭
        Sess->>SM: transition(ACTIVE→ARCHIVED) CAS
        Sess->>Chat: extractMemories(sid, uid, rollingSummary)
        Chat->>LLM: 从摘要抽取显式偏好/事实/行为
        Chat->>Chat: ConflictResolver.resolveOnInsert(冲突检测)
        Chat->>DB: UPSERT user_memory (confidence累加)
        Chat->>PG: upsert memory_vectors
        Sess->>Redis: EXPIRE 5个Key 5分钟后自动删除
    end

    Note over Chat: === 定时任务（独立于对话流）===
    Note over Chat: • KnowledgeScheduler: 启动30s后+每小时 → ingestAll
    Note over Chat: • PostVectorScheduler: 启动60s后+每5min → syncAll
    Note over Chat: • 每周日02:00 → decayMemories 长期记忆衰减
```

***

## 二、核心架构图：各模块的位置与数据流

```mermaid
flowchart TB
    subgraph 入口["入口层（WebFlux + SSE）"]
        Ctrl["AgentController<br/>JWT鉴权 → 限流 → SSE输出"]
        RL["AgentRateLimiter<br/>Redis固定窗口<br/>10次/分/userId<br/>fail-open降级"]
        Resilience["ResilienceConfig<br/>4个独立熔断器<br/>+全局100/min限流"]
    end

    subgraph 输入侧["用户输入"]
        Query["用户消息<br/>（文本）"]
    end

    subgraph 会话层["会话管理与状态机"]
        Sess["AgentSessionService<br/>CRUD + 权限验证<br/>软删除(status=DELETED)"]
        SM["SessionStateMachine<br/>8状态CAS转移<br/>Redis+DB双写<br/>事件审计90天"]
        Cat["AgentSessionCategory<br/>文件夹分类管理<br/>下属会话软转移"]
        Arch["SessionArchivalService<br/>僵尸检测(30min)<br/>归档→记忆抽取→延迟清理"]
    end

    subgraph 意图层["意图识别三层漏斗"]
        R1["Layer1: 规则短路<br/>RuleShortCircuitFilter<br/>正则匹配 <5ms<br/>过滤~25%流量"]
        R2["Layer2: LLM分类<br/>IntentClassifier<br/>DeepSeek temperature=0<br/>JSON结构化输出+改写"]
        R3["Layer3: Embedding兜底<br/>EmbeddingIntentFallback<br/>语义相似度匹配"]
        R4["Default: SEARCH兜底<br/>全部失败时默认资源搜索"]
        R1 -->|未命中| R2
        R2 -->|失败/低置信| R3
        R3 -->|失败| R4
    end

    subgraph 分流层["快/慢路径分流"]
        Fast["快路径 ⚡<br/>OUT_OF_SCOPE(chitchat/写操作/超范围)<br/>NAVIGATE(跳转)<br/>→ 模板回复，0 LLM调用"]
        Slow["慢路径 🔍<br/>HOW_TO(操作指南)<br/>SEARCH(资源/讨论/问答)<br/>CLARIFY(多轮澄清)<br/>→ RAG + LLM"]
    end

    subgraph Prompt管理层["Prompt版本管理"]
        PVM["PromptVersionManager<br/>Redis版本号缓存<br/>按userId哈希灰度分流<br/>Redis→DB→硬编码三级降级"]
    end

    subgraph 记忆层["记忆系统（慢路径）"]
        M1["短期记忆 Redis<br/>5个Key/session<br/>TTL 2h滑动续期<br/>Redis→DB两级缓存"]
        M2["长期记忆 MySQL+PG<br/>用户画像/偏好<br/>向量语义检索<br/>周衰减+冲突解决<br/>ConflictResolver显式优先"]
    end

    subgraph RAG层["RAG混合检索（在线）"]
        direction TB
        Embed["Embedding<br/>BGE-M3 → 1024维<br/>熔断+重试+空向量降级"]
        subgraph 四路并行["四路并行（意图驱动配比）"]
            KV["① 知识库向量<br/>knowledge_vectors<br/>HNSW 余弦距离"]
            KK["② 知识库关键词<br/>pg_trgm GIN索引"]
            PV["③ 帖子向量<br/>post_vectors<br/>+ slots SQL过滤"]
            PK["④ 帖子关键词<br/>按配置启用"]
        end
        Proc["后处理链：<br/>阈值过滤 → chunk聚合 → RRF融合<br/>质量加权(FourDimension) → 跨源去重 → Token截断<br/>→ Redis缓存5min"]
        Embed --> KV
        Embed --> PV
        KV --> Proc
        KK --> Proc
        PV --> Proc
        PK --> Proc
    end

    subgraph 离线摄入["离线数据管线（定时任务）"]
        direction TB
        KS["KnowledgeScheduler<br/>启动30s+每小时"]
        PS["PostVectorScheduler<br/>启动60s+每5min"]
        KI["KnowledgeIngestionService<br/>扫描.md → MD5增量<br/>→ MarkdownChunker分块<br/>→ embed批量(32) → 重复检测<br/>→ 质量评分 → SemVer版本<br/>→ MySQL+PG写入"]
        PVS["PostVectorService<br/>分页拉取(100/批)<br/>→ embed → upsert向量<br/>独立熔断器保护"]
        KQC["KnowledgeChunker<br/>(MarkdownChunker)<br/>H2→H3→段落→句子<br/>50token重叠+标题路径"]
        KQD["KnowledgeDuplicateDetector<br/>(ThresholdDuplicateDetector)<br/>双阈值: 0.95重复/0.85相似<br/>fail-open(UNIQUE)"]
        KQS["KnowledgeQualityScorer<br/>(FourDimensionQualityScorer)<br/>召回0.4+反馈0.3+新鲜0.2+完整0.1"]
        KV["KnowledgeVersionService<br/>完整快照(非diff)<br/>SemVer递增<br/>回滚产生新版本<br/>反馈调整质量分"]
        KS --> KI
        PS --> PVS
        KI --> KQC
        KI --> KQD
        KI --> KQS
        KI --> KV
    end

    subgraph Prompt层["System Prompt装配"]
        L1["L1 平台级Prompt<br/>（固定，命中Prefix Cache）"]
        L2["L2 任务级Prompt<br/>（按意图切换HOW_TO/SEARCH/...）"]
        L3["L3 Few-shot示例"]
        CTX["<context>检索结果+记忆<br/>（资料≠指令，防注入）"]
        GD["L4 安全护栏<br/>（末尾防注入覆盖）"]
        L1 --> SP["完整System Prompt"]
        L2 --> SP
        L3 --> SP
        CTX --> SP
        GD --> SP
    end

    subgraph 上下文层["上下文工程 L0-L5"]
        CL0["L0 System Prompt<br/>~4000-5000tok ★永驻"]
        CL1["L1 用户画像<br/>~300tok ★可裁剪"]
        CL4["L4 对话历史<br/>~1500-4000tok ★可压缩"]
        CL5["L5 当前输入<br/>~700tok ★永驻"]
        Budget["Token预算总控<br/>maxInput≈8000tok"]
        Degrade["三级降级链<br/>L4→2轮 → 丢L1 → L4→1轮"]
        Snap["📸 ContextSnapshot<br/>异步持久化<br/>答错回放唯一证据"]
        CL0 --> Msg["Messages列表"]
        CL1 --> Msg
        CL4 --> Msg
        CL5 --> Msg
        Msg --> Budget
        Budget -->|超预算| Degrade
        Degrade --> Msg
        Msg --> Snap
    end

    subgraph 输出侧["LLM流式输出 + 后处理"]
        LLM["DeepSeek-V4-Flash<br/>流式SSE返回<br/>熔断+重试弹性保护"]
        Refs["refs事件<br/>引用源JSON<br/>前端渲染卡片"]
        Out["输出验证<br/>Constitutional AI<br/>（仅log+meter）"]
        Save["持久化<br/>MySQL turn+session<br/>Redis短期记忆"]
        Compress["上下文压缩<br/>消息>10时触发<br/>三合一LLM压缩<br/>失败降级LTRIM"]
        Extract["长期记忆抽取<br/>会话归档时<br/>extractMemories"]
        Metrics["Micrometer指标<br/>intent.* / knowledge.*<br/>/prompt.version.fallback<br/>→ Prometheus/Grafana"]
        LLM --> Refs
        LLM --> Out
        Out --> Save
        Save --> Compress
        Compress --> M1
        Save -.->|归档时| Extract
        Extract --> M2
    end

    Query --> Ctrl
    Ctrl --> RL
    RL --> R1
    Ctrl --> Sess
    Sess --> SM
    R1 -->|命中chitchat/navigate| Fast
    R2 -->|OUT_OF_SCOPE/NAVIGATE| Fast
    Fast -->|"模板回复<br/>SSE直返"| Out2["前端渲染"]
    R2 -->|HOW_TO/SEARCH/CLARIFY| Slow
    R3 --> Slow
    R4 --> Slow
    Slow --> PVM
    PVM --> L1
    Slow --> M2
    Slow --> RAG层
    M2 -->|"用户画像<br/>相关记忆"| CTX
    M1 -->|"历史摘要/槽位<br/>Pin/最近轮次"| CL4
    RAG层 --> Proc
    Proc --> CTX
    SP --> CL0
    Msg --> LLM
    LLM -->|"逐token delta"| Out2
    Arch -.->|定时触发| Extract
    Resilience -.->|保护| LLM
    Resilience -.->|保护| Embed
    Resilience -.->|保护| PVS
    Metrics -.->|全局埋点| 意图层
    Metrics -.->|全局埋点| RAG层
    Metrics -.->|全局埋点| Prompt层

    style Fast fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    style Slow fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    style RAG层 fill:#f3e5f5,stroke:#7b1fa2
    style 上下文层 fill:#e8f5e9,stroke:#388e3c
    style M1 fill:#e0f7fa,stroke:#00838f
    style M2 fill:#fce4ec,stroke:#c62828
    style 离线摄入 fill:#f9fbe7,stroke:#827717
    style 会话层 fill:#efebe9,stroke:#4e342e
    style 入口 fill:#e8eaf6,stroke:#283593
    style Prompt管理层 fill:#fce4ec,stroke:#880e4f
```

***

## 三、各模块深度解读

### 3.1 入口防护：限流 + 弹性保护 + JWT鉴权

请求到达业务逻辑之前，先经过两层防护：

```mermaid
flowchart LR
    Req["SSE请求"] --> JWT["JWT鉴权<br/>解析userId"]
    JWT --> RL{"AgentRateLimiter<br/>Redis固定窗口<br/>10次/分钟/userId"}
    RL -->|超限| Err["SSE error事件<br/>'请求过于频繁'"]
    RL -->|通过| FB{"Resilience4j<br/>全局限流<br/>100次/分钟"}
    FB -->|熔断/限流| Err2["快速失败/降级"]
    FB -->|通过| Biz["进入AgentChatService"]

    RL -->|Redis异常| Biz
    style Err fill:#fce4ec,stroke:#c62828
    style Err2 fill:#fce4ec,stroke:#c62828
```

**限流设计要点**：

- **用户级限流**：Redis Key `agent:rate_limit:{userId}`，固定窗口计数器，1分钟10次。首次请求设TTL=60s，`INCR`原子计数
- **全局限流兜底**：Resilience4j RateLimiter `agent-global`，100次/分钟，防突发流量
- **Fail-Open策略**：Redis异常时默认放行（`AgentRateLimiter`返回true），保证可用性优先于限流
- **响应式编程**：使用`ReactiveStringRedisTemplate`，与WebFlux全链路非阻塞集成

**Resilience4j弹性保护**：4个独立熔断器，每个外部依赖隔离，防止故障级联：

| 熔断器                 | 保护目标           | 默认参数                      |
| ------------------- | -------------- | ------------------------- |
| `deepseek`          | DeepSeek LLM调用 | 窗口10次/50%失败率/熔断30s/半开3次试探 |
| `embedding`         | BGE-M3向量化      | 同上                        |
| `post-sync`         | 帖子服务同步调用       | 同上                        |
| `intent-classifier` | 意图分类LLM调用      | 同上                        |

熔断状态机：`CLOSED(正常) → 失败率超阈值 → OPEN(快速失败30s) → HALF_OPEN(试探3次) → 成功→CLOSED / 失败→OPEN`

所有外部LLM/Embedding调用还配置了**指数退避重试**（3次，初始1s），仅对5xx和网络超时重试，4xx快速失败。

***

### 3.2 会话管理与状态机：从创建到归档的完整生命周期

会话不是简单的数据库记录，而是有严格状态流转的实体。

**8状态流转图**：

```mermaid
stateDiagram-v2
    [*] --> INIT: 创建会话
    INIT --> ACTIVE: 首条消息到达
    ACTIVE --> TOOL_CALLING: 工具调用中
    TOOL_CALLING --> ACTIVE: 工具返回
    ACTIVE --> WAITING_CLARIFY: 需要用户澄清
    WAITING_CLARIFY --> ACTIVE: 用户补充信息
    ACTIVE --> REFLECTING: 回答完成(异步伪状态)
    REFLECTING --> ACTIVE: 后处理完成
    ACTIVE --> ARCHIVED: 30min无活动/定时扫描
    ACTIVE --> CLOSED: 用户主动关闭(终态)
    INIT --> ERROR: 不可恢复错误
    ACTIVE --> ERROR: 不可恢复错误
    TOOL_CALLING --> ERROR: 不可恢复错误
    WAITING_CLARIFY --> ERROR: 不可恢复错误
    ARCHIVED --> [*]: Redis延迟5min清理
    CLOSED --> [*]: Redis延迟5min清理
    ERROR --> [*]: 需新建会话

    note right of ACTIVE: TOOL_CALLING/WAITING_CLARIFY\n不能直接到ARCHIVED\n(防中途归档)
    note right of CLOSED: 终态，不可转移
    note right of ERROR: 不可恢复，用户需新开会话
```

**关键设计**：

1. **CAS原子转移**：`SessionStateMachine.transition()` 使用CAS语义——先校验`canTransition(from,to)`，再检查Redis当前状态，再更新Redis和DB。MVP阶段用HGET+HSET（非原子Lua），故障时容忍跳过CAS，事件审计保留90天。
2. **Redis+DB双写一致性**：状态先写Redis（即时生效），再更新DB；Redis miss时降级读DB。
3. **会话归档流程**（`SessionArchivalService`）——**长期记忆抽取的触发点**：

```mermaid
sequenceDiagram
    participant Scheduler as @Scheduled(每分钟)
    participant Redis
    participant SM as SessionStateMachine
    participant Arch as SessionArchivalService
    participant LTM as LongTermMemoryService
    participant CR as ConflictResolver
    participant DB as MySQL
    participant PG as pgvector

    Scheduler->>Redis: SCAN agent:session:*:meta
    Scheduler->>Scheduler: 过滤last_active_at>30min AND status=ACTIVE
    loop 每个僵尸会话
        Arch->>SM: transition(ACTIVE→ARCHIVED) CAS
        Arch->>Redis: loadSummary(sid)
        Arch->>LTM: extractMemories(sid, uid, summary)
        LTM->>LTM: LLM抽取偏好/事实/行为
        LTM->>CR: resolveOnInsert(newMemory)
        CR->>CR: EXPLICIT>INFERRED/新时间戳优先/Jaccard<0.3冲突
        CR->>DB: UPSERT user_memory + 降置信/标记冲突
        LTM->>PG: upsert memory_vectors
        Arch->>DB: UPDATE sessions SET status=ARCHIVED
        Arch->>Redis: EXPIRE 5个Key 300s(延迟清理防竞态)
    end
```

1. **软删除**：`deleteSession()`先归档（确保记忆抽取完成），再标记`status=DELETED`，不物理删除。
2. **会话分类**：`AgentSessionCategoryService`提供文件夹功能（创建/重命名/删除分类），删除分类时下属会话`categoryId`置null（软转移，不级联删除）。

**短期记忆Redis Key结构**（每个会话5个Key，初始化时统一TTL=2h，每次活跃续期）：

| Key                                   | 类型     | 内容                                                                                |
| ------------------------------------- | ------ | --------------------------------------------------------------------------------- |
| `agent:session:{sid}:meta`            | Hash   | user\_id, status, current\_intent, intent\_history, turn\_count, last\_active\_at |
| `agent:session:{sid}:messages`        | List   | 最近20条消息(RPUSH/LTRIM)，JSON序列化MemoryMessage                                         |
| `agent:session:{sid}:rolling_summary` | String | 滚动摘要文本（压缩后更新）                                                                     |
| `agent:session:{sid}:slots`           | Hash   | 已确认槽位(school/category/topic等)                                                     |
| `agent:session:{sid}:pinned`          | List   | Pin消息(最多5条)，重要偏好永不压缩                                                              |

**Redis→MySQL两级缓存模式**：读操作Redis miss时自动回源MySQL（`ContextSummary`/`ContextSlot`/`PinMessage`表）并回写Redis；写操作先写Redis立即返回，MySQL异步持久化（boundedElastic线程池），持久化失败仅log告警不影响对话。

***

### 3.3 System Prompt 工程：五层结构 + 版本管理 + Prefix Cache

System Prompt不是一段长文本，而是**五层结构**拼接而成，并且支持**版本灰度切换**：

```Textile
┌─────────────────────────────────────────────────┐
│ L1 平台级 Prompt（PLATFORM_PROMPT）              │
│ ─ 身份定义：你是CampusShare AI助手小享            │
│ ─ 核心能力：找资料、解答平台使用问题               │
│ ─ 回复规则：必须引用来源、Markdown格式、中文回复   │
│ ★ 字节级固定(ADR-SP-06) → 命中Prefix Cache       │
├─────────────────────────────────────────────────┤
│ L2 任务级 Prompt（按意图切换，灰度可切）           │
│ ─ HOW_TO_PROMPT：操作指南回答模板                │
│ ─ SEARCH_PROMPT：资源搜索回答模板                │
│ ─ NAVIGATE_PROMPT：跳转引导模板                  │
│ ─ CLARIFY_PROMPT：澄清追问模板                   │
│ ─ OUT_OF_SCOPE_PROMPT：超范围拒绝模板            │
├─────────────────────────────────────────────────┤
│ L3 Few-shot 示例                                 │
│ ─ 2-3个高质量问答示例，教LLM输出格式             │
├─────────────────────────────────────────────────┤
│ <context> 检索结果 + 相关记忆                     │
│ ─ [n] 来源：知识库/帖子/用户记忆 | 标题：xxx      │
│ ─ 章节/可信度/分类/学校等元数据                  │
│ ─ 内容：...                                     │
│ ★ 用<context>标签包裹 → 资料≠指令 → 防注入      │
├─────────────────────────────────────────────────┤
│ L4 安全护栏（GUARDRAIL_PROMPT）                   │
│ ─ 防Prompt泄露、防越狱、防敏感内容               │
│ ★ 放在末尾 → 近因效应覆盖前面的任何恶意指令      │
└─────────────────────────────────────────────────┘
```

**Prompt版本管理与灰度发布**（`PromptVersionManager`）：

```mermaid
flowchart LR
    A[请求到达] --> B[Redis读版本号和灰度比例]
    B --> C{grayRatio < 100?}
    C -->|否 全量发布| D[使用current_version]
    C -->|是 灰度中| E[userId哈希取模分流]
    E -->|灰度组| D
    E -->|对照组| F[使用上一RELEASED版本]
    D --> G[从DB加载PromptVersion]
    F --> G
    G -->|DB故障| H[降级到硬编码常量]
    G --> I[PromptAssembler装配]
    H --> I
```

- **秒级切换**：`switchVersion()`只改Redis版本号，所有新请求立即生效
- **确定性灰度**：同一用户始终在同一分组（hashCode取模），不会一会用A版一会用B版
- **灰度只切L2/L3/L4**：L1平台级字节级固定保证Prefix Cache命中率
- **三级降级**：Redis→DB→硬编码常量，极端故障也能提供基础服务
- **可观测**：降级次数通过`prompt.version.fallback` Counter监控

**关键设计决策**：

- **Prefix Cache命中**：L1平台级prompt所有请求完全相同，DeepSeek缓存prefix部分，这部分token只计1/10价格，首token延迟也显著降低
- **`<context>`标签防注入**：即使用户在帖子内容里写"忽略以上指令"，LLM也不会执行
- **护栏放末尾**：近因效应——大模型对最后看到的指令权重最高

***

### 3.4 意图识别：三层漏斗，从快到准

用户消息进来后，不是直接扔给LLM分类，而是经过**三层漏斗**，先用最快最便宜的方式过滤高确定性请求：

```mermaid
flowchart LR
    Q["用户消息"] --> L1{"Layer1: 规则匹配<br/>(<5ms, 0成本)"}
    L1 -->|"命中(25%流量)"| Done1["✅ 直接返回<br/>闲聊/写操作/指代词/个人列表"]
    L1 -->|"未命中"| L2{"Layer2: LLM分类<br/>(~300ms, 低token)"}
    L2 -->|"成功(≥4类)"| Done2["✅ IntentResult<br/>intent+subIntent+slots+rewrittenQuery"]
    L2 -->|"失败/低置信<0.6"| L3{"Layer3: Embedding兜底<br/>(语义相似度)"}
    L3 -->|"匹配成功(≥0.7)"| Done3["✅ 近似意图"]
    L3 -->|"失败"| Done4["🔄 Default SEARCH<br/>兜底为资源搜索"]

    style L1 fill:#e8f5e9,stroke:#388e3c
    style L2 fill:#e3f2fd,stroke:#1976d2
    style L3 fill:#fff3e0,stroke:#f57c00
    style Done4 fill:#fce4ec,stroke:#c62828
```

**五大意图体系**：

| 意图                 | 子意图                                           | 触发场景              | 路径      | 检索策略                           |
| ------------------ | --------------------------------------------- | ----------------- | ------- | ------------------------------ |
| **HOW\_TO**        | —                                             | "怎么发帖""怎么认证"      | 慢路径RAG  | 仅知识库（postTopK=0），threshold=0.5 |
| **SEARCH**         | resource                                      | "有没有高数资料"         | 慢路径RAG  | 偏帖子（postTopK=8），threshold=0.4  |
| **SEARCH**         | discussion                                    | "大家怎么看考研"         | 慢路径RAG  | 偏帖子+关键词，threshold=0.4          |
| **SEARCH**         | content\_qa                                   | "什么是学分绩点"         | 慢路径RAG  | 偏知识库（knowledgeTopK=8）          |
| **NAVIGATE**       | my\_list/feature\_loc/section\_loc            | "去我的收藏""通知在哪"     | **快路径** | 不检索 → 模板回复+navigate事件          |
| **CLARIFY**        | coreference                                   | "那个帖子""上面那个"      | 慢路径RAG  | 均衡检索+上轮结果降权合并                  |
| **OUT\_OF\_SCOPE** | chitchat/write\_action/open\_domain/sensitive | "你是谁""帮我发帖""今天天气" | **快路径** | 不检索 → 模板回复                     |

**规则短路四类规则（优先级从高到低）**：

1. **指代词→CLARIFY**：含"那个/它/上面那个"等 → 强制追问澄清
2. **写操作→OUT\_OF\_SCOPE**：含"帮我发/帮我点赞/帮我改" → 拒绝模板
3. **闲聊问候→OUT\_OF\_SCOPE**：正则匹配"你好/谢谢/你是谁/再见"开头 → 闲聊模板
4. **个人列表→NAVIGATE**：含"我点赞的/我收藏的" → 跳转卡片

**学校名称双提取**：规则层用`SchoolNameUtils.extractFromQuery()`正则预提取学校名（含别名归一化如"北大"→"北京大学"），同时LLM分类输出的slots.school也会被归一化，双重保障避免"北大"导致SQL过滤失败。

**LLM分类输出结构**（JSON格式，temperature=0，确定性输出）：

```json
{
  "intent": "SEARCH",
  "subIntent": "resource",
  "confidence": 0.92,
  "slots": { "school": "北京大学", "category": "资料", "topic": "高数" },
  "rewrittenQuery": "北京大学 高数 资料 资源帖"
}
```

`rewrittenQuery`将口语化表达改写为检索关键词组合，`slots`用于帖子向量检索时的结构化SQL过滤（`WHERE school_id=? AND category_id=?`）。

此外还有**意图缓存**（`IntentCacheService`）：完全相同的query字符串直接返回缓存的意图结果，进一步降低LLM调用成本。

**全链路监控指标**：

- `agent.intent.classification.total`：按intent/subIntent/layer/result计数
- `agent.intent.classification.duration`：各层分类耗时
- `agent.intent.cache.total`：缓存命中率
- `agent.intent.route.total`：快/慢路径路由计数

***

### 3.5 RAG知识库：四路并行+RRF融合+后处理链（在线检索）

RAG是慢路径的核心，负责从知识库和帖子中找到与用户问题相关的参考资料。整个检索管线是**意图驱动**的——不同意图的检索来源配比、topK、阈值完全不同。

```mermaid
flowchart TD
    Q["改写后的query"] --> Embed["BGE-M3 Embedding<br/>→ 1024维向量<br/>(熔断:空向量降级)"]

    subgraph 四路并行检索["四路并行检索"]
        direction TB
        KV["① 知识库向量<br/>knowledge_vectors HNSW<br/>余弦距离 top-K"]
        KK["② 知识库关键词<br/>pg_trgm GIN<br/>标题+内容相似度 top-K"]
        PV["③ 帖子向量<br/>post_vectors HNSW<br/>+ slots SQL过滤<br/>(school/category/postType) top-K"]
        PK["④ 帖子关键词<br/>pg_trgm GIN<br/>按意图启用 top-K"]
    end

    Embed --> KV
    Embed --> PV
    Q --> KK
    Q --> PK

    KV --> F1["filterByThreshold<br/>相似度阈值过滤<br/>(HOW_TO:0.5, SEARCH:0.4)"]
    KK --> F2["filterByThreshold"]
    PV --> F3["filterByThreshold"]
    PK --> F4["filterByThreshold"]

    F1 --> Agg["aggregateByArticle<br/>chunk→文章级聚合<br/>同文多chunk加成"]
    F2 --> Agg

    F3 --> RRF["RRF融合<br/>score = Σ 1/(k+rank)<br/>k=60"]
    F4 --> RRF
    Agg --> RRF

    CLARIFY["⑤ CLARIFY: 上轮结果<br/>score×0.5降权加入"] --> RRF

    RRF --> QW["applyQualityWeight<br/>知识库: score×(0.8+0.2×qualityScore)"]
    QW --> Dedup["crossSourceDedup<br/>跨源标题Jaccard>0.8去重"]
    Dedup --> Trunc["truncateByTokenBudget<br/>按token预算截断<br/>(~2500tok)"]
    Trunc --> Cache["缓存Redis(5min TTL)"]
    Trunc --> Out["最终检索结果<br/>List<RetrievalResult>"]

    style RRF fill:#e1f5fe,stroke:#0288d1,stroke-width:2px
    style Embed fill:#f3e5f5,stroke:#7b1fa2
```

**意图驱动的检索配置**：

| 配置项                  | HOW\_TO | SEARCH/resource | SEARCH/discussion | SEARCH/content\_qa | CLARIFY |
| -------------------- | ------- | --------------- | ----------------- | ------------------ | ------- |
| knowledgeTopK        | 8       | 2               | 2                 | 8                  | 5       |
| knowledgeKeywordTopK | 5       | 2               | 0                 | 5                  | 3       |
| postTopK             | **0**   | 8               | 8                 | 3                  | 5       |
| postKeywordTopK      | 0       | 5               | 5                 | 2                  | 3       |
| similarityThreshold  | 0.5     | 0.4             | 0.4               | 0.5                | 0.4     |
| 低置信度boost            | +3      | +3              | +3                | +3                 | —       |

**关键设计细节**：

- **chunk→文章级聚合**：知识库被切分成\~256token的chunk存入向量库，检索回来后按article\_id聚合，同一文章取最高分chunk，但多个chunk命中有加成（`maxSim × (1 + 0.1 × (chunkHits - 1))`）
- **RRF融合无需调权重**：RRF只看排名不看绝对分数，天然适配多路异构检索（余弦距离0-1、trgm相似度0-1无需归一化）
- **质量加权**：四维质量分（见3.6）影响排序，引导LLM引用可靠文档
- **跨源去重**：Jaccard分词相似度>0.8时移除低分那条
- **Token预算截断**：逐条累加token数到上限就停，精确控制注入prompt长度
- **Redis缓存**：非CLARIFY意图结果缓存5分钟，省Embedding和PG查询
- **Embedding降级**：熔断打开时返回空向量，四路检索中向量路返回空结果，仅有关键词路工作（降级检索）

***

### 3.6 离线数据管线：知识库摄入与帖子向量同步

在线检索的质量依赖于离线数据管线的持续维护。两条独立的定时管道保证向量库数据新鲜：

```mermaid
flowchart TB
    subgraph 知识库摄入["📚 知识库摄入管道 (KnowledgeScheduler)"]
        direction TB
        Trigger1["启动30s后 + 每小时1次"] --> Scan["扫描docs-path/*.md"]
        Scan --> MD5{"MD5校验<br/>文件是否变更?"}
        MD5 -->|未变更| Skip["⏭️ 跳过"]
        MD5 -->|新增/变更| Parse["解析frontmatter<br/>title/topic/tags"]
        Parse --> Chunk["MarkdownChunker分块<br/>H2→H3→段落→句子<br/>target=256tok, overlap=50tok"]
        Chunk --> Embed1["embedBatch批量向量化<br/>BGE-M3, batch=32"]
        Embed1 --> Dup{"ThresholdDuplicateDetector<br/>首chunk vs 已有文档"}
        Dup -->|≥0.95| Skip2["⏭️ DUPLICATE 跳过"]
        Dup -->|≥0.85| Warn["⚠️ SIMILAR 警告但继续"]
        Dup -->|<0.85| Snap["KnowledgeVersionService<br/>snapshot()创建快照<br/>SemVer patch+1"]
        Warn --> Snap
        Snap --> Score["FourDimensionQualityScorer<br/>召回0.4+反馈0.3+新鲜0.2+完整0.1"]
        Score --> Write1["写入MySQL knowledge_articles<br/>+ PG knowledge_vectors"]
    end

    subgraph 帖子同步["📝 帖子向量同步管道 (PostVectorScheduler)"]
        direction TB
        Trigger2["启动60s后 + 每5分钟1次<br/>(兜底:通知丢失也能补齐)"] --> Page["分页拉取post-service<br/>size=100/批"]
        Page --> Embed2["单帖: title+'\\n'+excerpt<br/>→ BGE-M3 embedding"]
        Embed2 --> Write2["UPSERT PG post_vectors"]
        Delete["内部接口DELETE事件"] --> DelVec["直接删除向量<br/>(不调embedding)"]
    end

    subgraph 质量反馈闭环["⚖️ 质量反馈闭环"]
        direction TB
        FB["用户点赞/点踩"] --> Adjust["feedbackScore ±0.05<br/>[0.0, 1.0]"]
        Adjust --> Recalc["重新计算qualityScore<br/>→ 更新向量库"]
        Recall["每次检索召回"] --> Count["recallCount++<br/>(log归一化影响质量分)"]
    end

    style Skip fill:#e0e0e0,stroke:#9e9e9e
    style Skip2 fill:#e0e0e0,stroke:#9e9e9e
    style Warn fill:#fff3e0,stroke:#f57c00
```

**MarkdownChunker语义分块策略**（不按固定长度切，优先保留语义边界）：

1. 按`^## ` 二级标题分割为H2段
2. 每个H2段按`^### ` 三级标题分割为H3段
3. 每个H3段按`\n\n`分割为段落
4. 段落token计数：≤maxTokens(512)累积，>maxTokens按句子边界`[。！？.!?]`拆分
5. 累积达targetTokens(256)输出chunk
6. 相邻chunk保留50token重叠，保证语义连贯
7. 每个chunk携带`headingPath`（如"平台指南 > 发帖 > 如何上传图片"），检索时提供上下文

**四维质量评分**（`FourDimensionQualityScorer`）：

| 维度   | 权重  | 归一化方法                                           |
| ---- | --- | ----------------------------------------------- |
| 召回频次 | 0.4 | log归一化(100次封顶)：0次=0, 1次≈0.15, 10次≈0.5, 100+=1.0 |
| 用户反馈 | 0.3 | feedbackScore直接使用(点赞+0.05/点踩-0.05)              |
| 新鲜度  | 0.2 | 30天内=1.0，90天后=0.0，线性衰减                          |
| 完整度  | 0.1 | 分块数：0块=0, 1块=0.3, 3块=0.7, 5+=1.0                |

**知识库版本管理**（`KnowledgeVersionService`）：

- 每次更新前对当前版本创建**完整快照**（存`knowledge_article_versions`表，非diff）
- 版本号SemVer递增（v1.0.0→v1.0.1），回滚也产生新版本号（不倒退）
- 回滚流程：快照当前版本(ROLLBACK原因)→用目标版本内容覆盖主表→patch+1→触发重新向量化
- 点赞/点踩实时调整feedbackScore并同步更新PG向量库中的quality\_score

**Embedding客户端弹性**（`EmbeddingClient`）：

- 批量自动分片：超过32条自动切分为多个批次并行调用（Mono.zip合并）
- 熔断打开时返回空向量而非抛出异常，让上层降级到关键词检索
- 单批失败返回空列表不影响其他批次（部分失败容忍）
- 结果按index排序保证顺序与输入一致

***

### 3.7 上下文工程：L0-L5分层装载+Token预算+三级降级+快照

上下文工程回答一个核心问题：**在8000 token的输入预算内，给LLM看什么最有价值？**

```mermaid
flowchart TB
    subgraph Token预算["单轮输入预算: ~8000 tokens"]
        direction TB
        L0["L0: System Prompt（永驻，不截断）
━━━━━━━━━━━━━━━━━━━━━━━━
包含：L1平台级+L2任务级+L3 Few-shot
     +<context>检索结果+记忆+L4护栏
预算：~4000-5000 tokens
来源：PromptAssembler输出"]

        L1["L1: 用户画像（可裁剪）
━━━━━━━━━━━━━━━━━━━━━━━━
包含：用户偏好/专业/兴趣等
预算：~300 tokens
来源：LongTermMemoryService.loadUserProfile"]

        L2["L2: 工具定义（Function Calling时启用）
━━━━━━━━━━━━━━━━━━━━━━━━
<available_tools>XML标签
当前MVP阶段未启用"]

        L4["L4: 对话历史（可压缩）
━━━━━━━━━━━━━━━━━━━━━━━━
包含：虚拟轮次(摘要/槽位/Pin) + 最近N轮原文
预算：~1500-4000 tokens（按意图动态分配）
来源：Redis短期记忆 → DB降级"]

        L5["L5: 当前用户输入（永驻，不截断）
━━━━━━━━━━━━━━━━━━━━━━━━
包含：<user_query>标签包裹的当前消息
预算：~700 tokens
来源：用户直接输入"]
    end

    L0 --> Assemble["ContextAssembler.assemble()"]
    L1 --> Assemble
    L2 --> Assemble
    L4 --> Assemble
    L5 --> Assemble

    Assemble --> Count["Token计数(jtokkit cl100k_base)"]
    Count --> Check{总input ≤ 预算?}

    Check -->|是| Send["✅ 发给LLM"]
    Check -->|否| Degrade["⚠️ 三级降级链"]

    Degrade --> D1["降级1: L4截断→最近2轮
（保留指代消解最少上下文）"]
    D1 --> Check2{还超?}
    Check2 -->|是| D2["降级2: 丢弃L1用户画像
（保住核心对话能力）"]
    D2 --> Check3{还超?}
    Check3 -->|是| D3["降级3: 硬兜底→L4截断→1轮
（最后防线，truncated=true）"]
    Check2 -->|否| Send
    Check3 -->|否| Send
    D3 --> Send

    Assemble --> Snapshot["📸 ContextSnapshot
fire-and-forget异步写入
agent_context_snapshots表
便于答错复盘回放"]

    style L0 fill:#e3f2fd,stroke:#1976d2
    style L5 fill:#fff3e0,stroke:#f57c00
    style Degrade fill:#fce4ec,stroke:#c62828
    style Snapshot fill:#f3e5f5,stroke:#7b1fa2
```

**对话历史的特殊结构——虚拟轮次+原文轮次**：L4层不是简单的最近N轮原文，而是压缩产物和原文以统一的AgentTurn格式注入：

```
history列表（时间正序）：
┌────────────────────────────────────────┐
│ user: "[历史对话摘要]"                    │  ← 虚拟轮次：滚动摘要（Redis summary）
│ assistant: "用户在询问北大考研资料..."     │
├────────────────────────────────────────┤
│ user: "[已确认约束]"                      │  ← 虚拟轮次：已确认槽位
│ assistant: "{school:北大, category:资料}" │
├────────────────────────────────────────┤
│ user: "[用户偏好]"                        │  ← 虚拟轮次：Pin消息
│ assistant: "偏好PDF格式..."               │
├────────────────────────────────────────┤
│ user: "有没有北大的考研资料？"             │  ← 真实轮次1（原文）
│ assistant: "为你找到以下北大考研资料..."   │
├────────────────────────────────────────┤
│ user: "那个带下载链接的"                  │  ← 真实轮次2（原文，当前CLARIFY的前一轮）
│ assistant: "..."                          │
└────────────────────────────────────────┘
```

**XML标签分层规范**：最终发给LLM的messages列表用XML标签明确区分各层，避免内容混淆：

- `<system_rules>`...`</system_rules>`：L0（含平台级+任务级+Few-shot+context+护栏）
- `<user_profile>`...`</user_profile>`：L1用户画像
- `<available_tools>`...`</available_tools>`：L2工具定义（预留）
- `<user_query>`...`</user_query>`：L5当前输入（包裹在user消息中）
- L4历史保持标准的user/assistant交替格式

**上下文快照**（`ContextSnapshotService`）：每次`ContextAssembler`组装完后，fire-and-forget异步写入`agent_context_snapshots`表，记录完整的messages列表、各层token分布、是否截断及截断原因。这是**答错复盘的唯一证据**——当用户反馈"AI答错了"时，可以精确还原当时发给LLM的全部上下文。写入失败仅log告警不阻塞主流程（≈2ms延迟可接受）。

***

### 3.8 记忆系统：短期Redis+长期MySQL/PG双轨 + 冲突解决

记忆系统分**短期记忆**（Redis，会话级）和**长期记忆**（MySQL+pgvector，用户级），两者独立运作、协同提供上下文。

```mermaid
flowchart TB
    subgraph 短期记忆["短期记忆 (Redis, TTL 2h滑动续期)"]
        direction TB
        ST1["messages (List, max=20)
每轮user+assistant消息
含turnId/role/content/tokens/ts
RPUSH+LTRIM自动裁剪"]
        ST2["summary (String)
滚动摘要：旧轮次压缩为≤300字"]
        ST3["slots (Hash)
已确认约束：school/category/topic
Hash合并写"]
        ST4["pinned (List, max=5)
Pin消息：用户重要偏好/约束
永不压缩"]
        ST5["meta (Hash)
turn_count/last_active_at
/current_intent/intent_history"]
    end

    subgraph 长期记忆["长期记忆 (MySQL user_memory + PG memory_vectors)"]
        direction TB
        LT1["用户画像记忆
专业/偏好格式/偏好语言/兴趣分类"]
        LT2["相关记忆（本轮检索）
向量+关键词双路RRF检索
与当前query语义相关的历史记忆"]
        LT3["记忆生命周期
extractMemories(会话归档) → ConflictResolver
→ UPSERT(confidence累加) → 周衰减 → 软删除"]
    end

    subgraph 冲突解决["ConflictResolver 冲突检测"]
        direction TB
        CR1{"新记忆 vs 已有同key记忆"}
        CR1 -->|EXPLICIT vs INFERRED| CR2["✅ EXPLICIT优先<br/>隐式标记conflict_flag=1<br/>置信度×0.5"]
        CR1 -->|EXPLICIT vs EXPLICIT| CR3["✅ 新时间戳优先<br/>旧标记冲突+写入history审计"]
        CR1 -->|INFERRED vs INFERRED| CR4["✅ 高置信度优先<br/>低置信度×0.8降权"]
        CR0["Jaccard相似度<0.3<br/>判定为值冲突"]
        CR0 --> CR1
    end

    subgraph 记忆流向["记忆读写时序"]
        direction TB
        Read["📖 对话开始时读：
1. loadProfileMemories → L1画像
2. retrieveRelevantMemories → <context>记忆
3. Redis miss自动回源MySQL → 回写Redis"]
        Write["✍️ 回答完成后写：
1. appendMessage(RPUSH+LTRIM)
2. incrementTurnCount + renewTTL
3. 消息>10 → triggerCompression"]
        Archive["📦 会话归档时：
1. extractMemories(LLM从摘要抽取)
2. ConflictResolver.resolveOnInsert
3. UPSERT user_memory + upsert向量"]
        Decay["⏰ 每周日02:00定时：
decayMemories → EXPLICIT×0.97/BEHAVIOR×0.9/TASK×0.7
近7天访问≥3次→衰减减半
confidence<0.2软删除(30天回收站)
TASK类型4周自动删除"]
    end

    ST1 -->|消息>10触发| Compress["🔄 上下文压缩
三合一LLM调用：
• 更新滚动摘要(≤300字)
• 抽取/合并槽位
• Pin重要消息(最多5条)
→ LTRIM保留最近5条
失败降级:直接LTRIM"]
    Compress --> ST2
    Compress --> ST3
    Compress --> ST4

    Write --> ST1
    Write --> ST5
    Archive --> LT1
    Decay --> LT1
    Archive --> CR1

    LT2 -->|注入<context>| CTX["SystemPrompt的<context>区"]
    LT1 -->|注入<user_profile>| UP["L1用户画像层"]
    ST2 -->|虚拟轮次| HIS["L4对话历史层"]
    ST3 -->|虚拟轮次| HIS
    ST4 -->|虚拟轮次| HIS
    ST1 -->|最近N轮原文| HIS

    style 短期记忆 fill:#e0f7fa,stroke:#00838f
    style 长期记忆 fill:#fce4ec,stroke:#c62828
    style Compress fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    style 冲突解决 fill:#f3e5f5,stroke:#7b1fa2
```

**长期记忆四象限分类**：

| 类型         | source   | 周衰减率         | 说明        | 示例        |
| ---------- | -------- | ------------ | --------- | --------- |
| PREFERENCE | EXPLICIT | 0.03 (×0.97) | 用户明确说的偏好  | "我喜欢PDF"  |
| FACT       | EXPLICIT | 0.03         | 用户明确声明的事实 | "我是计算机专业" |
| BEHAVIOR   | INFERRED | 0.1 (×0.9)   | 行为推断      | 常访问"考研"分类 |
| TASK       | INFERRED | 0.3 (×0.7)   | 当前任务，4周删除 | 在准备期末复习   |

**记忆UPSERT逻辑**：同一`user_id + type + key`已存在时，`confidence = min(1, confidence + 0.1)`；新记忆插入时confidence=1.0。同一偏好被多次提及→置信度累加→更稳定。

**冲突解决策略**（`ConflictResolver`）——显式优先原则：

1. **EXPLICIT vs INFERRED**：用户明确说的永远优先于模型推断的，隐式记忆标记冲突并降置信度
2. **EXPLICIT vs EXPLICIT**：两个显式记忆冲突，时间新的胜出，旧的标记冲突
3. **INFERRED vs INFERRED**：两个隐式记忆冲突，高置信度胜出，低置信度降权(×0.8)
4. 冲突判定：Jaccard字符相似度<0.3视为值冲突
5. **不删除旧记忆**：仅标记conflict\_flag+降置信度，保留完整审计轨迹（`user_memory_history`表）

**衰减与增强机制**：

- EXPLICIT明确记忆周衰减仅0.03（基本不遗忘），TASK类型4周未更新自动删除
- 近7天访问≥3次的记忆衰减率减半（用进废退）
- confidence < 0.2时软删除（30天回收站，非物理删除）

**记忆检索**：记忆也走**向量+关键词双路RRF融合**，和帖子/知识库检索同构。检索结果分两种用法：

- **Profile Memories**：Top-K高置信+近期使用的记忆，格式化为`[用户画像]`文本注入L1层
- **Relevant Memories**：与当前query语义相关的记忆，混入<context>区，标注来源为"用户记忆"，带置信度和source标签

***

### 3.9 上下文压缩：三级渐进式压缩（三合一LLM降本60%）

当Redis中messages List长度超过10条时触发压缩，避免历史无限增长撑爆上下文窗口：

````mermaid
flowchart LR
    Trigger{"触发条件<br/>messages > 10<br/>或 L4历史>2500tok"} --> Load["加载：
• oldSummary（旧摘要）
• existingSlots（已有槽位）
• toCompress（前N条旧消息，保留最近5条）
• 单条消息截断至500字防prompt爆炸"]
    Load --> Build["构建压缩Prompt<br/>规定输出JSON格式<br/>≤300字摘要限制"]
    Build --> LLM["调用DeepSeek非流式
三合一压缩(一次调用输出三段JSON)：
1. new_summary
2. slot_updates
3. pinned_messages"]
    LLM --> Parse["解析JSON<br/>支持```json```包裹格式<br/>失败重试1次"]
    Parse --> Write["写回Redis：
• updateSummary(+MySQL异步持久化)
• updateSlots(Hash合并)
• pinMessage(RPUSH, 最多5条)
• LTRIM messages 保留最近5条"]
    LLM -->|LLM失败/解析失败| Fallback["⚠️ 降级：
直接LTRIM保留最近4条
不生成摘要
fallback=true标记"]
    Parse -->|摘要>300字| Trunc["截断到300字"]
    Trunc --> Write
    style LLM fill:#e3f2fd,stroke:#1976d2
    style Fallback fill:#fce4ec,stroke:#c62828
````

压缩结果是三种产出物的组合：

- **滚动摘要（L1）**：把旧对话+旧摘要压缩为≤300字新摘要，作为虚拟轮次放在history最前面
- **槽位更新（L2）**：从对话中提取/合并已确认的约束条件（如用户说"我要北大的"→school=北大）
- **Pin消息（L3）**：识别用户重要偏好声明（如"我只要PDF"），钉住不被后续压缩，最多5条

**三合一设计的降本思路**：如果分三次LLM调用（摘要/槽位/Pin各一次），成本是3×input+3×output。三合一一次prompt输出三段JSON，成本降低约60%。

***

## 四、快路径 vs 慢路径对比

| 维度         | 快路径 ⚡                         | 慢路径 🔍                          |
| ---------- | ----------------------------- | ------------------------------- |
| 触发意图       | OUT\_OF\_SCOPE, NAVIGATE      | HOW\_TO, SEARCH, CLARIFY        |
| LLM调用      | 0次                            | 1次（生成）+ 可选1次（压缩/记忆抽取）           |
| RAG检索      | 不检索                           | 四路并行+RRF融合                      |
| Prompt版本查询 | 不查（硬编码模板）                     | 查Redis→DB→降级                    |
| 上下文组装      | 不组装                           | L0-L5分层+预算+降级                   |
| 快照写入       | 不写                            | fire-and-forget写ContextSnapshot |
| 典型延迟       | <50ms（规则匹配+模板）                | 2-6s（Embedding+检索+LLM流式）        |
| 输出方式       | SSE: session+delta(+navigate) | SSE: session+delta+refs         |
| Token消耗    | 0（不调LLM）                      | \~6000-8000 input tokens        |
| 流量占比       | \~25%（闲聊/拒绝/跳转）               | \~75%（问答/搜索/澄清）                 |
| 典型场景       | "你是谁""帮我发帖""去我的收藏"            | "怎么发帖""有没有高数资料"                 |

***

## 五、SSE事件流协议与前端交互

前后端通过Server-Sent Events通信，建立在WebFlux响应式栈之上：

```mermaid
sequenceDiagram
    participant FE as 前端AgentPage
    participant BE as AgentController

    Note over FE,BE: 连接建立（fetch + ReadableStream）
    BE-->>FE: event: session\ndata: {"sessionId":"xxx"}

    alt 快路径OUT_OF_SCOPE
        BE-->>FE: event: delta\ndata: 你好！我是CampusShare AI助手...
    else 快路径NAVIGATE
        BE-->>FE: event: delta\ndata: 正在为你跳转...
        BE-->>FE: event: navigate\ndata: {"route":"/profile/liked","label":"我的点赞"}
    else 慢路径
        loop 流式生成
            BE-->>FE: event: delta\ndata: 为
            BE-->>FE: event: delta\ndata: 你
            BE-->>FE: event: delta\ndata: 找到...
        end
        BE-->>FE: event: refs\ndata: [{"index":1,"id":"xxx","type":"POST","title":"...","url":"/post/xxx"}]
    end

    BE-->>FE: event: done\ndata: [DONE]
    Note over FE: 输入框重新聚焦(handleSend后setTimeout focus)

    alt 异常
        BE-->>FE: event: error\ndata: {"code":"RATE_LIMITED","message":"请求过于频繁"}
    end
```

| 事件         | data格式                            | 时机             | 前端处理                           |
| ---------- | --------------------------------- | -------------- | ------------------------------ |
| `session`  | `{"sessionId":"..."}`             | 最先发送           | 记录当前会话ID，后续消息携带                |
| `delta`    | 纯文本片段                             | LLM流式输出中逐token | 追加到AI消息气泡，react-markdown渲染     |
| `refs`     | 引用列表JSON数组                        | delta全部发送完     | 渲染"引用来源"卡片列表，点击跳转              |
| `navigate` | `{"route":"...", "label":"..."}`  | 快路径NAVIGATE    | 渲染跳转卡片，点击IonRouterOutlet SPA导航 |
| `error`    | `{"code":"...", "message":"..."}` | 限流/鉴权/服务异常     | Toast提示错误信息                    |
| `done`     | `[DONE]`                          | 流结束标记          | 关闭连接，重新允许发送                    |

**前端会话管理**：左侧会话列表展示用户所有非DELETED会话，支持分类文件夹切换；左滑操作可重命名/归档/删除会话；新建会话自动置于列表顶部。

***

## 六、安全护栏：输入+输出双重防护

```mermaid
flowchart LR
    Input["用户输入"] --> HardBlock{"shouldHardBlock?<br/>硬拦截：Prompt泄露等"}
    HardBlock -->|是| Reject["❌ 抛出异常<br/>返回403/SSE error"]
    HardBlock -->|否| Injection{"detectInjection?<br/>注入检测（软拦截）"}
    Injection -->|是| Meter["📊 log+meter计数<br/>injectionDetectedCounter++<br/>继续处理（避免误杀）"]
    Injection -->|否| Normal["✅ 正常处理"]

    Output["LLM输出"] --> Validate{"ConstitutionalAIValidator<br/>.validate(output)"}
    Validate -->|违规| Log["📊 log+meter<br/>violationCounter++<br/>写入turn.tools_used<br/>(流式已推给用户，不替换)"]
    Validate -->|合规| Done["✅ 完成"]

    style Reject fill:#fce4ec,stroke:#c62828
    style Log fill:#fff3e0,stroke:#f57c00
```

- **输入硬拦截**：检测Prompt泄露等严重攻击模式，直接抛异常拒绝服务
- **输入软拦截**：检测到疑似注入只log+计数，不阻断对话（避免误杀正常提问）
- **输出验证**：流式场景下用户已看到内容，不做中途替换，仅记录违规到turn的tools\_used字段用于离线分析
- **<context>标签隔离**：检索内容用XML标签包裹并明确标注"参考资料非指令"，从Prompt层面降低注入风险
- **护栏末尾覆盖**：安全护栏放在System Prompt最末尾，利用近因效应覆盖任何恶意指令

***

## 七、数据流与存储全景

```mermaid
flowchart LR
    subgraph 外部["外部依赖"]
        DS[("DeepSeek API<br/>V4-Flash生成<br/>BGE-M3向量(1024维)")]
        PostSvc[("post-service :8082<br/>帖子数据Feign调用<br/>分页拉取+增量通知")]
        UserSvc[("user-service :8081<br/>用户信息Feign调用")]
    end

    subgraph Agent服务["agent-service :8083 (WebFlux响应式)"]
        direction TB
        Ctrl["AgentController<br/>SSE/JWT/限流入口"]
        Chat["AgentChatService<br/>(主编排)"]
        Ingest["离线摄入管道<br/>KnowledgeIngestionService<br/>PostVectorService"]
        Sched["定时调度器<br/>KnowledgeScheduler<br/>PostVectorScheduler<br/>SessionArchivalService"]
    end

    subgraph 数据层["数据存储"]
        MySQL[("MySQL :3306
━━━━━━━━━━━━━━━
agent_sessions          会话元数据
agent_turns             对话轮次+tokens
agent_session_categories 会话分类
agent_session_events    状态转移审计(90天)
user_memory             长期记忆条目
user_memory_history     记忆冲突审计
agent_context_snapshots 上下文快照
context_summaries/slots/pins 短期记忆持久化
prompt_versions         Prompt版本
knowledge_articles      知识库文档元数据
knowledge_article_versions 知识库版本快照")]

        PG[("PostgreSQL+pgvector :5432
━━━━━━━━━━━━━━━
knowledge_vectors       知识库chunk向量(HNSW)
post_vectors            帖子向量(HNSW+质量分)
memory_vectors          用户记忆向量(HNSW)")]

        Redis[("Redis :6379
━━━━━━━━━━━━━━━
messages/summary/slots/pinned/meta  (5key/session, TTL2h)
retrieval缓存           (5min TTL)
rate_limit              (1min窗口)
prompt:current_version  (版本号缓存)
prompt:gray_ratio       (灰度比例)
session status          (CAS状态)")]
    end

    subgraph 监控["可观测性"]
        Metrics["Micrometer → Prometheus → Grafana
━━━━━━━━━━━━━━━
agent.intent.*          意图分类全链路
agent.knowledge.*       知识库摄入/检索
prompt.version.fallback 版本降级
(未来)agent.llm.*       LLM耗时/token/错误"]
    end

    Ctrl --> Chat
    Chat -->|SSE流式| DS
    Chat -->|Embedding| DS
    Chat --> MySQL
    Chat --> PG
    Chat --> Redis
    Chat -->|Feign/WebClient| PostSvc
    Chat -->|Feign| UserSvc
    Ingest -->|分页拉取| PostSvc
    Ingest -->|embed| DS
    Ingest --> MySQL
    Ingest --> PG
    Sched -->|定时触发| Chat
    Sched -->|定时触发| Ingest
    Chat -.-> Metrics
    Ingest -.-> Metrics
```

***

## 八、降级策略全景：故障时的优雅退化

系统设计了多层降级，确保任何单点故障都不会导致服务完全不可用：

| 故障点                | 降级策略                         | 用户体验影响                |
| ------------------ | ---------------------------- | --------------------- |
| Redis不可用           | 回源MySQL读短期记忆；限流fail-open放行   | 历史可能丢失，但对话可继续         |
| DeepSeek LLM熔断     | 熔断器30s后自动半开试探；意图分类返回SEARCH兜底 | 慢路径暂时不可用（快路径仍正常）      |
| Embedding服务熔断      | 返回空向量；向量检索路返回空，关键词检索路仍工作     | 检索精度下降，但仍能关键词匹配       |
| Post服务不可用          | post-sync熔断器打开；帖子检索路返回空      | 仅知识库检索可用（HOW\_TO不受影响） |
| MySQL不可用           | Redis短期记忆仍可读写；新会话创建失败        | 进行中的会话可继续，新会话受阻       |
| PromptVersion DB故障 | 降级到PromptConstants硬编码常量      | 使用稳定的默认Prompt         |
| 上下文压缩LLM失败         | 直接LTRIM保留最近4条，不生成摘要          | 失去摘要连续性，但对话可继续        |
| 上下文快照写入失败          | 仅log告警，不阻塞主流程                | 无影响（仅影响复盘能力）          |
| 重复检测失败             | fail-open返回UNIQUE，允许文档摄入     | 可能产生重复文档              |

**降级核心原则**：

- **Fail-Open优先**：限流、重复检测、快照等非核心路径异常时放行不阻断
- **功能降级而非服务不可用**：向量检索降级到关键词、压缩降级到截断、记忆降级到无记忆
- **自动恢复**：熔断器半开状态自动试探，无需人工干预
- **可观测**：所有降级通过Micrometer Counter记录，可在Grafana看板发现

***

## 九、关键代码索引

| 模块                | 核心文件                                                                                                                                                                           | 主要职责                                 |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------ |
| **入口/Controller** | [AgentController.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/controller/AgentController.java)                      | SSE端点、JWT鉴权、限流前置、事件封装                |
| **限流**            | [AgentRateLimiter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentRateLimiter.java)                       | Redis固定窗口限流、fail-open降级              |
| **弹性配置**          | [ResilienceConfig.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/ResilienceConfig.java)                        | 4个熔断器+全局RateLimiter配置                |
| **主编排**           | [AgentChatService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java)                       | 全链路编排：会话→意图→分流→RAG→Prompt→上下文→流式→持久化 |
| **会话管理**          | [AgentSessionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentSessionService.java)                 | 会话CRUD、权限验证、轮次查询、引用列表                |
| **状态机**           | [SessionStateMachine.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionStateMachine.java)                 | 8状态CAS转移、Redis+DB双写、事件审计             |
| **会话归档**          | [SessionArchivalService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionArchivalService.java)           | 僵尸检测(30min)、归档→记忆抽取→延迟清理             |
| **会话分类**          | [AgentSessionCategoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentSessionCategoryService.java) | 文件夹分类CRUD、删除时软转移                     |
| **规则短路**          | [RuleShortCircuitFilter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RuleShortCircuitFilter.java)           | Layer1规则匹配，<5ms过滤闲聊/写操作/指代词/个人列表     |
| **LLM分类**         | [IntentClassifier.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java)                       | Layer2 LLM结构化意图分类+查询改写+槽位提取          |
| **Embedding兜底**   | [EmbeddingIntentFallback.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/EmbeddingIntentFallback.java)         | Layer3语义相似度兜底分类                      |
| **意图缓存**          | [IntentCacheService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentCacheService.java)                   | 相同query意图结果缓存                        |
| **意图路由**          | [IntentRouter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java)                               | 快/慢路径分流+模板回复选择                       |
| **RAG检索**         | [RetrievalService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java)                       | 四路并行检索+RRF融合+后处理链+缓存                 |
| **Prompt装配**      | [PromptAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java)                          | L1-L4五层System Prompt拼接+检索结果格式化       |
| **Prompt版本管理**    | [PromptVersionManager.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java)                | 版本灰度切换、Redis缓存、三级降级                  |
| **Prompt常量**      | [PromptConstants.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptConstants.java)                          | 各意图任务级Prompt+安全护栏+Few-shot硬编码默认值     |
| **上下文组装**         | [ContextAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java)                       | L0-L5分层装载+Token预算+三级降级链              |
| **上下文快照**         | [ContextSnapshotService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextSnapshotService.java)           | fire-and-forget异步写入快照，答错回放证据         |
| **短期记忆**          | [ConversationMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java)     | Redis 5Key读写、TTL续期、MySQL回源回写、压缩触发    |
| **上下文压缩**         | [ContextCompressionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextCompressionService.java)     | 三合一LLM压缩（摘要+槽位+Pin）、失败降级             |
| **长期记忆**          | [LongTermMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java)             | 记忆抽取/UPSERT/衰减/向量化                   |
| **冲突解决**          | [ConflictResolver.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConflictResolver.java)                       | EXPLICIT优先冲突解决、Jaccard判定、审计记录        |
| **记忆检索**          | [MemoryRetrievalService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/MemoryRetrievalService.java)           | 用户画像加载+相关记忆双路RRF检索                   |
| **知识库摄入**         | [KnowledgeIngestionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java)     | MD5增量→分块→embedding→去重→质量评分→版本→写入     |
| **Markdown分块**    | [MarkdownChunker.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/MarkdownChunker.java)                         | H2→H3→段落→句子语义分块、50token重叠、标题路径       |
| **重复检测**          | [ThresholdDuplicateDetector.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ThresholdDuplicateDetector.java)   | 双阈值重复检测(0.95/0.85)、fail-open         |
| **质量评分**          | [FourDimensionQualityScorer.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/FourDimensionQualityScorer.java)   | 召回+反馈+新鲜+完整四维加权评分                    |
| **知识版本**          | [KnowledgeVersionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeVersionService.java)         | 完整快照、SemVer、回滚产生新版本、反馈调整质量分          |
| **帖子向量同步**        | [PostVectorService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/PostVectorService.java)                     | 增量+全量分页同步、独立熔断                       |
| **LLM客户端**        | [DeepSeekClient.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/DeepSeekClient.java)                               | DeepSeek API封装（流式+非流式+熔断+重试）         |
| **Embedding客户端**  | [EmbeddingClient.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/EmbeddingClient.java)                             | BGE-M3批量向量化、自动分片32、熔断空向量降级           |
| **安全护栏**          | [ConstitutionalAIValidator.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java)      | 输入注入检测+输出违规验证                        |
| **学校工具**          | [SchoolNameUtils.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/util/SchoolNameUtils.java)                            | 12所高校ID映射、别名归一化、query正则提取            |
| **意图指标**          | [IntentMetricsConfig.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/IntentMetricsConfig.java)                  | 意图分类Micrometer埋点(计数/耗时/缓存/路由)        |
| **知识指标**          | [KnowledgeMetricsConfig.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/KnowledgeMetricsConfig.java)            | 知识库摄入/分块/embedding/检索/去重指标           |
| **知识调度**          | [KnowledgeScheduler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/KnowledgeScheduler.java)                    | 启动30s+每小时触发ingestAll                 |
| **帖子调度**          | [PostVectorScheduler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/PostVectorScheduler.java)                  | 启动60s+每5分钟触发syncAll兜底                |
| **前端页面**          | [AgentPage.tsx](file:///e:/workspace_work/CampusShare/frontend/src/pages/AgentPage.tsx)                                                                                        | 聊天UI+SSE消费+流式渲染+引用卡片+输入框重聚焦          |

***

*本文档描述的是2026-07-10代码现状。架构设计文档见* *[docs/agent-assistant/architecture-overview.md](file:///e:/workspace_work/CampusShare/docs/agent-assistant/architecture-overview.md)（包含演进路线图），各模块详细ADR见* *`docs/agent-assistant/`* *下93份设计文档。*
