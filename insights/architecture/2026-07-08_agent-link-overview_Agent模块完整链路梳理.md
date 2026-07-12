# Agent 模块完整链路梳理（SystemPrompt / RAG / 意图识别 / 上下文工程）

- **日期：** 2026-07-08
- **类型：** 架构梳理 / 链路全景
- **业务背景：** 主动需求型（技术驱动） — 为了加深对 agent 模块的理解，方便后续迭代和排障，需要一份图文并茂的链路梳理文档
  - **Why now：** agent 模块已完成 SystemPrompt 工程、意图识别、RAG 检索、上下文工程四大核心模块的开发，代码量较大，新人/上下文压缩后难以快速掌握全貌
  - **What if not done：** 后续排障效率低，新功能开发容易破坏现有设计，模块间边界模糊
  - **成功标志：** 一张图看懂 agent 全链路，各模块职责清晰，数据流方向明确

---

## 一、整体链路全景图

> 这是最重要的一张图。从用户发送消息到收到回复的完整数据流。

```mermaid
flowchart LR
    User[用户发送消息] --> AC[AgentController]
    AC --> CS[AgentChatService]

    CS --> IR[意图识别三层漏斗]
    IR --> RT{意图路由}

    RT -->|OUT_OF_SCOPE / NAVIGATE| FAST[快路径:模板回复]
    FAST --> SSE1[SSE 流式输出]

    RT -->|HOW_TO / SEARCH / CLARIFY| RAG[RAG 混合检索]
    RAG --> PA[SystemPrompt 装配]
    PA --> MEM[记忆加载]
    MEM --> CA[上下文工程]
    CA --> DS[DeepSeek 流式调用]

    DS --> SSE2[SSE 流式输出]

    SSE2 --> POST[后处理]
    POST --> RED[Redis 短期记忆]
    POST --> MYSQL[MySQL 持久化]
```

