# Agent 架构审查与升级方案

> 审查日期: 2026-07-13
> 审查范围: campushare-agent 模块全部代码(11 个核心模块)
> 审查方式: 全链路代码走查 + 设计文档对比 + 大厂架构对标
> 文档目的: 总结各模块设计融合与全链路,Review 方案缺陷与潜在风险,提出大厂级架构升级方案

---

## 目录

- [一、Agent 全链路架构总览](#一agent-全链路架构总览)
- [二、各模块设计融合详解](#二各模块设计融合详解)
- [三、方案缺陷与潜在风险](#三方案缺陷与潜在风险)
- [四、大厂架构升级方案](#四大厂架构升级方案)
- [五、升级优先级与实施路线图](#五升级优先级与实施路线图)

---

## 一、Agent 全链路架构总览

### 1.1 全链路调用图

```
用户消息 (ChatRequest)
   │
   ▼
┌─────────────────────────────────────────────────────────────────┐
│  AgentChatService.chat()  ← 主编排器(Reactor 响应式)              │
│  │                                                              │
│  ├─① getOrCreateSession()        会话管理(新建/复用)              │
│  │    └─ SessionStateMachine      INIT → ACTIVE 状态转移         │
│  │    └─ ConversationMemoryService.initSession()  Redis 5 Key 初始化│
│  │                                                              │
│  ├─② recognizeIntent()           意图识别(三层漏斗)               │
│  │    ├─ Layer1: RuleShortCircuitFilter  规则短路                │
│  │    ├─ Layer2: IntentClassifier        LLM 分类(+IntentCache)  │
│  │    ├─ Layer3: EmbeddingIntentFallback Embedding 兜底          │
│  │    └─ Default: SEARCH 兜底                                   │
│  │    └─ SchoolNameUtils              学校名称规则预提取           │
│  │                                                              │
│  ├─③ ConstitutionalAIValidator    注入检测(硬拦截/软拦截)         │
│  │                                                              │
│  ├─④ IntentRouter.tryShortCircuit()  意图路由                    │
│  │    ├─ 快路径: OUT_OF_SCOPE/NAVIGATE → 模板回复(不调 LLM)      │
│  │    └─ 慢路径: HOW_TO/SEARCH/CLARIFY → RAG 管线                │
│  │                                                              │
│  ├─⑤ RetrievalService.retrieve()  RAG 混合检索(意图驱动)          │
│  │    ├─ 知识库向量检索(HNSW + 余弦)                             │
│  │    ├─ 知识库关键词检索(pg_trgm)                               │
│  │    ├─ 帖子向量检索(带 slots 过滤)                             │
│  │    ├─ 帖子关键词检索                                          │
│  │    ├─ CLARIFY: 上一轮结果降权 0.5 合并                        │
│  │    ├─ RRF 融合 → 质量加权 → 跨源去重 → Token 预算截断          │
│  │    └─ Redis 缓存(非 CLARIFY)                                 │
│  │                                                              │
│  ├─⑥ MemoryRetrievalService      长期记忆检索                    │
│  │    ├─ loadProfileMemories()    画像全量装载                   │
│  │    └─ retrieveRelevantMemories() 相关性向量召回                │
│  │                                                              │
│  ├─⑦ PromptAssembler.assemble()  Prompt 组装(L1-L4 + context)    │
│  │    └─ PromptVersionManager     版本管理(灰度/A/B)              │
│  │                                                              │
│  ├─⑧ ContextAssembler.assemble() 上下文工程(L0-L5 分层)          │
│  │    ├─ L0 System Prompt(永驻)                                 │
│  │    ├─ L1 用户画像(可裁剪)                                    │
│  │    ├─ L2 工具定义(永驻)                                      │
│  │    ├─ L4 对话历史(可压缩,滚动摘要+槽位+Pin 前缀)              │
│  │    ├─ L5 用户输入(永驻)                                      │
│  │    ├─ Token 预算分配(意图驱动)                                │
│  │    └─ 三级降级链(L4截断→L1丢弃→硬上限)                       │
│  │    └─ ContextSnapshotService  异步快照入库                    │
│  │                                                              │
│  ├─⑨ runToolCallLoop()           工具调用循环(maxToolRounds=5)   │
│  │    ├─ ToolRegistry.getToolSchemas(intent)  意图驱动工具分配    │
│  │    ├─ DeepSeekClient.chatCompletion(msgs, tools)  Function Call│
│  │    ├─ ToolExecutor.execute()   工具执行(并发)                  │
│  │    │    ├─ NavigateToPageTool   页面导航                      │
│  │    │    ├─ SearchKnowledgeTool  知识库搜索                    │
│  │    │    └─ SearchPostsTool      帖子搜索                      │
│  │    └─ 递归直到无 tool_calls 或达上限                          │
│  │                                                              │
│  ├─⑩ DeepSeekClient.chatCompletionStream()  最终流式回答         │
│  │    └─ SSE 逐 token 输出 → ChatEvent("delta")                 │
│  │                                                              │
│  └─⑪ completeTurn()              Turn 完成(异步)                 │
│       ├─ ConstitutionalAIValidator 输出校验                      │
│       ├─ 事务更新 AgentTurn + AgentSession                       │
│       ├─ ConversationMemoryService  Redis 短期记忆写入            │
│       ├─ triggerCompression()     三级压缩(摘要/槽位/Pin)        │
│       └─ SessionArchivalService   会话归档(触发长期记忆抽取)      │
└─────────────────────────────────────────────────────────────────┘
   │
   ▼
SSE 流式响应 (ChatEvent: session / delta / navigate / refs)
```

### 1.2 数据流与存储分层

| 存储层 | 技术 | 用途 | TTL |
|--------|------|------|-----|
| 热缓存 | Redis | 短期记忆(5 Key)、意图缓存、检索结果缓存、Prompt 版本号 | 2h / 5m |
| 关系库 | MySQL | 会话/轮次/快照/槽位/Pin/长期记忆/知识库元数据/Prompt 版本 | 持久 |
| 向量库 | PostgreSQL + pgvector | 知识库分块向量、帖子向量、记忆向量 | 持久 |
| 对象存储 | - | 知识库 Markdown 文档源 | 持久 |

### 1.3 模块矩阵

| 模块 | 核心类 | 职责 | ADR |
|------|--------|------|-----|
| LLM 网关 | DeepSeekClient / EmbeddingClient | 模型调用、熔断重试、流式 | - |
| 意图识别 | IntentClassifier / IntentRouter / RuleShortCircuitFilter | 三层漏斗分类、路由短路 | ADR-024~026 |
| 上下文工程 | ContextAssembler / ContextCompressionService | L0-L5 分层、Token 预算、降级 | ADR-070~076 |
| 短期记忆 | ConversationMemoryService | Redis 5 Key、压缩触发 | ADR-054~058 |
| 长期记忆 | LongTermMemoryService / MemoryRetrievalService | 画像装载、记忆抽取、衰减 | ADR-059~063 |
| RAG 检索 | RetrievalService | 四路混合检索、RRF 融合 | ADR-024~029 |
| 知识库管理 | KnowledgeIngestionService / KnowledgeVersionService | 摄入、分块、版本、去重 | - |
| 工具调用 | ToolRegistry / ToolExecutor | 注解注册、意图过滤、Function Calling | ADR-TOOL |
| MCP 协议 | McpClientManager / McpServerController | JSON-RPC、工具发现 | - |
| Prompt 工程 | PromptAssembler / PromptVersionManager | 分层组装、版本灰度 | - |
| 安全护栏 | ConstitutionalAIValidator | 注入检测、输出校验 | - |
| 可观测性 | IntentMetricsConfig / KnowledgeMetricsConfig | Micrometer 指标 | ADR-030 |

---

## 二、各模块设计融合详解

### 2.1 LLM 网关层

**核心实现**: [DeepSeekClient.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/DeepSeekClient.java)

**设计要点**:
- 三种调用模式:同步 `chatCompletion`、流式 `chatCompletionStream`、Function Calling(带 tools 参数)
- 弹性策略:Resilience4j 熔断器(`CircuitBreakerOperator`)+ 指数退避重试(`Retry.backoff`,max 3 次)+ 超时(同步 60s / 流式 120s)
- 可重试判定:5xx、429(限流)、TimeoutException、IOException 才重试,4xx 不重试
- 流式 SSE 解析:`ServerSentEvent<String>` → `StreamChunk(content, usage)`,`[DONE]` 终止信号
- 流式超时降级:超时后返回固定提示文本 `[响应超时,请重试]`,不中断 Flux

**融合点**:
- 被 AgentChatService 的 `runToolCallLoop`(同步,带 tools)和最终回答(流式,不带 tools)调用
- 被 LongTermMemoryService 的 `callExtractionLLM`(记忆抽取)调用
- 被 ContextCompressionService(三级压缩)调用
- 被 IntentClassifier(意图分类)调用

### 2.2 意图识别模块

**核心实现**: [IntentClassifier.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java) / [IntentRouter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java)

**三层漏斗设计**:
```
query
  │
  ├─ Layer 1: RuleShortCircuitFilter  正则/关键词规则命中 → 直接返回(0ms)
  │
  ├─ Layer 2: IntentClassifier        LLM 分类(带 IntentCacheService 缓存)
  │    └─ 失败降级 ↓
  │
  ├─ Layer 3: EmbeddingIntentFallback  Embedding 相似度匹配预设意图向量
  │    └─ 失败降级 ↓
  │
  └─ Default: SEARCH/RESOURCE 兜底(confidence=0.0)
```

**意图体系**(5 大意图 + 子意图):
- `HOW_TO`:操作指引(偏知识库)
- `SEARCH`:资源搜索(RESOURCE/DISCUSSION/CONTENT_QA 子意图,偏帖子)
- `NAVIGATE`:页面导航(快路径,模板回复 + 跳转)
- `CLARIFY`:指代消解(合并上一轮检索结果,降权 0.5)
- `OUT_OF_SCOPE`:超范围(快路径,模板拒绝)

**意图驱动检索**(ADR-024):不同意图选择不同的检索配置(知识库 topK / 帖子 topK / 阈值 / 是否启用关键词检索),见 [RetrievalService.selectConfig()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java#L260)。

**路由短路**:`IntentRouter.tryShortCircuit()` 对 NAVIGATE/OUT_OF_SCOPE 返回 `RouteDecision`,跳过 LLM 调用,直接模板回复并以打字机效果流式输出(25ms/2字符)。

### 2.3 上下文工程模块

**核心实现**: [ContextAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java)

**L0-L5 分层结构**:
| 层 | 内容 | 可截断 | Token 预算 | 来源 |
|----|------|--------|-----------|------|
| L0 | System Prompt(含 L1平台+L2任务+L3 Few-shot+检索结果+L4护栏) | 否 | ~4000-5000 | PromptAssembler |
| L1 | 用户画像 | 是 | ~300 | LongTermMemoryService |
| L2 | 工具定义 Schema | 否 | - | ToolRegistry(Function Calling 时) |
| L3 | 检索结果 | 已嵌入 L0 | - | RetrievalService |
| L4 | 对话历史 | 是 | ~1500-4000 | ConversationMemoryService |
| L5 | 用户输入 | 否 | ~700 | ChatRequest |

**Token 预算分配**(`TokenBudget.forIntent(intent)`):总预算 8000 tokens,按意图动态分配 L4 历史预算。

**三级降级链**:
```
超预算 → 降级1: L4 截断到最近 2 轮
       → 降级2: L1 丢弃(用户画像)
       → 降级3: 硬上限 L4 截断到最近 1 轮(记录 truncated=true)
```

**XML 标签分层规范**:`<system_rules>` / `<user_profile>` / `<available_tools>` / `<user_query>`,帮助 LLM 区分指令与数据。

**短期记忆前缀注入**(L4 头部虚拟轮次):
1. 滚动摘要 → `[历史对话摘要]`
2. 槽位 → `[已确认约束]`
3. Pin 消息 → `[用户偏好]`
4. 最近 N 轮原文

### 2.4 短期记忆模块

**核心实现**: [ConversationMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java)

**Redis 5 Key 结构**(每会话):
| Key | 类型 | 内容 | TTL |
|-----|------|------|-----|
| `agent:session:{sid}:meta` | Hash | user_id/status/turn_count/current_intent/intent_history | 2h |
| `agent:session:{sid}:messages` | List | 最近 20 条 user/assistant 消息 JSON | 2h |
| `agent:session:{sid}:rolling_summary` | String | 滚动摘要 | 2h |
| `agent:session:{sid}:slots` | Hash | 槽位 key-value | 2h |
| `agent:session:{sid}:pinned` | List | Pin 消息(最多 5 条) | 2h |

**读写时序**(ADR-054):
- 读:Redis 优先,miss 时降级到 MySQL(context_summary/context_slot/pin_message 表),并回写 Redis
- 写:Redis 先写,MySQL 异步持久化(fire-and-forget)
- TTL 续期:每次活跃 `renewTTL()` 批量 EXPIRE 5 个 Key

**三级压缩**(触发阈值 messages > 10):
- `ContextCompressionService.compress()` 一次 LLM 调用产出:摘要 + 槽位 + Pin
- 压缩后 `trimMessages(keepCount=5)`,降级模式 `trimMessages(4)`
- 持久化:摘要/槽位/Pin 写入 MySQL

### 2.5 长期记忆模块

**核心实现**: [LongTermMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java)

**四象限分类**:
| 类型 | source | 衰减率 | 示例 |
|------|--------|--------|------|
| PREFERENCE | EXPLICIT | 0.02/周 | "我喜欢 PDF" |
| FACT | EXPLICIT | 0.03/周 | "我是计算机专业" |
| BEHAVIOR | INFERRED | 0.1/周 | 主要访问分类 |
| TASK | - | 0.3/周,4周未更新删除 | "在找考研资料" |

**装载策略**(`loadUserProfile`):相关性 + 优先级排序 → Top-5 → 格式化为画像文本(≤300 字)
1. 强相关(memory_key/value 匹配意图/槽位)
2. 高置信(≥0.7)
3. 近期使用(7 天内)
4. 类型优先级(PREFERENCE > FACT > BEHAVIOR > TASK)

**记忆抽取**(会话归档时):`extractMemories()` 从滚动摘要 LLM 抽取显式偏好,UPSERT 到 user_memory 表。

**UPSERT 语义**:已存在同 user+type+key → confidence += 0.1(封顶 1.0),evidence_count += 1;不存在 → 插入 confidence=1.0。

**衰减任务**:`@Scheduled(cron = "0 0 2 ? * SUN")` 每周日 02:00,按类型差异化衰减,高频访问(7天≥3次)衰减率减半,confidence ≤ 0.2 软删除。

### 2.6 RAG 检索模块

**核心实现**: [RetrievalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java)

**四路混合检索**:
1. 知识库向量(HNSW + 余弦距离)
2. 知识库关键词(pg_trgm + GIN 索引)
3. 帖子向量(带 slots 过滤:学校/分类)
4. 帖子关键词(按配置启用)

**RRF 融合**:`score = Σ 1/(k + rank)`,k=60,同结果多路命中分数累加。

**后处理链**:
- 文章级聚合:同 article_id 多 chunk 取最高相似度,多命中加成 `sim * (1 + 0.1*(hits-1))`
- 相似度阈值过滤(意图驱动:HOW_TO 0.5 / SEARCH 0.4)
- 质量评分加权:`finalScore = rrfScore * (0.8 + 0.2 * qualityScore)`(仅知识库)
- 跨源去重:标题 Jaccard 分词相似度 > 0.8 移除低分项
- Token 预算截断(默认 2500 tokens)

**缓存**:Redis 缓存检索结果,key = `agent:retrieval:{md5(query:intent:subIntent)}`,TTL 5min,CLARIFY 不缓存。

### 2.7 知识库管理模块

**核心实现**: [KnowledgeIngestionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java)

**摄入流程**:
```
Markdown 文档 → 解析(frontmatter + 正文)
  → MD5 去重(content_md5 对比)
  → MarkdownChunker 按 H2 标题分块
  → EmbeddingClient 批量 embedding
  → 重复检测(ThresholdDuplicateDetector,阈值 0.95)
  → 四维度质量评分(FourDimensionQualityScorer)
  → KnowledgeVectorStore.upsertChunks(PG)
  → KnowledgeArticle + KnowledgeArticleVersion(MySQL)
```

**版本管理**(SemVer):`KnowledgeVersionService` 支持版本创建、切换、回滚,回滚生成 nextPatch 版本。

**帖子向量化**:`PostVectorScheduler` 定时同步帖子到 PG,帖子服务通过 Feign 通知 agent 模块。

### 2.8 工具调用模块

**核心实现**: [ToolRegistry.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolRegistry.java)

**注解驱动注册**:
- `@ToolDef(name, description, intent, readOnly, timeoutMs)` 标注工具类
- `@PostConstruct` 时扫描所有 `Tool` Bean,反射构建参数 Schema(`@ToolParam`)
- 强制 `readOnly=true`(ADR-TOOL-02:Agent 不能执行写操作),否则启动失败

**意图驱动工具分配**:`getToolSchemas(intent)` 只下发该意图可用的工具(工具定义的 intents 为空表示全意图可用)。

**Function Calling 循环**(`AgentChatService.runToolCallLoop`):
- 最大 5 轮(`maxToolRounds`)
- 每轮:LLM 带 tools 调用 → 解析 tool_calls → 并发执行(`Flux.concat`)→ 结果作为 tool message 回填 → 递归
- 无 tool_calls 或达上限 → 返回最终 messages
- 错误降级:工具执行失败返回 `{"status":"ERROR"}` JSON,不中断循环

**三个内置工具**:
- `NavigateToPageTool`:页面导航(route 参数)
- `SearchKnowledgeTool`:知识库搜索(query 参数)→ 返回 Refs
- `SearchPostsTool`:帖子搜索(query/category 参数)→ 返回 Refs

### 2.9 MCP 协议模块

**核心实现**: [McpClientManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/McpClientManager.java)

- **客户端**:JSON-RPC 协议,工具发现(`tools/list`)、工具调用(`tools/call`)
- **服务端**:`McpServerController` 暴露内置工具供外部 MCP 客户端调用
- **适配器**:`McpToolAdapter` 将内置 Tool 包装为 MCP 协议格式
- **动态发现**:`McpToolDiscoveryService` 从远程 MCP server 发现并注册工具

### 2.10 Prompt 工程模块

**核心实现**: [PromptAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java)

**六要素装配**:
```
L1 平台级(固定,命中 Prefix Cache)
  + L2 任务级(按意图切换:HOW_TO/SEARCH/NAVIGATE/CLARIFY/OUT_OF_SCOPE)
  + L3 Few-shot 示例
  + <context> 检索结果(防隐式注入,资料不是指令)
  + L4 安全护栏(末尾,防注入)
```

**版本管理**:`PromptVersionManager` 支持灰度发布、A/B 测试、用户绑定版本,DB 故障降级到 `PromptConstants` 硬编码常量。

**检索结果格式化**:展示完整 metadata(headingPath/qualityScore/chunkHits/category/school),帮助 LLM 引用位置、判断可信度。

### 2.11 安全护栏模块

**核心实现**: [ConstitutionalAIValidator.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java)

**双向校验**:
- 输入侧:`shouldHardBlock`(硬拦截,抛异常) + `detectInjection`(软拦截,log + meter)
- 输出侧:`validate(content)` 检测违规内容,记录 `agent.prompt.violation` 指标

---

## 三、方案缺陷与潜在风险

### 3.1 架构级缺陷(Critical)

#### 缺陷 1:单模型强依赖,无多模型路由与降级

**位置**: [DeepSeekClient.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/DeepSeekClient.java)

**问题**:整个系统硬绑定 DeepSeek 单一模型,意图分类、记忆抽取、压缩、最终回答、工具调用决策全部走同一模型同一 endpoint。没有多模型路由层(如便宜模型做意图分类,强模型做复杂推理),也没有模型不可用时的跨厂商降级(如 DeepSeek 宕机 → 切换通义千问/智谱)。

**风险**:DeepSeek 服务故障 = 全站 Agent 不可用;无法按任务复杂度优化成本(简单意图分类用大模型浪费)。

#### 缺陷 2:响应式流中大量 block() 同步阻塞

**位置**: [AgentChatService.java#L533](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java#L533)、[AgentChatService.java#L547](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java#L547)、[AgentChatService.java#L610](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java#L610)、[LongTermMemoryService.java#L426](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java#L426)

**问题**:`retrievalService.retrieve(...).block()`、`memoryRetrievalService.retrieveRelevantMemories(...).block()`、`intentClassifier.classify(...).block()`、`callExtractionLLM(...).block()` 在响应式链路中同步阻塞,虽然外层包了 `Schedulers.boundedElastic()`,但多个 block 串行执行导致首字延迟叠加(意图分类 + 检索 + 记忆检索串行)。

**风险**:首字延迟 = 意图分类(1-2s)+ 检索(500ms)+ 记忆检索(300ms)+ Prompt 组装,总延迟可能 2-3s 才出第一个 token。

#### 缺陷 3:跨数据源(MySQL + PostgreSQL)无分布式事务

**位置**: [InternalAgentController.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/controller/InternalAgentController.java)、[KnowledgeVectorStore.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/store/KnowledgeVectorStore.java)

**问题**:知识库摄入/回滚涉及 MySQL(元数据)和 PG(向量)双写,无分布式事务或补偿机制。`upsertChunks()` 先删后插无 `@Transactional`(问题 2),rollback 跨源无补偿(问题 3)。

**风险**:部分失败导致元数据与向量不一致,检索结果与实际内容不符,且无自动修复机制。

### 3.2 性能瓶颈(Major)

#### 瓶颈 1:PromptVersion 每请求查 DB 无缓存

**位置**: [PromptVersionManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java#L108)(问题 7)

**问题**:`getCurrentVersion(userId)` 每次对话都查 MySQL 获取完整 PromptVersion(2-5KB),高并发(100 QPS)下每秒 100 次 DB 查询。

#### 瓶颈 2:长期记忆全表扫描

**位置**: [LongTermMemoryService.java#L127](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java#L127)

**问题**:`loadUserProfile` 执行 `userMemoryMapper.selectList(userId)` 加载用户全部记忆到内存,再在 Java 层排序取 Top-5。用户记忆多时(如 1000 条)每次加载全部,内存和查询开销大。

#### 瓶颈 3:意图识别缓存 key 不含会话上下文

**位置**: [IntentCacheService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentCacheService.java)

**问题**:缓存 key 只基于 query 文本,同一 query 在不同会话上下文(如 CLARIFY 指代消解)可能需要不同意图,但缓存命中会返回错误意图。

### 3.3 数据一致性风险(Major)

#### 风险 1:异步 fire-and-forget 链路过长

**位置**: AgentChatService 中多处 `.subscribeOn(Schedulers.boundedElastic()).subscribe()`

**问题**:Turn 完成、快照写入、记忆写入、压缩触发、召回计数、缓存写入、向量写入全部是 fire-and-forget,无失败重试、无补偿队列。任意环节失败数据丢失且无感知。

**典型场景**:
- `contextSnapshotService.saveSnapshot()` 失败 → 快照丢失,无法审计
- `asyncUpsertVector()` 失败 → 记忆向量未入库,语义检索永远查不到该记忆
- `asyncIncrementRecall()` 失败 → 召回计数不准,影响质量评分

#### 风险 2:Redis 与 MySQL 双写不一致

**位置**: [ConversationMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java)

**问题**:摘要/槽位/Pin 采用 Redis 先写 + MySQL 异步持久化。若 Redis 写成功但 MySQL 异步写失败,Redis TTL 过期后降级加载 MySQL 会读到旧数据或空数据。

#### 风险 3:重复检测误判自身旧版本(问题 8)

**位置**: [KnowledgeIngestionService.java#L147](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java#L147)

**问题**:更新文档时重复检测未排除自身 articleId,第一个 chunk 内容未变即误判为 DUPLICATE 跳过更新,导致知识库更新静默失效。

### 3.4 功能完整性缺陷(功能缺失)

#### 缺失 1:长期记忆模块完成度仅 30-35%

详见 [code-review-issues.md](file:///d:/WorkSpace-java/CS/docs/agent-design/code-review-issues.md) 问题 9-15:
- INFERRED 行为推断通道未实现(问题 9)
- 向量检索未实现,memory_vectors 表闲置(问题 10)
- 冲突仲裁未实现,直接覆盖(问题 11)
- used_memory_ids 回写未实现(问题 12)
- 用户记忆管理 API 未实现(问题 13)
- 证据表和审计表闲置(问题 14)
- 物理清除和记忆恢复未实现(问题 15)

#### 缺失 2:工具调用缺乏权限控制与沙箱

**位置**: [ToolExecutor.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolExecutor.java)

**问题**:工具执行无用户级权限校验(如某用户不能搜索某分类)、无执行沙箱、无结果大小限制、无超时熔断(虽然 ToolDef 有 timeoutMs 但未在 Executor 强制执行)。

#### 缺失 3:可观测性缺乏分布式追踪

**位置**: [AgentChatService.java#L464](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java#L464)

**问题**:MDC traceId 是 `UUID.randomUUID().substring(0,8)`,未接入 OpenTelemetry/Jaeger/Tempo 分布式追踪。跨服务调用(post/user Feign 调用)无法串联,问题定位困难。虽然 docker-compose 配了 Tempo,但代码层未集成。

### 3.5 安全风险

#### 安全 1:Prompt 注入检测依赖正则,易绕过

**位置**: [ConstitutionalAIValidator.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java)

**问题**:注入检测基于关键词/正则匹配,无法防御语义级注入(如"忽略上述指令"的变体、Unicode 同形字、多语言绕过)。`<context>` 标签是软隔离,LLM 仍可能被检索内容中的指令操纵。

#### 安全 2:工具参数未做 schema 严格校验

**位置**: [ToolExecutor.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolExecutor.java)

**问题**:LLM 返回的 tool arguments 只做了 JSON 解析,未按 `@ToolParam` 的 type/required/enumValues 严格校验。恶意 LLM 输出可传入超长字符串、非法枚举值、注入 SQL。

#### 安全 3:内部 API 认证薄弱

**位置**: [InternalApiAuthFilter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/config/InternalApiAuthFilter.java)

**问题**:内部 API(知识库摄入、向量通知)仅靠静态 token 认证,无 IP 白名单、无签名、无时效,token 泄露即可任意操作知识库。

---

## 四、大厂架构升级方案

### 4.1 LLM 网关升级:多模型路由 + 统一网关

**对标**:阿里云百炼、字节豆包 Coze、OpenAI Router

**升级方案**:

```
┌─────────────────────────────────────────────────┐
│              LLM Gateway(统一网关)               │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐   │
│  │ 模型路由器 │  │ 成本追踪  │  │ 配额管理  │   │
│  └─────┬─────┘  └───────────┘  └───────────┘   │
│        │                                        │
│  ┌─────▼──────────────────────────────────┐    │
│  │       Provider 适配层                   │    │
│  │  DeepSeek │ Qwen │ GLM │ OpenAI │ ...  │    │
│  └────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────┐    │
│  │  熔断 + 限流 + 重试 + 降级 + 熔断器      │    │
│  └────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

**关键设计**:
1. **任务路由**:意图分类/记忆抽取/压缩 → 轻量模型(DeepSeek-V3-Flash);复杂推理/工具决策 → 强模型(DeepSeek-V3 / Qwen-Max)
2. **多模型降级**:主模型熔断 → 自动切换备用模型(DeepSeek → Qwen → GLM),基于 Resilience4j 熔断器状态机
3. **统一接口**:`LlmClient` 接口抽象,各 Provider 实现,统一 `chatCompletion` / `chatCompletionStream` / `embed` 接口
4. **成本追踪**:每次调用记录 model/input_tokens/output_tokens/cost/latency,写入 `llm_call_log` 表,支持按用户/会话/模型维度成本分析
5. **配额管理**:用户级 QPS/日调用量配额,超限返回 429,防止滥用
6. **Prompt 缓存**:对前缀稳定的 system prompt 启用 provider 侧 prefix cache(DeepSeek 已支持),降低 50%+ input token 成本

**预期收益**:成本降低 40%(轻量任务走便宜模型)、可用性 99.95%(多模型降级)、首字延迟降低 30%(路由优化)。

### 4.2 意图识别升级:级联分类 + 在线学习

**对标**:Google Dialogflow CX、Rasa、阿里小蜜

**升级方案**:

```
query
  │
  ├─ Layer 0: 语义缓存(Semantic Cache)  语义级命中,无需分类
  │    └─ query embedding → 近似最近邻查历史意图
  │
  ├─ Layer 1: 规则短路(保留)              高置信规则,0ms
  │
  ├─ Layer 2: 小模型分类(BERT/分类器)     本地推理,10ms
  │    └─ Fine-tuned BERT-Base-Chinese
  │
  ├─ Layer 3: LLM 分类(保留,降级用)      强模型兜底
  │
  └─ Layer 4: Embedding 兜底(保留)
```

**关键设计**:
1. **语义缓存**:query embedding → 向量库查相似历史 query(相似度 > 0.95),直接复用意图,避免重复调 LLM
2. **小模型分类**:Fine-tune BERT-Base-Chinese 做意图分类(5 大意图),本地 GPU 推理 10ms,准确率 90%+ 时不再调 LLM
3. **在线学习**:用户反馈(点赞/点踩)+ 人工标注回流,定期 retrain 小模型,形成数据飞轮
4. **置信度路由**:小模型置信度 > 0.9 直接用;< 0.9 才调 LLM;LLM 也低置信走 Embedding
5. **意图预测**:基于会话历史意图序列(意图转移马尔科夫链)预测当前意图,作为先验

### 4.3 上下文工程升级:分层缓存 + 预计算

**对标**:Claude Context Window、GPT-4 Turbo Memory、LangChain Memory

**升级方案**:

1. **Prompt Prefix Cache**:L1 平台级 + L2 任务级 prompt 前缀稳定,利用 provider 侧 prefix cache(DeepSeek/OpenAI 均支持),减少 50% input token
2. **上下文预计算**:会话活跃期间异步预计算下一轮可能需要的上下文(如预加载相关记忆、预热检索),减少首字延迟
3. **智能压缩策略**:
   - 当前:固定阈值 10 条触发压缩
   - 升级:基于 token 预算动态触发,按消息重要性(含工具调用/用户反馈的消息权重高)选择性保留
4. **长期上下文窗口**:引入 "长期记忆指针"机制,不把全部历史塞入 context,而是让 LLM 通过工具调用按需检索历史轮次(类似 Cursor 的代码库索引)
5. **多模态上下文**:预留图片/文件上下文层(L3.5),为未来多模态扩展做准备

### 4.4 短期记忆升级:事件溯源 + CQRS

**对标**:ChatGPT Memory、Notion AI、Linear AI

**升级方案**:

```
┌──────────────────────────────────────────────┐
│  事件溯源(Event Sourcing)                     │
│  agent_session_events 表:所有写操作记录为事件   │
│  - MessageAppended / SummaryUpdated / ...    │
└──────────────┬───────────────────────────────┘
               │
   ┌───────────▼───────────┐
   │   CQRS 读写分离        │
   │  写:事件追加(仅 append) │
   │  读:Redis 快照 + 事件回放│
   └───────────────────────┘
```

**关键设计**:
1. **事件溯源**:所有记忆变更记录为不可变事件(`AgentSessionEvent`),当前状态 = 初始状态 + 事件回放,天然审计 + 可回滚
2. **Redis 写穿 + Write-Behind**:Redis 写成功同步写事件表,异步物化视图(MySQL 快照),保证最终一致
3. **压缩即 Snapshot**:压缩操作 = 创建新 snapshot 事件,旧 snapshot 保留可回滚
4. **跨会话记忆继承**:新会话可继承同用户上一会话的 slots/summary,实现会话间连续性
5. **记忆可视化 API**:用户可查看/编辑/删除会话记忆,满足隐私合规(GDPR/个保法)

### 4.5 长期记忆升级:Graph Memory + 双通道

**对标**:Microsoft GraphRAG、Zep Memory、Mem0

**升级方案**:

```
┌──────────────────────────────────────────────────┐
│              长期记忆系统(Graph + Vector)          │
│                                                  │
│  ┌──────────────┐    ┌──────────────────────┐   │
│  │ 记忆知识图谱  │    │ 记忆向量库(pgvector)  │   │
│  │ User -[LIKE]->│    │  HNSW 索引           │   │
│  │  Python       │    │  语义检索            │   │
│  └──────┬───────┘    └──────────┬───────────┘   │
│         │     双路召回 + 融合     │               │
│         └──────────┬────────────┘               │
│                    │                            │
│  ┌─────────────────▼──────────────────┐        │
│  │  双通道采集                          │        │
│  │  EXPLICIT: LLM 抽取(已实现)         │        │
│  │  INFERRED: 行为证据累积 → 推断        │        │
│  └────────────────────────────────────┘        │
└──────────────────────────────────────────────────┘
```

**关键设计**:
1. **记忆知识图谱**:`User -[PREFERENCE]-> Entity`,支持关系推理(如"喜欢 Python" → "可能喜欢编程"),解决向量检索无法推理的痛点
2. **INFERRED 通道**:`user_memory_evidence` 表记录行为证据(QUERY/FEEDBACK/TOOL_CALL),evidence_count ≥ 3 触发 LLM 推断,confidence=0.6
3. **冲突仲裁 LLM**:`ConflictResolver` 用 LLM 仲裁 KEEP_NEW/KEEP_OLD/KEEP_BOTH,旧值归档到 `user_memory_history`
4. **三层遗忘**:日衰减(当前周衰减 → 升级日衰减)+ 软删除(30 天回收站)+ 物理清除(定时任务)
5. **记忆恢复**:软删除期间再次提及 → 恢复 deletedAt=NULL,confidence 重置 0.5
6. **used_memory_ids 回写**:`loadUserProfile` 返回 usedMemoryIds,`ContextSnapshotService` 写入快照,支撑使用频率统计
7. **用户管理 API**:`GET/DELETE /agent/memories`,软删除 + 审计,满足隐私合规

### 4.6 RAG 检索升级:Agentic RAG + 多模态

**对标**:Perplexity、Anthropic Claude、Vercel AI SDK

**升级方案**:

```
query
  │
  ├─ Query 改写(已实现)
  ├─ HyDE 假设文档生成  query → LLM 生成假设答案 → 用答案 embedding 检索
  │
  ├─ 多路检索(已实现 4 路)
  ├─ Cross-Encoder 重排  替代 RRF,语义级精排
  ├─ GraphRAG 补充  知识图谱关系检索
  │
  ├─ Agentic 检索  LLM 自主决定是否检索/检索几次
  └─ Self-RAG  LLM 自评检索结果质量,不足时重检索
```

**关键设计**:
1. **HyDE**(Hypothetical Document Embedding):query → LLM 生成假设答案 → 用答案 embedding 检索,解决 query-answer 语义鸿沟
2. **Cross-Encoder 重排**:召回阶段用 Bi-Encoder(快),精排阶段用 Cross-Encoder(如 BGE-Reranker,准),替代纯 RRF
3. **GraphRAG**:构建知识图谱(实体 + 关系),检索时既召回向量相似,也召回图邻居,支持多跳推理
4. **Agentic RAG**:LLM 自主决定检索策略(是否检索、检索几次、查询改写),类似 ReAct 模式
5. **Self-RAG**:LLM 评估检索结果相关性,不足时自主重检索(最多 N 轮),避免低质量回答
6. **多模态检索**:图片/表格向量化(BGE-M3),支持图文混合检索
7. **检索结果缓存升级**:语义缓存(embedding 相似度 > 0.95 复用),而非当前 MD5 精确匹配

### 4.7 知识库管理升级:流式摄入 + 增量索引

**对标**:Dify、FastGPT、Coze 知识库

**升级方案**:

1. **流式摄入**:文档上传 → 异步队列(Kafka/RabbitMQ)→ Worker 消费 → 分块 + embedding + 入库,支持大文档不阻塞 API
2. **增量索引**:文档变更只重新 embedding 变化的 chunk(基于 chunk hash 对比),非全量重灌
3. **混合分块策略**:Markdown 按 H2(已实现)+ 语义分块(基于 embedding 相似度滑动窗口)+ 重叠窗口,解决固定分块切断语义问题
4. **分布式事务补偿**:跨 MySQL/PG 双写引入 Outbox 模式,MySQL 事务内写 outbox 表,定时任务消费 outbox 同步 PG,失败重试
5. **知识图谱自动构建**:文档摄入时 LLM 抽取实体 + 关系,构建知识图谱,支撑 GraphRAG
6. **多格式支持**:PDF/Word/HTML/Excel 解析,Apache Tika 统一抽取
7. **去重修复**:重复检测排除自身 articleId(问题 8),新增文档才做重复检测

### 4.8 工具调用升级:权限沙箱 + 工具市场

**对标**:OpenAI GPTs、Anthropic MCP、Coze 插件

**升级方案**:

```
┌──────────────────────────────────────────────┐
│              Tool Platform                    │
│  ┌────────────┐  ┌────────────┐  ┌────────┐ │
│  │ 权限引擎    │  │ 沙箱执行器  │  │ 工具市场│ │
│  │ RBAC/ABAC  │  │ 容器/WASM  │  │ 注册中心│ │
│  └────────────┘  └────────────┘  └────────┘ │
│  ┌──────────────────────────────────────┐   │
│  │  Tool Execution Pipeline              │   │
│  │  schema 校验 → 鉴权 → 沙箱 → 超时 → 审计│   │
│  └──────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

**关键设计**:
1. **权限引擎**:RBAC + ABAC,用户/角色 × 工具 × 资源 三维权限,如"学生角色不能搜索教师分类"
2. **沙箱执行**:危险工具在 WASM/容器沙箱执行,限制 CPU/内存/网络/文件系统
3. **严格 schema 校验**:按 `@ToolParam` 的 type/required/enum 严格校验 LLM 输出,拒绝非法参数
4. **强制超时熔断**:`ToolExecutor` 基于 `ToolDef.timeoutMs` 强制超时,超时返回 `TIMEOUT` 错误
5. **工具市场**:支持动态注册/发现工具(已有 MCP 雏形),第三方工具经审核后上架
6. **工具版本管理**:工具支持多版本,灰度切换,旧版本保留兼容
7. **执行审计**:所有工具调用记录 `tool_call_log`(userId/tool/args/result/latency/status),支持审计与回放
8. **并行工具调用优化**:当前 `Flux.concat` 串行并发,升级为 `Flux.merge` 真并行 + 全局并发度限制

### 4.9 可观测性升级:OpenTelemetry 全链路

**对标**:Datadog、Grafana Stack、阿里云 ARMS

**升级方案**:

```
┌──────────────────────────────────────────────┐
│  OpenTelemetry 全链路追踪                      │
│  Agent → Post/User(Feign) → LLM/Embedding   │
│  → PG/Redis/MySQL                            │
│  统一 traceId 贯穿,Span 级耗时分析             │
└──────────────────────────────────────────────┘
┌──────────────────────────────────────────────┐
│  指标体系(Micrometer + Prometheus)            │
│  - 业务:会话数/轮次数/意图分布/工具调用分布     │
│  - 性能:P50/P95/P99 延迟、首字延迟、流式速率   │
│  - 成本:token 消耗、LLM 调用成本              │
│  - 质量:检索命中率、引用点击率、用户反馈率      │
└──────────────────────────────────────────────┘
┌──────────────────────────────────────────────┐
│  质量评估(LLM-as-a-Judge)                     │
│  - 每日抽样回答,LLM 自动评分(相关性/准确性/流畅)│
│  - 用户反馈(点赞/点踩)回流意图识别训练         │
│  - 检索质量:点击率/停留时间/重检索率           │
└──────────────────────────────────────────────┘
```

**关键设计**:
1. **OpenTelemetry 集成**:替换 MDC UUID 为 OTel traceId,自动注入 Feign/WebClient/Redis/JDBC 调用,Tempo 可视化
2. **LLM 调用专项指标**:`llm.call{model,intent,success}` 维度的 latency/token/cost 指标
3. **首字延迟(TTFT)专项监控**:从请求到第一个 delta 的时间,P95 < 2s 告警
4. **回答质量自动评估**:每日抽样 1% 回答,LLM-as-a-Judge 评分,低分告警人工复核
5. **用户反馈闭环**:点赞/点踩 → 意图识别训练集;点踩 → 触发回答质量复盘
6. **成本看板**:按用户/会话/模型/意图维度的成本分析,支持预算告警

### 4.10 安全护栏升级:多层防御 + LLM-as-Guard

**对标**:Anthropic Constitutional AI、OpenAI Moderation、Lakera Guard

**升级方案**:

```
输入 → [Lakera Guard 规则] → [LLM Moderation 语义] → [Prompt 注入检测] → Agent
                              ↓
输出 ← [Constitutional AI 校验] ← [敏感词过滤] ← [PII 脱敏] ← LLM
```

**关键设计**:
1. **输入侧三层防御**:
   - Layer 1:正则/关键词(已实现,快)
   - Layer 2:Lakera Guard 或开源 PromptGuard(语义级注入检测)
   - Layer 3:LLM Moderation API(OpenAI/阿里云内容安全,兜底)
2. **输出侧校验**:
   - Constitutional AI(已实现)+ 敏感词过滤 + PII 脱敏(手机号/身份证/邮箱自动打码)
3. **工具调用安全**:
   - 工具参数 schema 严格校验(防 LLM 越权)
   - 工具结果脱敏后回填 LLM(防数据泄露)
4. **内部 API 加固**:
   - mTLS 双向认证 + IP 白名单 + JWT 时效签名,替代静态 token
5. **审计日志**:所有敏感操作(知识库摄入/记忆删除/工具调用)记录不可篡改审计日志

### 4.11 编排架构升级:Workflow 引擎 + 多 Agent

**对标**:LangGraph、AutoGen、CrewAI

**升级方案**:

```
┌──────────────────────────────────────────────┐
│         Agent Workflow Engine(DAG)            │
│  ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐  │
│  │ 意图  │→ │ 检索  │→ │ 推理  │→ │ 回答  │  │
│  └──────┘   └──────┘   └──────┘   └──────┘  │
│     │           │          │          │      │
│     ▼           ▼          ▼          ▼      │
│  条件分支  │  循环  │  并行  │  人工节点       │
└──────────────────────────────────────────────┘
┌──────────────────────────────────────────────┐
│         Multi-Agent 协作                      │
│  Planner Agent → Researcher Agent → Writer    │
│  Supervisor 编排,支持 Agent 间通信            │
└──────────────────────────────────────────────┘
```

**关键设计**:
1. **Workflow DAG 编排**:将当前硬编码的 `prepareContext` 流程抽象为 DAG 工作流,节点可配置(条件分支/循环/并行/人工审批),支持可视化编排
2. **多 Agent 协作**:复杂任务拆分给多个专业 Agent(Planner/Researcher/Writer),Supervisor 编排,类似 AutoGen
3. **Agent 个性化**:不同场景配置不同 Agent persona/prompt/tools,如"学习助手 Agent"vs"资源检索 Agent"
4. **Human-in-the-loop**:敏感操作(如删除记忆/发布内容)支持人工审批节点,LLM 起草 + 人工确认
5. **断点续跑**:长流程支持 checkpoint,中断后可恢复

---

## 五、升级优先级与实施路线图

### 5.1 优先级矩阵

| 优先级 | 升级项 | 收益 | 复杂度 | 建议周期 |
|--------|--------|------|--------|----------|
| P0 | 跨数据源事务补偿(Outbox) | 数据一致性 | 中 | 2 周 |
| P0 | PromptVersion 缓存(问题 7) | 性能 | 低 | 3 天 |
| P0 | 重复检测修复(问题 8) | 功能正确性 | 低 | 1 天 |
| P0 | upsertChunks 事务(问题 2) | 数据安全 | 低 | 1 天 |
| P1 | LLM 多模型路由网关 | 可用性 + 成本 | 高 | 4 周 |
| P1 | OpenTelemetry 全链路追踪 | 可观测性 | 中 | 2 周 |
| P1 | 长期记忆 INFERRED 通道 + 向量检索 | 个性化 | 中 | 3 周 |
| P1 | 工具权限 + schema 校验 + 超时 | 安全 | 中 | 2 周 |
| P2 | Cross-Encoder 重排 + HyDE | 检索质量 | 中 | 3 周 |
| P2 | 事件溯源短期记忆 | 可审计 | 高 | 4 周 |
| P2 | 长期记忆 Graph + 冲突仲裁 | 个性化 | 高 | 4 周 |
| P2 | 语义缓存(意图 + 检索) | 性能 | 中 | 2 周 |
| P3 | Agentic RAG + Self-RAG | 检索质量 | 高 | 6 周 |
| P3 | Workflow 引擎 + 多 Agent | 扩展性 | 高 | 8 周 |
| P3 | LLM-as-Guard 安全护栏 | 安全 | 中 | 3 周 |
| P3 | 知识图谱 + GraphRAG | 检索能力 | 高 | 8 周 |

### 5.2 实施路线图

**Phase 1:稳固根基(1-2 周)**
- 修复 4 个 P0 问题(事务/缓存/去重)
- 补齐长期记忆基础(向量检索/used_memory_ids/冲突仲裁/物理清除)
- 完善工具执行安全(schema 校验 + 超时)

**Phase 2:性能与可观测(3-6 周)**
- LLM 多模型路由网关(降本增效)
- OpenTelemetry 全链路追踪(可观测)
- 语义缓存(意图 + 检索)
- Cross-Encoder 重排 + HyDE(检索质量)

**Phase 3:智能化(7-14 周)**
- 长期记忆知识图谱 + INFERRED 通道
- 事件溯源短期记忆
- Agentic RAG + Self-RAG
- LLM-as-Guard 安全护栏

**Phase 4:平台化(15-24 周)**
- Workflow DAG 引擎 + 可视化编排
- 多 Agent 协作框架
- 工具市场 + 第三方接入
- 知识图谱 + GraphRAG

### 5.3 关键指标目标

| 指标 | 当前 | 目标 |
|------|------|------|
| 首字延迟 P95 | ~3s | < 1.5s |
| LLM 可用性 | 99%(单模型) | 99.95%(多模型降级) |
| LLM 成本 | 100% | -40%(任务路由) |
| 检索命中率 | - | +20%(Cross-Encoder + HyDE) |
| 长期记忆完成度 | 30-35% | 90%+ |
| 全链路追踪覆盖率 | MDC UUID | 100% OTel |
| 数据一致性 | 无保证 | Outbox 最终一致 |

---

## 附录:核心代码引用索引

| 模块 | 核心文件 | 关键行 |
|------|----------|--------|
| 主编排器 | [AgentChatService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) | L111 chat() / L462 prepareContext() / L278 runToolCallLoop() |
| LLM 网关 | [DeepSeekClient.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/DeepSeekClient.java) | L67 chatCompletion() / L99 chatCompletionStream() |
| 上下文工程 | [ContextAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java) | L63 assemble() / L107 降级链 |
| 短期记忆 | [ConversationMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java) | L96 initSession() / L313 appendMessage() |
| 长期记忆 | [LongTermMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java) | L125 loadUserProfile() / L357 upsertMemory() / L504 decayMemories() |
| RAG 检索 | [RetrievalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) | L118 retrieve() / L260 selectConfig() / L408 rrfFusion() |
| 工具调用 | [ToolRegistry.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolRegistry.java) | L27 init() / L96 getToolSchemas() |
| Prompt 工程 | [PromptAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java) | L51 assemble() / L117 formatRetrievalContext() |
| 已知问题 | [code-review-issues.md](file:///d:/WorkSpace-java/CS/docs/agent-design/code-review-issues.md) | 15 个问题详情 |