**核心编排器：** [AgentChatService.prepareContext()](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java#L164-L240) — 8 步串行编排

---

## 二、意图识别：三层漏斗架构

### 2.1 漏斗结构图

```mermaid
flowchart TD
    Q[用户 query] --> L1[Layer 1:规则短路]
    L1 -->|命中| R1[返回 IntentResult - RULE]
    L1 -->|未命中| L2[Layer 2:LLM 分类]

    L2 --> CACHE[Redis 缓存检查]
    CACHE -->|命中| R2[返回缓存结果]
    CACHE -->|未命中| LLM[DeepSeek 调用:分类+改写+槽位]

    LLM -->|成功| R3[返回 IntentResult - LLM]
    LLM -->|超时/失败| L3[Layer 3:Embedding 兜底]

    L3 -->|成功| R4[返回 IntentResult - EMBEDDING]
    L3 -->|失败| DEF[Default:SEARCH 兜底]

    DEF --> R5[返回 IntentResult - DEFAULT]

    R1 --> OUT[输出统一结构 IntentResult]
    R2 --> OUT
    R3 --> OUT
    R4 --> OUT
    R5 --> OUT
```

### 2.2 规则优先级（Layer 1）

```mermaid
flowchart LR
    Q[query] --> P1[优先级 1: 指代词 - CLARIFY]
    Q --> P2[优先级 2: 写操作 - OUT_OF_SCOPE]
    Q --> P3[优先级 3: 闲聊问候 - OUT_OF_SCOPE]
    Q --> P4[优先级 4: 个人列表 - NAVIGATE]
```

### 2.3 意图体系（5 大 + 14 子）

```mermaid
flowchart TB
    ROOT[Intent 5 大意图]

    ROOT --> H[HOW_TO 操作指引]
    ROOT --> S[SEARCH 内容检索]
    ROOT --> N[NAVIGATE 页面导航]
    ROOT --> C[CLARIFY 多轮澄清]
    ROOT --> O[OUT_OF_SCOPE 超范围]

    H --> H1[feature_help 功能帮助]
    H --> H2[rule_explain 规则解释]

    S --> S1[resource 资源检索]
    S --> S2[discussion 讨论检索]
    S --> S3[content_qa 内容问答]

    N --> N1[feature_loc 功能定位]
    N --> N2[section_loc 板块定位]
    N --> N3[my_list 我的列表]

    C --> C1[coreference 指代消解]
    C --> C2[refine 修正细化]
    C --> C3[followup 追问深入]

    O --> O1[chitchat 闲聊问候]
    O --> O2[open_domain 开放域]
    O --> O3[write_action 写操作]
    O --> O4[sensitive 敏感内容]
```

**核心文件：**
- [RuleShortCircuitFilter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RuleShortCircuitFilter.java)
- [IntentClassifier.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java)
- [Intent.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/enums/Intent.java)
- [IntentResult.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/dto/IntentResult.java)

---

## 三、RAG 检索：四路混合检索 + RRF 融合

### 3.1 检索流程图

```mermaid
flowchart TD
    Q[query 用 rewrittenQuery] --> CONFIG[意图驱动配置选择]
    CONFIG --> CACHE[缓存检查 非 CLARIFY 意图]
    CACHE -->|命中| CACHE_OUT[返回缓存结果]
    CACHE -->|未命中| EMBED[query 转 embedding 向量]

    EMBED --> KV[知识库向量检索]
    EMBED --> KK[知识库关键词检索]
    EMBED --> PV[帖子向量检索 带 slots 过滤]
    EMBED --> PK[帖子关键词检索 按配置启用]
    EMBED --> CLARIFY[CLARIFY专属 上一轮结果降权]

    KV --> AGG[Chunk 转 Article 聚合]
    KK --> AGG
    PV --> AGG
    PK --> AGG
    CLARIFY --> AGG

    AGG --> RRF[RRF 融合排序]
    RRF --> QUALITY[质量评分加权]
    QUALITY --> DEDUP[跨源去重]
    DEDUP --> TOKEN[Token 预算截断]
    TOKEN --> OUT[RetrievalResult 列表]

    OUT --> ASYNC[异步操作]
    ASYNC --> RECALL[召回计数累加]
    ASYNC --> CACHE_WRITE[缓存写入 非 CLARIFY]
```

### 3.2 意图驱动的检索策略对比

| 意图 | 知识库向量 | 知识库关键词 | 帖子向量 | 帖子关键词 | 策略特点 |
|------|-----------|-------------|---------|-----------|---------|
| HOW_TO | 8 | 5 | 2 | 0 | 知识库为主，功能说明靠知识库 |
| SEARCH/resource | 2 | 2 | 8 | 5 | 帖子为主，找资源帖 |
| SEARCH/content_qa | 8 | 5 | 3 | 2 | 知识库为主，内容问答 |
| CLARIFY | 5 | 3 | 5 | 3 | 均衡，澄清追问靠上下文 |

### 3.3 RRF 融合示意

> 假设有 3 路检索结果，A 在第 1 路排第 1，第 2 路排第 3，第 3 路排第 2

```mermaid
flowchart LR
    A1[A rank=1]
    B1[B rank=2]
    C1[C rank=3]

    D2[D rank=1]
    A2[A rank=3]
    E2[E rank=2]

    F3[F rank=1]
    A3[A rank=2]
    G3[G rank=3]

    A1 --> RRF
    B1 --> RRF
    C1 --> RRF
    D2 --> RRF
    A2 --> RRF
    E2 --> RRF
    F3 --> RRF
    A3 --> RRF
    G3 --> RRF

    RRF[RRF 融合 k=60] --> FINAL[最终排序]
```

**核心文件：**
- [RetrievalService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java)
- [KnowledgeVectorStore.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/store/KnowledgeVectorStore.java)
- [RetrievalConfig.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/dto/RetrievalConfig.java)
- [RetrievalResult.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/dto/RetrievalResult.java)

---

## 四、SystemPrompt 工程：六要素分层装配

### 4.1 六要素分层结构图

```mermaid
flowchart TB
    L1[L1 平台级 PLATFORM_PROMPT - 角色定义 + 输出格式]
    L2[L2 任务级 HOW_TO / SEARCH / CLARIFY - 按意图切换]
    L3[L3 Few-shot 示例 - 3 条示例覆盖三大意图]
    CTX[检索结果用 context 标签包裹 - 防隐式注入]
    L4[L4 安全护栏 GUARDRAIL_PROMPT - Constitutional AI 5 条规则]

    L1 --> L2
    L2 --> L3
    L3 --> CTX
    CTX --> L4

    NOTE[装配顺序: 认识自己 - 知道任务 - 看例子 - 看资料 - 防御]
```

### 4.2 版本管理与灰度发布

```mermaid
flowchart TD
    U[用户请求] --> GRAY[灰度判断 按 userId 哈希]
    GRAY -->|命中灰度| CURR[当前版本]
    GRAY -->|未命中| PREV[上一版本]

    CURR --> DB[MySQL prompt_versions 表]
    PREV --> DB
    DB -->|故障降级| CODE[代码常量 PromptConstants]

    REDIS[Redis 存 current_version + gray_ratio]
    SWITCH[秒级切换版本] --> REDIS
    ROLLBACK[秒级回滚] --> REDIS
    GRAY_SET[设置灰度比例] --> REDIS
```

### 4.3 安全护栏双保险

```mermaid
flowchart LR
    USER_INPUT[用户输入] --> HARD[硬拦截 Prompt 泄露检测]
    HARD -->|命中| REJECT[拒绝请求]
    HARD -->|未命中| SOFT[软拦截 注入特征检测]
    SOFT -->|命中| LOG[记录日志 继续调用]
    SOFT -->|未命中| SYS[GUARDRAIL_PROMPT 写入系统提示末尾]

    SYS --> LLM_OUT[LLM 输出]
    LLM_OUT --> VAL[输出后验证]
    VAL -->|违规| VIOLATION[记录违规]
    VAL -->|正常| OK[正常输出]
```

**核心文件：**
- [PromptAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java)
- [PromptConstants.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptConstants.java)
- [PromptVersionManager.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java)
- [ConstitutionalAIValidator.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java)

---

## 五、上下文工程：L0-L5 分层 + Token 预算 + 三级降级

### 5.1 L0-L5 分层结构图

```mermaid
flowchart TB
    L0[L0 System Prompt 1000 tokens - 永驻不可压缩]
    L1[L1 用户画像 300 tokens - 可裁剪]
    L2[L2 工具定义 500 tokens - 永驻不可压缩]
    L3[L3 检索结果 3000 tokens - 高可压缩]
    L4[L4 对话历史 2500 tokens - 中可压缩]
    L5[L5 用户输入 700 tokens - 永驻不可压缩]

    L0 --> L1
    L1 --> L2
    L2 --> L3
    L3 --> L4
    L4 --> L5

    NOTE[总预算 8000 tokens - 硬性下限 L0+L2+L5 约 2200]
```

### 5.2 按意图的动态 Token 分配

| 意图 | L3 检索 | L4 历史 | 设计理念 |
|------|---------|---------|---------|
| HOW_TO | 4000 | 1500 | 知识片段优先，历史可压缩 |
| SEARCH | 3500 | 2000 | 帖子摘要 + 历史指代消解 |
| CLARIFY | 500 | 4000 | 历史是核心，靠上下文澄清 |
| 默认 | 3000 | 2500 | 均衡配置 |

### 5.3 三级降级链

```mermaid
flowchart TD
    START[计算总 Token] --> CHECK[总 Token 大于 8000?]
    CHECK -->|否| OK[正常输出]
    CHECK -->|是| D1[降级 1: L4 截断到最近 2 轮 4 条消息]
    D1 --> CHECK2[还超?]
    CHECK2 -->|否| DONE1[降级 1 完成]
    CHECK2 -->|是| D2[降级 2: 丢弃 L1 用户画像]
    D2 --> CHECK3[还超?]
    CHECK3 -->|否| DONE2[降级 2 完成]
    CHECK3 -->|是| D3[降级 3: 硬上限兜底 L4 截断到最近 1 轮]
    D3 --> DONE3[降级 3 完成]
    DONE1 --> OUT[truncated=true]
    DONE2 --> OUT
    DONE3 --> OUT
```

### 5.4 短期记忆 Redis 5 Key 结构

```mermaid
flowchart TD
    META[meta Hash - user_id / status / turn_count]
    MESSAGES[messages List - 最近 20 条消息]
    SUMMARY[rolling_summary String - 滚动摘要]
    SLOTS[slots Hash - 已确认槽位]
    PINNED[pinned List - Pin 消息 用户偏好]

    V1[虚拟轮次 1: 历史对话摘要]
    V2[虚拟轮次 2: 已确认约束]
    V3[虚拟轮次 3: 用户偏好]
    REAL[最近 N 轮原文消息]

    SUMMARY --> V1
    SLOTS --> V2
    PINNED --> V3
    MESSAGES --> REAL

    V1 --> V2
    V2 --> V3
    V3 --> REAL
```

### 5.5 三级压缩机制

```mermaid
flowchart TD
    TRIGGER[触发: messages 大于 10 条] --> INPUT[旧摘要 + 旧消息 保留最近 5 条]
    INPUT --> LLM[三合一 LLM 调用 输出 summary + slots + pins]
    LLM --> PARSE[解析 JSON 重试 1 次]
    PARSE -->|成功| R1[L1 滚动摘要]
    PARSE -->|成功| R2[L2 槽位冻结]
    PARSE -->|成功| R3[L3 Pin 消息]
    R1 --> TRIM[LTRIM messages 保留最近 5 条]
    R2 --> TRIM
    R3 --> TRIM
    PARSE -->|失败| FB[降级: 截断不生成摘要 保留最近 4 条]
    FB --> TRIM
```

**核心文件：**
- [ContextAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java)
- [ConversationMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java)
- [ContextCompressionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextCompressionService.java)
- [LongTermMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java)
- [ContextLayer.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/dto/ContextLayer.java)
- [TokenBudget.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/dto/TokenBudget.java)

---

## 六、完整时序图：一次对话的完整流程

```mermaid
sequenceDiagram
    actor U as 用户
    participant FE as 前端
    participant GW as Gateway 8080
    participant AC as AgentController
    participant CS as AgentChatService
    participant IR as 意图识别
    participant RR as IntentRouter
    participant RS as RetrievalService
    participant PA as PromptAssembler
    participant CM as ConversationMemory
    participant LM as LongTermMemory
    participant CA as ContextAssembler
    participant DS as DeepSeek API
    participant REDIS as Redis
    participant DB as MySQL

    U->>FE: 发送消息
    FE->>GW: POST /api/agent/chat SSE
    GW->>AC: JWT 认证通过
    AC->>CS: chat(userId, request)

    Note over CS: prepareContext() 核心编排

    CS->>IR: ① recognizeIntent(query)
    IR->>IR: Layer1: 规则短路
    alt 规则命中
        IR-->>CS: IntentResult (RULE)
    else 规则未命中
        IR->>REDIS: 查意图缓存
        alt 缓存命中
            IR-->>CS: IntentResult (CACHE)
        else 缓存未命中
            IR->>DS: LLM 分类（三合一）
            DS-->>IR: JSON 结果
            IR->>REDIS: 异步写缓存
            IR-->>CS: IntentResult (LLM)
        end
    end

    CS->>CS: ② 注入检测（硬拦截/软拦截）

    CS->>RR: ③ tryShortCircuit(intent)
    alt 快路径（OUT_OF_SCOPE/NAVIGATE）
        RR-->>CS: RouteDecision + 模板回复
        CS-->>FE: SSE: session + delta + done
    else 慢路径（HOW_TO/SEARCH/CLARIFY）
        RR-->>CS: empty → 走 RAG

        CS->>RS: ④ retrieve(rewrittenQuery, intent)
        RS->>RS: 意图驱动配置选择
        RS->>DS: embedding query
        DS-->>RS: 1024 维向量
        RS->>RS: 四路并行检索 + RRF 融合
        RS-->>CS: List<RetrievalResult>

        CS->>PA: ⑤ assemble(intent, results, version)
        PA-->>CS: systemPrompt 字符串

        CS->>CM: ⑥ loadHistoryWithMemory(session)
        CM->>REDIS: 读 5 Key
        REDIS-->>CM: 摘要 + 槽位 + Pin + 消息
        CM-->>CS: 历史消息（含虚拟轮次）

        CS->>LM: ⑦ loadUserProfile(userId, intent, slots)
        LM->>DB: 查询 user_memories
        DB-->>LM: 记忆列表
        LM-->>CS: profile 文本（Top-5）

        CS->>CA: ⑧ assemble(...) 上下文工程
        CA->>CA: Token 预算 + 三级降级
        CA-->>CS: messages[] + totalTokens

        CS->>DS: chatCompletionStream(messages)
        DS-->>CS: delta 流式返回

        CS-->>FE: SSE: session → delta → delta → ...

        Note over CS: 流式结束后，异步后处理

        CS->>CS: 输出后护栏验证
        CS->>DB: 更新 agent_turns 状态
        CS->>CM: appendMessage() → 写入记忆
        CM->>REDIS: 追加 messages + 更新 meta
        CS->>CM: needsCompression?
        alt 需要压缩
            CS->>CS: triggerCompression()
            CS->>DS: 三合一 LLM 压缩调用
            DS-->>CS: summary + slots + pins
            CS->>CM: 更新摘要/槽位/Pin
            CM->>REDIS: LTRIM messages
        end
    end

    FE-->>U: 显示回复
```

---

## 七、关键设计决策速查表

| ADR | 决策 | 位置 | 效果 |
|-----|------|------|------|
| ADR-010 | 低置信度(<0.6)兜底为 SEARCH | IntentClassifier | 分类不准也不崩，最通用兜底 |
| ADR-011 | 分类+改写+槽位三合一 LLM 调用 | IntentClassifier | 省 ~500ms 延迟 + 1 次 API 成本 |
| ADR-013 | IntentRouter 快路径 | IntentRouter | OUT_OF_SCOPE/NAVIGATE 0 次 LLM |
| ADR-024 | 意图驱动检索策略 | RetrievalService.selectConfig | 不同意图用不同来源配比，更精准 |
| ADR-026 | CLARIFY 合并上一轮检索结果 | RetrievalService.mergePreviousRetrieval | 指代消解有上下文，降权 0.5 不喧宾夺主 |
| ADR-029 | 检索结果缓存（非 CLARIFY） | RetrievalService | 重复 query 省 embedding + 检索成本 |
| ADR-050~053 | 三级压缩机制 | ContextCompressionService | 长对话不爆 token，降级有保底 |
| ADR-051 | 压缩三合一 LLM 调用 | ContextCompressionService | 省 60% 压缩成本 |
| ADR-054 | 短期记忆读写时序：LLM 回答后写 | ConversationMemoryService | 避免写入不完整的 assistant 消息 |
| ADR-059 | 长期记忆按意图/槽位相关性装载 | LongTermMemoryService | 新会话首轮就能个性化 |
| ADR-070~072 | L0-L5 分层 + Token 预算 + 三级降级 | ContextAssembler | 上下文可控，超预算有兜底 |
| ADR-SP-04 | Guardrail 放末尾防注入 | PromptAssembler | 利用 LLM recency bias，注入难绕过 |
| ADR-SP-06 | L1 平台级 Prompt 固定不变 | PromptConstants | 命中 Prefix Cache，省成本 + 加速 |

---

## 八、核心代码文件索引

| 模块 | 主文件 | 关键方法 |
|------|--------|---------|
| 总入口 | [AgentChatService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) | `chat()` / `prepareContext()` |
| 意图识别-L1 | [RuleShortCircuitFilter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RuleShortCircuitFilter.java) | `filter()` |
| 意图识别-L2 | [IntentClassifier.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java) | `classify()` / `classifyByLLM()` |
| 意图路由 | [IntentRouter.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java) | `tryShortCircuit()` |
| RAG 检索 | [RetrievalService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) | `retrieve()` / `selectConfig()` / `rrfFusion()` |
| 知识库向量 | [KnowledgeVectorStore.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/store/KnowledgeVectorStore.java) | `searchChunks()` / `keywordSearchChunks()` |
| Prompt 装配 | [PromptAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java) | `assemble()` |
| Prompt 版本 | [PromptVersionManager.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java) | `getCurrentVersion()` |
| 安全护栏 | [ConstitutionalAIValidator.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java) | `shouldHardBlock()` / `validate()` |
| 上下文组装 | [ContextAssembler.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java) | `assemble()` |
| 短期记忆 | [ConversationMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java) | `loadHistoryAsTurns()` / `appendMessage()` |
| 上下文压缩 | [ContextCompressionService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextCompressionService.java) | `compress()` |
| 长期记忆 | [LongTermMemoryService.java](file:///e:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java) | `loadUserProfile()` |
