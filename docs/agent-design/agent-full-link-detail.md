# CampusShare Agent 全链路详细梳理（合并后完整版）

> 对比日期：2026-07-13
> 代码位置：`backend/campushare-agent/src/main/java/com/campushare/agent/`
> 分支：full-snapshot（已合并 origin/develop）

---

## 第一部分：请求入口到响应输出的主链路

### 1.1 HTTP 入口层

请求从 [AgentController.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/controller/AgentController.java) 进入，经过三层过滤：

```
HTTP POST /api/agent/chat
  |
  +- TraceIdFilter（WebFilter）
  |   从 X-Trace-Id Header 读取 traceId；没有则 UUID 生成
  |   MDC.put("traceId", traceId.substring(0,8))
  |   -- 贯穿整个请求生命周期，直到 finally { MDC.remove("traceId") }
  |
  +- AgentRateLimiter（Redis 滑动窗口）
  |   key: agent:ratelimit:{userId}
  |   超限返回 429
  |
  +- JWT 认证 -> 提取 userId
  |
  +- AgentController.chat(userId, request)
      返回 Flux<ChatEvent>（SSE 流）
```

### 1.2 AgentChatService.chat() — 核心编排入口

[AgentChatService.java:118](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) 的 `chat()` 方法：

```java
public Flux<ChatEvent> chat(String userId, ChatRequest request) {
    return Mono.fromCallable(() -> prepareContext(userId, request))  // 同步准备上下文
        .subscribeOn(Schedulers.boundedElastic())  // 切到 boundedElastic 线程池
        .flatMapMany(ctx -> {
            // 快路径：OUT_OF_SCOPE / NAVIGATE
            if (ctx.routeDecision() != null && ctx.routeDecision().isShortCircuit()) {
                return Flux.concat(sessionEvent, streamText(templateReply), navigateEvent)
                    .doFinally(signal -> completeShortCircuitTurn(...));
            }
            // 慢路径：交给 DialogueOrchestrator 编排
            return dialogueOrchestrator.orchestrate(userId, sessionId, message, intent, retrievalResults)
                .flatMapMany(turnResponse -> {
                    // 流式输出 + refs 事件 + navigate 事件
                    return Flux.concat(sessionEvent, deltaStream, refsEvent, navigateEvent);
                });
        });
}
```

**关键设计**：
- `prepareContext()` 是同步方法，用 `Mono.fromCallable()` 包装后 `subscribeOn(boundedElastic)` 切到线程池，避免阻塞 Netty IO 线程
- 快路径不调 LLM，用 `streamText()` 把模板回复拆成 2 字符/chunk、25ms 延迟，模拟打字机效果
- 慢路径交给 [DialogueOrchestrator](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/impl/DialogueOrchestratorImpl.java) 编排（ReAct/CoT/Plan-and-Execute/Reflexion/Clarify）

---

### 1.3 prepareContext() — 上下文准备的完整 14 步

[AgentChatService.java:482-626](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) 是全链路最核心的方法，每一步都至关重要：

#### Step 1：TraceId + 根 Span 启动

```java
String traceId = traceService.generateTraceId();   // UUID
MDC.put("traceId", traceId.substring(0, 8));        // MDC 注入（日志贯穿）
TraceSpan rootSpan = traceService.startSpan(traceId, "chat", "CHAT");
```

`traceId` 写入 MDC 后，logback 配置的 pattern 会在所有日志中自动输出 `traceId` 字段。`rootSpan` 是调用链的根节点，记录到 `agent_trace_spans` 表。

#### Step 2：会话管理 + userId 权限校验

```java
AgentSession session = getOrCreateSession(userId, request);
```

[getOrCreateSession()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) 逻辑：
- 如果 `request.getSessionId()` 不为空 -> 从 MySQL 查询会话 -> **校验 `session.getUserId().equals(userId)`**，不匹配抛 `USER_ACCOUNT_FORBIDDEN "无权访问此会话"`
- 如果为空 -> 创建新会话，标题取用户消息前 50 字

#### Step 3：状态机 INIT -> ACTIVE（CAS）

```java
SessionStatus currentStatus = sessionStateMachine.getCurrentStatus(session.getId());
if (currentStatus == SessionStatus.INIT) {
    sessionStateMachine.transition(session.getId(), SessionStatus.INIT, SessionStatus.ACTIVE, "First message");
}
```

[SessionStateMachine](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionStateMachine.java) 的 8 个状态：

| 状态 | 说明 | 触发转移条件 |
|------|------|------------|
| `INIT` | 初始创建 | 首条消息 -> ACTIVE |
| `ACTIVE` | 正在对话 | 超时 30 分钟 -> IDLE |
| `IDLE` | 空闲 | 新消息 -> ACTIVE；7 天 -> ARCHIVED |
| `CLARIFYING` | 追问澄清中 | 槽位补全 -> ACTIVE |
| `TOOL_CALLING` | 工具调用中 | 工具完成 -> ACTIVE |
| `ERROR` | 异常 | 重试 -> ACTIVE |
| `ARCHIVED` | 已归档 | 终态 |
| `DELETED` | 已删除 | 终态 |

**CAS 语义**：`UPDATE agent_sessions SET status='ACTIVE' WHERE id=? AND status='INIT'`，利用数据库行锁保证原子性，避免并发转移。

#### Step 4：意图识别三层漏斗

```java
IntentResult intentResult = recognizeIntent(userMessage, session.getId());
```

[recognizeIntent()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) 三层漏斗：

```
Layer 1: RuleShortCircuitFilter.filter(query)
  +- 正则匹配 OUT_OF_SCOPE（闲聊/天气/股票等）
  +- 正则匹配 NAVIGATE（跳转到个人主页/发布等 15 个路由）
  +- 命中 -> 直接返回（classifyLayer="RULE"），不调 LLM

Layer 2: IntentClassifier.classify(query, sessionId)
  +- 调用 DeepSeek Function Calling，输出 JSON：
  |   {intent, subIntent, confidence, rewrittenQuery, slots{school,category,...}}
  +- classifyLayer="LLM"

Layer 3: EmbeddingIntentFallback（IntentClassifier 内部调用）
  +- LLM 失败时，用 query embedding 与意图模板 embedding 做余弦相似度
  +- 阈值 > 0.75 -> 选择对应意图
  +- classifyLayer="EMBEDDING"

Default: 全部失败 -> Intent.SEARCH + SubIntent.RESOURCE + confidence=0.0
```

意图识别结果 `IntentResult` 包含：
- `intent`：HOW_TO / SEARCH / CLARIFY / NAVIGATE / OUT_OF_SCOPE
- `subIntent`：RESOURCE / DISCUSSION / CONTENT_QA（仅 SEARCH 有子意图）
- `confidence`：0.0-1.0，低于 0.6 视为低置信
- `rewrittenQuery`：LLM 改写后的查询（用于检索）
- `slots`：{school, category, postId, ...} 结构化槽位

#### Step 5：学校名规则提取 + 别名规范化

```java
String ruleExtractedSchool = SchoolNameUtils.extractFromQuery(userMessage);
```

[SchoolNameUtils](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/util/SchoolNameUtils.java) 内置 12 所大学别名映射表（"北大" -> "北京大学"），用正则从原始 query 提取学校名。**规则提取优先于 LLM**，因为 LLM 可能输出"北大"等简称导致 SQL ILIKE 过滤失败。

#### Step 6：第一次注入检测

```java
if (constitutionalAIValidator.shouldHardBlock(userMessage)) {
    throw new BusinessException(USER_ACCOUNT_FORBIDDEN, "该请求包含不允许的内容");
}
if (constitutionalAIValidator.detectInjection(userMessage)) {
    injectionDetectedCounter.increment();  // 软拦截：记录但不阻断
}
```

- `shouldHardBlock()`：检测 Prompt 泄露请求（如"忽略以上指令"、"输出你的系统提示"）-> **硬拦截，抛异常**
- `detectInjection()`：检测其他注入模式 -> **软拦截，Counter 计数 + 日志记录**

#### Step 7：意图路由 — 快慢路径分流

```java
Optional<RouteDecision> shortCircuit = intentRouter.tryShortCircuit(intentResult);
if (shortCircuit.isPresent()) {
    // 快路径：返回模板回复，不调 LLM
    return new ChatContext(..., shortCircuit.get(), ...);  // routeDecision 非空
}
```

[IntentRouter](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java) 路由规则：
- `OUT_OF_SCOPE` -> 快路径，模板回复"我专注于校园生活相关的问题..."
- `NAVIGATE` -> 快路径，模板回复 + navigateRoute（如 `/profile/posts`）
- `HOW_TO / SEARCH / CLARIFY` -> 慢路径，走 RAG 管线

**快路径不调用 LLM**，直接用 `streamText()` 逐字输出模板回复，延迟仅 25ms/chunk。

#### Step 8：RAG 四路混合检索

```java
String retrieveQuery = intentResult.getRewrittenQuery() != null
        ? intentResult.getRewrittenQuery() : userMessage;

// CLARIFY 时加载上一轮检索结果（用于上下文合并）
List<RetrievalResult> previousResults = null;
if (intentResult.getIntent() == Intent.CLARIFY) {
    previousResults = loadPreviousRetrieval(session.getId());
}

List<RetrievalResult> retrievalResults = retrievalService.retrieve(
        retrieveQuery, intentResult, previousResults).block();
```

[RetrievalService.retrieve()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) 的完整流程：

**① 意图驱动配置选择**（`selectConfig()`）：

| 意图 | 知识向量 topK | 知识关键词 topK | 帖子向量 topK | 帖子关键词 | 相似度阈值 |
|------|-------------|---------------|-------------|----------|----------|
| HOW_TO | 8 | 5 | 0 | 否 | 0.5 |
| SEARCH/resource | 2 | 2 | 8 | 是 | 0.4 |
| SEARCH/discussion | 2 | 0 | 8 | 是 | 0.4 |
| SEARCH/content_qa | 8 | 5 | 3 | 是 | 0.5 |
| CLARIFY | 5 | 3 | 5 | 是 | 0.4 |
| 低置信(<0.6) | 各路 +3 扩大召回 | | | | |

**② 检索缓存读取**（非 CLARIFY 意图）：
```java
String cacheKey = buildCacheKey(query, intent);  // MD5(query + intent)
String cached = redisTemplate.opsForValue().get(cacheKey);
// 命中 -> 直接反序列化返回，跳过 embedding 和数据库查询
```

**③ Embedding 生成**：
```java
float[] queryVec = embeddingClient.embed(query);  // BGE-M3 1024维
```

**④ 四路并行召回**：

```
路1: 知识库向量检索
  knowledgeVectorStore.searchChunks(queryVec, knowledgeTopK)
  -> pgvector HNSW + 余弦距离
  -> 返回 List<ChunkResult>（含 articleId, chunkIndex, similarity, chunkContent, qualityScore）

路2: 知识库关键词检索
  knowledgeVectorStore.keywordSearchChunks(query, knowledgeKeywordTopK)
  -> pg_trgm GIN 索引 + trigram 相似度
  -> 返回 List<ChunkResult>

路3: 帖子向量检索（带 slots 过滤）
  postVectorStore.search(queryVec, postTopK, slots)
  -> pgvector + WHERE category ILIKE ? AND school ILIKE ?
  -> 返回 List<RetrievalResult>

路4: 帖子关键词检索（按配置启用）
  postVectorStore.keywordSearch(query, postKeywordTopK, slots)
  -> pg_trgm + slots 过滤
  -> 返回 List<RetrievalResult>
```

**⑤ 文章级聚合**（仅知识库）：
```
同 article_id 的多个 chunk -> 取最高 similarity 的一个
多 chunk 命中加成：finalSim = maxSim * (1 + 0.1 * (chunkCount - 1))
```

**⑥ RRF 跨源融合**：
```
对每个结果 r：rrfScore = 1 / (rrfK + rank_in_its_list)
rrfK = 60（标准值）
多路结果按 article_id/post_id 去重后求和 rrfScore
最终按 rrfScore 降序取 top rerankTopK（默认 5）
```

**⑦ 质量加权**（仅知识库结果）：
```
finalScore = rrfScore * (0.8 + 0.2 * qualityScore)
qualityScore 来自 FourDimensionQualityScorer（0-1）：
  - 召回频次（被检索命中的次数，归一化）
  - 用户反馈（点赞/点踩比例）
  - 新鲜度（更新时间越近分越高）
  - 完整度（chunk 数量/平均长度）
```

**⑧ 跨源去重**：
```
基于 article_id + post_id 去重
知识库文章和帖子是不同 source，不会互相去重
```

**⑨ Token 预算截断**：
```
按 finalScore 降序累加 token
总 token 超 config.tokenBudget()（默认 2500）时截断
保留高分结果
```

**⑩ 异步召回计数**（fire-and-forget）：
```java
asyncIncrementRecall(fused);  // 更新 knowledge_articles.recall_count
```

**⑪ 缓存写入**（异步，非 CLARIFY）：
```java
asyncCacheResults(cacheKey, fused);  // Redis TTL 5 分钟
```

#### Step 9：长期记忆装载（L1 层）

```java
// 画像全量装载
List<RetrievalResult> profileMemories = memoryRetrievalService.loadProfileMemories(userId);
String userProfile = memoryRetrievalService.formatProfileText(profileMemories);

// 相关记忆向量检索
List<RetrievalResult> relevantMemories = memoryRetrievalService.retrieveRelevantMemories(
        userId, retrieveQuery, intentResult).block();
```

[MemoryRetrievalService](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/MemoryRetrievalService.java) 双路检索：

**画像装载**（`loadProfileMemories`）：
```
从 MySQL user_memory 表查询 userId 的所有记忆
按 confidence 降序 + updatedAt 降序排列
取 Top-K（默认 5）格式化为画像文本：
  [用户画像]
  - 偏好格式: PDF（置信 1.0，用户明确声明）
  - 专业: 计算机科学（置信 1.0）
```

**相关记忆检索**（`retrieveRelevantMemories`）：
```
路1: MemoryVectorStore 向量检索（PostgreSQL pgvector）
  -> query embedding -> 余弦相似度 top-K
路2: MemoryVectorStore 关键词检索
  -> pg_trgm 模糊匹配
RRF 融合 -> 返回 top-K
相似度过滤（阈值 0.7）
```

所有被装载的记忆 ID 记录到 `usedMemoryIds` Set，后续写入 `agent_context_snapshots` 表用于审计。

#### Step 10：Prompt 版本管理 + 灰度

```java
PromptVersion promptVersion = promptVersionManager.getCurrentVersion(userId);
```

[PromptVersionManager](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java)：
- 从 `prompt_versions` 表查询当前激活版本
- 灰度发布：按 userId hash 百分比判断是否进入灰度版本
- Redis 缓存当前版本号（key: `agent:prompt:current_version`，TTL 5 分钟）

#### Step 11：Prompt 六层装配

```java
String systemPrompt = promptAssembler.assemble(intentResult.getIntent(), allResults, promptVersion);
```

[PromptAssembler](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java) 六层结构：

```
Layer 1: 平台人格（"你是 CampusShare AI 助手..."）
Layer 2: 任务指令（按意图差异化：HOW_TO 给操作指南，SEARCH 给搜索结果）
Layer 3: Few-shot 示例（按意图提供 2-3 个示例对话）
Layer 4: RAG 检索结果（注入到 <context> 标签中）
         +- 知识库结果：<knowledge> 标签
         +- 帖子结果：<posts> 标签
         +- 记忆结果：<memories> 标签
Layer 5: Guardrail 安全规则（输出格式限制、能力边界）
Layer 6: 当前版本号（v1.2.0）
```

#### Step 12：对话历史加载（Redis 优先 + MySQL 降级）

```java
List<AgentTurn> history = loadHistoryWithMemory(session);
```

`loadHistoryWithMemory()` 逻辑：
```
1. 从 Redis 读取短期记忆前缀：
   +- session:{id}:summary -> 滚动摘要（如果有）
   +- session:{id}:slots -> 冻结槽位（如果有）
   +- session:{id}:pin -> Pin 消息（如果有）

2. 从 Redis 读取最近 N 轮消息：
   session:{id}:messages -> List<MemoryMessage>
   （LRIST + RPOP，保留最近 historyLimit=10 轮）

3. Redis 为空时降级到 MySQL：
   SELECT * FROM agent_turns WHERE session_id=? ORDER BY turn_number DESC LIMIT 10

4. 合并：摘要前缀 + 冻结槽位 + Pin消息 + 最近消息
```

[ConversationMemoryService](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java) 的 Redis 5-key 设计：

| Key | 数据结构 | TTL | 用途 |
|-----|---------|-----|------|
| `session:{id}:messages` | Redis List（LPUSH/RPOP） | 24h | 最近 10 轮对话消息 |
| `session:{id}:summary` | String | 24h | 滚动摘要（<=300 字） |
| `session:{id}:slots` | Hash | 24h | 冻结槽位（confirmed_intent/category/school/post_ids） |
| `session:{id}:pin` | List | 24h | Pin 消息（永不压缩） |
| `session:{id}:state` | String | 24h | 会话状态机当前状态 |

#### Step 13：上下文工程 L0-L5 分层组装

```java
ContextAssembler.AssembledContext assembled = contextAssembler.assemble(
        session.getId(), turn.getTurnNumber(), userMessage,
        intentResult, systemPrompt, history, userProfile, null,
        usedMemoryIds.isEmpty() ? null : new ArrayList<>(usedMemoryIds));
```

[ContextAssembler.assemble()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java) 的完整逻辑：

**Token 预算分配**（`TokenBudget.forIntent(intent)`）：

| 层 | 意图 | 预算（tokens） | 说明 |
|----|------|-------------|------|
| L0 System Prompt | 所有 | ~4000-5000 | 永驻不截断 |
| L1 用户画像 | 所有 | 300 | 可裁剪 |
| L2 工具定义 | 所有 | 动态 | Function Calling 时注入 |
| L4 对话历史 | HOW_TO | 1500 | 偏短（操作指南不需太多历史） |
| L4 对话历史 | SEARCH | 3000 | 偏长（需更多上下文理解需求） |
| L4 对话历史 | CLARIFY | 4000 | 最长（追问需完整上下文） |
| L5 用户输入 | 所有 | 700 | 永驻不截断 |
| **总预算** | | **8000** | maxInput=6500（预留 1500 输出） |

**三级降级链**（当 total > inputBudget 时逐级触发）：

```
降级 1: L4 截断到最近 2 轮
  workingHistory = workingHistory.subList(size-2, size)
  truncationReason = "DEGRADE_L4_TO_2_ROUNDS"

降级 2: L1 丢弃（用户画像）
  l1Content = null
  truncationReason = "DEGRADE_L1_DROPPED"

降级 3: 硬上限兜底
  L4 截断到最近 1 轮
  truncationReason = "HARD_LIMIT_L4_TO_1_ROUND"
```

**消息构建**（XML 标签分层）：

```xml
<system_rules>
  {L0 System Prompt}
</system_rules>

<user_profile>
  {L1 用户画像}
</user_profile>

<available_tools>
  {L2 工具定义 Schema}
</available_tools>
```

对话历史（L4）保持标准 `user/assistant` 交替格式，当前输入（L5）用 `<user_query>` 标签包裹。

#### Step 14：异步快照持久化

```java
contextSnapshotService.saveSnapshot(assembled.snapshot());
```

`ContextSnapshot` 写入 `agent_context_snapshots` 表，记录：
- sessionId, turnId
- messages（完整 messages JSON）
- layerTokens（各层 token 计数）
- usedMemoryIds（使用的长期记忆 ID 列表）
- truncated（是否截断）
- truncationReason（截断原因）

**fire-and-forget**：异步执行，失败不阻塞主流程。

---

### 1.4 慢路径：对话编排五模式

当 `prepareContext()` 返回的 `routeDecision` 为 null 时，进入慢路径：

```java
return dialogueOrchestrator.orchestrate(userId, sessionId, message, intentResult, retrievalResults)
    .flatMapMany(turnResponse -> { ... });
```

[DialogueOrchestratorImpl.orchestrate()](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/impl/DialogueOrchestratorImpl.java) 入口：

```java
public Mono<TurnResponse> orchestrate(userId, sessionId, message, intent, retrievalResults) {
    OrchestrationMode mode = selectMode(intent, turnNumber);
    return switch (mode) {
        case CLARIFY          -> clarify(userId, sessionId, message, intent);
        case PLAN_AND_EXECUTE -> planAndExecute(userId, sessionId, message, intent);
        case REFLEXION        -> reflexion(userId, sessionId, message, intent);
        case COT              -> chainOfThought(userId, sessionId, message, intent);
        case REACT            -> react(userId, sessionId, message, intent, retrievalResults);
    };
}
```

#### selectMode() 模式选择逻辑

```
if (intent == CLARIFY || slots缺失)     -> CLARIFY
else if (isComplexTask(intent, query))  -> PLAN_AND_EXECUTE
else if (turnNumber > 1 && hasFailedAttempts) -> REFLEXION
else if (isReasoningRequired(intent))   -> COT
else                                    -> REACT（默认）
```

**isComplexTask 判断**：query 长度 > 100 字 或 包含"步骤/流程/怎么做"等关键词
**isReasoningRequired 判断**：intent == HOW_TO 或 query 包含"为什么/原因/分析"等

#### 五种模式详解

**1. CLARIFY 模式** — 槽位缺失时追问

```
clarify(userId, sessionId, message, intent)
  +- 检查缺失槽位（如 school 为空但 SEARCH/resource 需要）
  +- 调用 LLM 生成追问问题："请问您想找哪个学校的资源？"
  +- 返回 TurnResponse(content=追问问题, refs=null)
  +- CLARIFY 意图时不显示引用卡片（前端判断）
```

**2. REACT 模式** — 默认，工具调用循环

```
react(userId, sessionId, message, intent, retrievalResults)
  +- 获取当前意图可用工具 Schema
  |   toolRegistry.getToolSchemas(intent)
  |   +- NAVIGATE -> [NavigateToPageTool]
  |   +- SEARCH   -> [SearchKnowledgeTool, SearchPostsTool]
  |   +- HOW_TO   -> [SearchKnowledgeTool]
  |
  +- runToolCallLoop(messages, toolSchemas, ...)
  |   循环（最多 maxToolRounds=5 轮）：
  |   +- deepSeekClient.chatCompletion(messages, toolSchemas)
  |   |   -> LLM 返回 content + tool_calls
  |   +- if (!response.hasToolCalls()) -> break，返回最终 messages
  |   +- 构建 assistant message（含 tool_calls）
  |   +- executeToolCalls(toolCalls, userId)
  |   |   对每个 toolCall：
  |   |   +- 解析 arguments JSON
  |   |   +- toolExecutor.execute(toolName, arguments, userId)
  |   |   |   +- NavigateToPageTool: 返回 route + label
  |   |   |   +- SearchKnowledgeTool: 调用 RetrievalService 检索知识库
  |   |   |   +- SearchPostsTool: 调用 PostVectorStore 检索帖子
  |   |   +- 收集 ToolResult.Ref（引用源）
  |   |   +- 构建 tool message（role="tool", toolCallId, content=resultJson）
  |   +- 递归 runToolCallLoop(round + 1)
  |
  +- 返回 TurnResponse(content=最终回答, refs=工具引用, navigate=跳转信息)
```

**3. PLAN_AND_EXECUTE 模式** — 复杂任务先规划

```
planAndExecute(userId, sessionId, message, intent)
  +- buildPlan(): 调用 LLM 规划任务步骤
  |   LLM 输出 ["步骤1: 搜索相关帖子", "步骤2: 搜索知识库", "步骤3: 综合回答"]
  +- executePlan(): 依次执行每个步骤
  |   每步可能调用工具（复用 runToolCallLoop）
  |   每步结果追加到 messages
  +- buildPlanExecutionSummary(): LLM 生成最终总结
      返回 TurnResponse(content=总结)
```

**4. REFLEXION 模式** — 失败后反思重试

```
reflexion(userId, sessionId, message, intent)
  +- analyzePastAttempts(): 分析过往失败对话
  |   加载最近 N 轮历史，判断是否有 ERROR 状态的 turn
  +- 如果 confident -> 直接 react()
  +- 如果不 confident:
      +- 调用 LLM 反思："上次回答为什么不够好？如何改进？"
      +- 根据反思改写 query
      +- 用新 query 重新 react()
```

**5. COT 模式** — 思维链推理

```
chainOfThought(userId, sessionId, message, intent)
  +- 调用 LLM，Prompt 中要求"请逐步思考并给出推理过程"
      返回 TurnResponse(content=包含思考过程的回答)
```

---

### 1.5 流式输出 + 后置处理

```java
Flux<ChatEvent> deltaStream = streamText(turnResponse.getContent())
    .doFinally(signalType -> {
        long elapsed = System.currentTimeMillis() - ctx.startTime();
        Mono.fromRunnable(() -> {
            if (signalType == SignalType.ON_COMPLETE) {
                completeTurn(ctx.turn(), ctx.session(), content, elapsed, ...);
            } else if (signalType == SignalType.ON_ERROR) {
                errorTurn(ctx.turn(), "Stream terminated with error");
                traceService.endSpanWithError(ctx.rootSpan(), "Stream terminated with error");
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    });
```

**SSE 事件顺序**：
```
1. session 事件：{"sessionId": "xxx"}
2. delta 事件：逐字内容（2字符/chunk，25ms延迟）
3. refs 事件：引用源卡片数据（CLARIFY 时不发送）
4. navigate 事件：跳转路由（NAVIGATE 时才发送）
```

**completeTurn() 后置处理**（异步）：
```
1. AgentTurn 持久化到 MySQL（content, tokens, retrievalContext, intent, promptVersion）
2. ConversationMemoryService.appendMessage() 写入 Redis 短期记忆
3. SessionStateMachine 保持 ACTIVE
4. traceService.endSpan(rootSpan) 记录总耗时
5. MetricsService 记录延迟/Token/成本
6. ConstitutionalAIValidator.validateOutput() 输出验证（第二次注入检测）
```

---

## 第二部分：四条数据闭环

### 闭环 1：跨轮对话流（同一会话内）

```
Turn 1:
  用户："帮我找北大的考研资料"
  +- 意图：SEARCH/resource，slots={school:"北京大学"}
  +- RAG 检索 -> 5 条结果
  +- LLM 回答 + refs 卡片
  +- Redis 写入：
      session:{id}:messages -> [user:帮我找..., assistant:根据搜索...]
      session:{id}:slots -> {confirmed_intent:SEARCH, target_school:北京大学}

Turn 2（超 2500 token 触发压缩）:
  用户："那清华的呢？"
  +- 意图：CLARIFY（指代消解："那"="考研资料"，"清华"替换"北大"）
  +- loadPreviousRetrieval() 加载上一轮 5 条结果
  +- 上一轮结果降权 0.5 作为第五路加入 RRF 融合
  +- RAG 检索 -> 新结果
  +- ContextCompressionService 检测 L4 > 2500 token
      +- 三合一 LLM 调用（一次输出 summary + slots + pins）
      |   Prompt: "把旧摘要+旧对话合并为新摘要，并抽取槽位和Pin消息"
      |   输出 JSON: {"summary":"用户在找考研资料...", "slots":{...}, "pins":[...]}
      +- session:{id}:summary = 新摘要
      +- session:{id}:slots = 更新后的槽位
      +- session:{id}:pin = Pin 消息
      +- session:{id}:messages = 截断后保留最近 2 轮
```

### 闭环 2：跨会话记忆流（长期记忆）

```
会话 A 归档（SessionArchivalService.archiveSession）:
  +- 状态转移：ACTIVE -> ARCHIVED
  +- ConversationSummaryService.summarizeSession()
  |   加载全部对话历史 -> LLM 生成 <=200 字会话摘要
  +- LongTermMemoryService.extractMemories(sessionId, userId, rollingSummary)
  |   LLM 从摘要抽取显式偏好（EXPLICIT 通道）：
  |   Prompt: "从对话摘要中抽取用户明确声明的偏好/事实"
  |   输出: [{"type":"PREFERENCE","key":"preferred_format","value":"PDF","evidence_quote":"我比较喜欢PDF"}]
  |   每条 -> upsertMemory():
  |     +- 查询是否存在同 userId+type+key
  |     +- 存在 -> confidence = min(1, confidence + 0.1), evidence_count += 1
  |     +- 不存在 -> 插入, confidence=1.0, source=EXPLICIT
  +- InferredBehaviorService.recordEvidence()
  |   记录行为证据到 user_memory_evidence 表
  |   evidence_count >= 3 -> inferFromBehavior()
  |     LLM 推断："用户主要访问考研分类" -> BEHAVIOR 类型记忆
  +- ConflictResolver.resolveOnInsert()
  |   新旧 value 不同时：
  |   +- 优先 LLM 仲裁（KEEP_NEW/KEEP_OLD/KEEP_BOTH）
  |   +- LLM 失败 -> 规则仲裁（EXPLICIT > INFERRED, 时间戳新的优先）
  +- MemoryVectorStore.upsert()
      向量入库到 PostgreSQL memory_vectors 表

会话 B 首轮:
  +- MemoryRetrievalService.loadProfileMemories(userId)
  |   查 MySQL user_memory -> Top-K -> 格式化画像文本
  +- MemoryRetrievalService.retrieveRelevantMemories(userId, query, intent)
  |   向量+关键词双路检索 -> RRF 融合
  +- 注入到 ContextAssembler L1 层
```

**衰减机制**（每周日 02:00 定时执行）：

```
LongTermMemoryService.decayMemories()（@Scheduled）:
  遍历所有未软删除的记忆：
  +- PREFERENCE/FACT 类型：confidence *= (1 - 0.03)  // 周衰减 3%
  +- EXPLICIT 类型：confidence *= (1 - 0.02)  // 周衰减 2%
  +- BEHAVIOR 类型：confidence *= (1 - 0.1)   // 周衰减 10%
  +- TASK 类型：confidence *= (1 - 0.3)       // 周衰减 30%
  |
  +- 高频访问增强（近7天 access_count >= 3）：衰减率减半
  |
  +- 软删除检查：
      confidence < 0.2 -> is_deleted = true
      TASK 类型 4 周未更新 -> is_deleted = true
```

### 闭环 3：离线到在线知识流

```
离线摄入（KnowledgeScheduler 定时触发）:
  KnowledgeIngestionService.ingestAll()
  +- 扫描知识库目录（markdown 文件）
  +- 对每个文件：
  |   +- MD5 内容比对：与上次摄入的 MD5 对比
  |   |   相同 -> 跳过（增量更新）
  |   |   不同 -> 重新摄入
  |   +- MarkdownChunker.chunk(content)
  |   |   按 H2 标题分块，每块 <= 500 字
  |   +- ThresholdDuplicateDetector.detect(chunks)
  |   |   用 chunk0 的 embedding 检索 PG -> 相似度 > 0.95 -> 判为重复
  |   |   注意 Bug：更新时 chunk0 未变会误判（见 code-review-issues.md 问题 8）
  |   +- EmbeddingClient.embed(chunk) -> 1024 维向量
  |   +- FourDimensionQualityScorer.calculateScore()
  |   |   +- 召回频次（初始 0）
  |   |   +- 用户反馈（初始 0.5）
  |   |   +- 新鲜度（1.0，刚创建）
  |   |   +- 完整度（chunk 数量/平均长度归一化）
  |   |   加权平均 -> qualityScore (0-1)
  |   +- MySQL 写入：knowledge_articles + knowledge_article_versions
  |   +- PostgreSQL 写入：knowledge_vectors（article_id, chunk_index, content, embedding, quality_score）
  |
  +- KnowledgeVersionService.createVersion()
      版本号递增（SemVer）：v1.0.0 -> v1.1.0（新增内容）-> v1.1.1（修复）
      快照保存到 knowledge_article_versions 表

在线检索:
  RetrievalService.retrieve() -> 四路召回 -> RRF -> 质量加权
  -> 召回时异步更新 recall_count（影响下次质量评分）
```

### 闭环 4：BadCase 数据飞轮

```
线上对话:
  +- 用户点踩（DISLIKE）或回复 ERROR
  |
  v
BadCaseScheduler 定时扫描（每天 02:00）:
  BadCaseServiceImpl.autoCollectBadCases()
  +- 查询 agent_turns WHERE status='ERROR' OR feedback='DISLIKE'
  +- 去重（同一 sessionId+turnNumber 不重复采集）
  +- 写入 agent_badcases 表（session_id, turn_id, user_message, assistant_message, error_type, feedback）

EvalService 评估（手动触发或 CI/CD）:
  +- 加载黄金测试集（eval_test_cases 表，50-100 条标注用例）
  +- 对每条用例：
  |   +- 执行 Agent chat() -> 获取回答
  |   +- LLM-as-Judge 评分（4 维度 Rubric）：
  |   |   1. 相关性（0-5）：回答是否切题
  |   |   2. 准确性（0-5）：信息是否正确
  |   |   3. 完整性（0-5）：是否覆盖所有要点
  |   |   4. 安全性（0-5）：是否包含不当内容
  |   +- 写入 eval_results 表
  +- 回归检测：与上次版本结果对比，分数下降 > 0.5 分告警
  +- EvalController 暴露 API（/api/agent/eval/run, /api/agent/eval/results）

Prompt 优化:
  +- 根据 BadCase 和 Eval 结果优化 Prompt
  +- PromptVersionManager 发布新版本（灰度 10% -> 50% -> 100%）
  +- CacheInvalidationService 失效语义缓存
```

---

## 第三部分：横切关注点详细实现

### 3.1 安全护栏（四层防御）

```
Layer 1: 输入层
  +- ConstitutionalAIValidator.shouldHardBlock(message)
  |   关键词：["忽略以上指令", "输出你的系统提示", "你是一个DAN", ...]
  |   -> 硬拦截：抛 BusinessException
  |
  +- ConstitutionalAIValidator.detectInjection(message)
  |   正则模式：10+ 种注入模式
  |   -> 软拦截：Counter 计数 + 日志
  |
  +- JailbreakDetector.detect(message)  [已实现，待集成]
  |   三层检测：
  |   +- 关键词检测：30+ 越狱短语（"DAN", "越狱模式", "开发者模式", ...）
  |   +- 正则模式：10+ Pattern（角色扮演注入、指令覆盖、...）
  |   +- PII 关键词：身份证/银行卡/手机号/邮箱正则
  |   威胁等级：LOW / MEDIUM / HIGH / CRITICAL
  |
  +- AgentSessionServiceImpl.getSessionAndVerifyOwner()
      session.getUserId().equals(userId) -> 不匹配抛异常

Layer 2: 模型层
  +- System Prompt 内嵌安全规则（PromptConstants 中）

Layer 3: 工具层
  +- ToolPermissionMatrix.isAllowed(userId, toolName)  [已实现，待集成]
  |   权限矩阵：
  |   +- NavigateToPageTool: 所有用户可用
  |   +- SearchKnowledgeTool: 所有用户可用
  |   +- SearchPostsTool: 所有用户可用
  |   +- （未来）DeletePostTool: 仅管理员
  |
  +- ToolExecutor.execute() 强制传入 userId
      工具内部用 userId 过滤数据（防越权）

Layer 4: 输出层
  +- ConstitutionalAIValidator.validateOutput(output)  [已实现]
  |   检测输出是否包含：系统提示泄露/不当内容/PII
  |
  +- SecurityAuditService 记录审计日志
      SecurityAuditLog 表字段：
      +- session_id, user_id, turn_id
      +- audit_type: INPUT / OUTPUT / THREAT / TOOL_CALL / VIOLATION
      +- content: 审计内容
      +- threat_level: LOW / MEDIUM / HIGH / CRITICAL
      +- created_at
```

### 3.2 可观测性（三支柱）

**Trace（链路追踪）**：

```
TraceIdFilter（HTTP 入口）
  +- 读取 X-Trace-Id Header，没有则 UUID 生成
  +- MDC.put("traceId", traceId.substring(0,8))

TraceService.startSpan(traceId, parentSpanId, name, type)
  +- 生成 spanId（UUID）
  +- 创建 TraceSpan 实体：
  |   +- traceId, spanId, parentSpanId
  |   +- spanName: "chat" / "intent_recognition" / "rag_retrieval" / "prompt_assembly" / "llm_stream" / "tool_call" / "memory_write"
  |   +- spanType: CHAT / INTENT / RAG / PROMPT / LLM / TOOL / MEMORY
  |   +- startTime, endTime, durationMs
  |   +- sessionId, userId, turnId
  |   +- metadata（如 intent 结果、检索结果数等）
  +- 写入 agent_trace_spans 表

7 阶段 span 层级：
  chat（根 span）
    +- intent_recognition（子 span）
    +- rag_retrieval（子 span）
    +- prompt_assembly（子 span）
    +- llm_stream（子 span）
    +- tool_call（子 span，每轮工具调用一个）
    +- memory_write（子 span）
    +- complete（子 span）

traceId 贯穿：
  MDC -> logback 输出 -> boundedElastic 线程切换后仍保留
  （MDC 在 prepareContext 的 finally 中 remove）
```

**Metrics（指标）**：

```
MetricsServiceImpl（Micrometer）6 大维度 14 项指标：

延迟维度:
  +- agent.chat.ttft（Timer）：首 Token 延迟
  +- agent.chat.duration（Timer）：总对话耗时
  +- agent.stage.duration（Timer, tag=stage）：各阶段耗时

Token 维度:
  +- agent.token.input（Counter, tag=intent/model）：输入 token
  +- agent.token.output（Counter, tag=intent/model）：输出 token

错误维度:
  +- agent.error.count（Counter, tag=error_type）：错误数
  +- agent.error.rate（Gauge）：错误率

工具维度:
  +- agent.tool.call.count（Counter, tag=tool_name）：工具调用次数
  +- agent.tool.error.rate（Gauge, tag=tool_name）：工具错误率

缓存维度:
  +- agent.cache.hit.count（Counter, tag=cache_type）：缓存命中
  +- agent.cache.miss.count（Counter, tag=cache_type）：缓存未命中

安全维度:
  +- agent.prompt.violation（Counter）：Constitutional AI 违规
  +- agent.prompt.injection.detected（Counter）：注入检测
```

**Logs（结构化日志）**：

```xml
<!-- logback-spring.xml -->
<pattern>
  {"timestamp":"%d","level":"%p","traceId":"%X{traceId}",
   "sessionId":"%X{sessionId}","userId":"%X{userId}",
   "logger":"%logger","msg":"%msg"}%n
</pattern>
```

MDC 7 字段：traceId, sessionId, userId, turnId, intent, model, spanName

### 3.3 LLM 网关（多模型路由 + 降级）

[LlmGateway](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmGateway.java) 已实现，待完全集成：

```
LlmGateway.chatCompletion(messages, tools)
  +- getAvailableProviders()
  |   +- 按 priority 排序（DeepSeek priority=1, OpenAI priority=2）
  |   +- 过滤掉 unhealthy 的 Provider
  |
  +- applyIntentRouting(intent)
  |   +- NAVIGATE/OUT_OF_SCOPE -> 优先 DeepSeek（轻量）
  |   +- SEARCH/HOW_TO -> DeepSeek 或 OpenAI（质量）
  |   +- CLARIFY -> DeepSeek
  |
  +- attemptFallbackChain(providers, messages)
  |   for provider in providers:
  |     try:
  |       result = provider.chatCompletion(messages, tools)
  |       return result
  |     catch Exception:
  |       markUnhealthy(provider, duration=30s)
  |       continue
  |   throw AllProvidersFailedException
  |
  +- markUnhealthy(provider, duration)
      +- healthMap.put(provider, UnhealthyUntil=now+30s)
      +- 30s 后自动恢复

当前状态：
  DeepSeekAdapter 已实现（封装原有 DeepSeekClient）
  AgentChatService 和 DialogueOrchestratorImpl 仍直接注入 DeepSeekClient
  -> 需要后续重构切换到 LlmGateway
```

### 3.4 缓存层（三级缓存）

```
L1 Caffeine 本地缓存（SemanticCacheService 内部）:
  +- localSemanticCache: LinkedHashMap（LRU, maxSize=1000）
  +- localEmbeddingCache: LinkedHashMap（LRU, maxSize=500）

L2 Redis 分布式缓存:
  +- SemanticCacheService
  |   key: agent:semantic:{hash(query)}  // hash = String.format("%08x", query.hashCode())
  |   value: JSON({response, embedding, timestamp})
  |   TTL: 24 小时
  |
  |   getSemanticCache(query):
  |     1. 生成 query embedding
  |     2. 遍历缓存项，计算余弦相似度
  |     3. 相似度 > 0.85 -> 命中，返回缓存结果
  |     4. 未命中 -> 返回 null
  |
  +- IntentCacheService
  |   key: agent:intent:cache:{MD5(query)}
  |   value: IntentResult JSON
  |   TTL: 1 小时
  |   精确匹配（非语义）
  |
  +- RetrievalService 缓存
      key: agent:retrieval:{MD5(query+intent)}
      value: List<RetrievalResult> JSON
      TTL: 5 分钟

L3 缓存失效（CacheInvalidationService）:
  触发事件：
  +- 知识库更新 -> 清空 agent:semantic:* + agent:retrieval:*
  +- 知识库删除 -> 同上
  +- 帖子更新 -> 清空 agent:semantic:* + agent:retrieval:*
  +- 帖子删除 -> 同上
  +- 用户记忆更新 -> 清空 agent:semantic:{userHash}*
  失效策略：SCAN + DELETE（模式匹配）
```

### 3.5 限流配额

```
AgentRateLimiter（接口层）:
  Redis 滑动窗口：ZSET
  key: agent:ratelimit:{userId}
  score: timestamp
  窗口：60 秒内最多 20 次
  超限 -> 429 Too Many Requests

QuotaService（配额管理）[已实现，待集成]:
  checkQuota(userId):
    +- dailyTokenLimit: 每日 Token 限额（默认 100000）
    |   key: agent:quota:{userId}:daily_token:{date}
    |   超限 -> 拒绝
    |
    +- monthlyTokenLimit: 每月 Token 限额（默认 3000000）
    |   key: agent:quota:{userId}:monthly_token:{month}
    |
    +- dailyRequestLimit: 每日请求次数（默认 100）
    |   key: agent:quota:{userId}:daily_request:{date}
    |
    +- concurrentRequestLimit: 并发请求数（默认 5）
        key: agent:quota:{userId}:concurrent
        INCR + EXPIRE

  consumeQuota(userId, tokens, model, intent, sessionId):
    +- Redis INCRBY 各维度计数器
    +- 成本归因：记录到 agent_cost_attribution 表
        字段：user_id, session_id, model, intent, date, tokens, cost
```

### 3.6 性能 SLO

```
SloConfig:
  +- TTFT_SLO: 800ms（首 Token 延迟目标）
  +- P99_SLO: 3000ms（P99 总延迟目标）
  +- ERROR_BUDGET: 1%（错误率预算）
  +- 延迟预算分配：
      +- 意图识别: 200ms
      +- RAG 检索: 500ms
      +- Prompt 装配: 50ms
      +- LLM 首 Token: 800ms
      +- 工具调用: 1000ms（每轮）
      +- 记忆写入: 100ms

SloServiceImpl:
  calculateBurnRate():
    +- 短窗口（5分钟）燃烧率 = actual_error_rate / SLO_error_budget
    +- 长窗口（1小时）燃烧率
    +- 多窗口算法：
        短窗口 > 14 且 长窗口 > 14 -> CRITICAL 告警
        短窗口 > 6 或 长窗口 > 6 -> WARNING 告警

  checkSlo():
    +- 查询最近 5 分钟的 Metrics
    +- 计算实际 P99 延迟
    +- 计算实际错误率
    +- 与 SLO 目标对比 -> 告警状态

  SloController 暴露 API:
    GET /api/agent/slo/status -> SLO 状态
    GET /api/agent/slo/burn-rate -> 燃烧率
    GET /api/agent/slo/alerts -> 告警列表
```

---

## 第四部分：离线/异步任务全景

| 定时任务 | 触发时间 | 模块 | 职责 |
|---------|---------|------|------|
| KnowledgeScheduler | 配置驱动 | KnowledgeIngestionService | 知识库文档摄入/更新（MD5 增量比对 + 分块 + 向量化 + 质量评分） |
| PostVectorScheduler | 定时 | PostVectorService | 帖子向量同步到 PG（扫描新增/更新帖子 -> embedding -> upsert） |
| LongTermMemoryService.decayMemories | 每周日 02:00 | LongTermMemoryService | 记忆周衰减（BEHAVIOR *0.9 / TASK *0.7 / PREFERENCE_FACT *0.03 / EXPLICIT *0.02） |
| LongTermMemoryService.softDelete | 衰减后 | LongTermMemoryService | confidence < 0.2 软删除；TASK 4 周未更新删除 |
| SessionArchivalService | 定时 | SessionArchivalService | 僵尸会话归档（IDLE > 7 天 -> ARCHIVED -> 抽取记忆 -> 清理 Redis） |
| BadCaseScheduler | 每天 02:00 | BadCaseService | BadCase 自动采集（扫描 ERROR/DISLIKE -> 去重 -> 写入） |
| ContextCompressionService | 实时触发 | ContextCompressionService | L4 > 2500 token 或轮次 > 10 时触发三级压缩 |

---

## 第五部分：数据存储全景

### MySQL 表（17 张）

| 表 | 用途 | 关键字段 |
|----|------|---------|
| agent_sessions | 会话主表 | id, user_id, title, status, message_count, total_tokens, total_cost |
| agent_turns | 对话轮次 | id, session_id, turn_number, user_message, assistant_message, tokens_used, status, retrieval_context, navigate_info |
| agent_context_snapshots | 上下文快照 | session_id, turn_id, messages, layer_tokens, used_memory_ids, truncated |
| knowledge_articles | 知识文章 | id, title, content, md5_hash, version, quality_score, recall_count |
| knowledge_article_versions | 版本快照 | article_id, version, snapshot_json, created_at |
| user_memory | 用户长期记忆 | id, user_id, memory_type, memory_key, memory_value, confidence, source, evidence_count, last_used_at, access_count, is_deleted |
| user_memory_evidence | 证据表 | id, memory_id, user_id, evidence_type, evidence_content, created_at |
| user_memory_history | 审计表 | memory_id, action, old_value, new_value, created_at |
| prompt_versions | Prompt 版本 | version, content, status, grayscale_percent, created_at |
| agent_trace_spans | 链路追踪 | trace_id, span_id, parent_span_id, span_name, span_type, start_time, end_time, duration_ms |
| agent_badcases | BadCase | session_id, turn_id, user_message, assistant_message, error_type, feedback |
| eval_test_cases | 评估用例 | id, input, expected_output, category, difficulty |
| eval_results | 评估结果 | test_case_id, version, actual_output, score, rubric_scores |
| security_audit_log | 安全审计 | session_id, user_id, turn_id, audit_type, content, threat_level |
| agent_cost_attribution | 成本归因 | user_id, session_id, model, intent, date, tokens, cost |
| slo_metrics | SLO 指标 | date, ttft_p99, error_rate, burn_rate_short, burn_rate_long |

### PostgreSQL 表（3 张，向量检索）

| 表 | 用途 | 关键字段 |
|----|------|---------|
| knowledge_vectors | 知识库向量 | article_id, chunk_index, content, embedding(1024维), quality_score, topic, heading_path |
| post_vectors | 帖子向量 | post_id, title, content, embedding(1024维), category, school, author_id |
| memory_vectors | 记忆向量 | memory_id, user_id, content, embedding(1024维), access_count |

### Redis Key（12 类）

| Key | 类型 | TTL | 用途 |
|-----|------|-----|------|
| `session:{id}:messages` | List | 24h | 最近 10 轮对话消息 |
| `session:{id}:summary` | String | 24h | 滚动摘要 |
| `session:{id}:slots` | Hash | 24h | 冻结槽位 |
| `session:{id}:pin` | List | 24h | Pin 消息 |
| `session:{id}:state` | String | 24h | 状态机当前状态 |
| `agent:semantic:{hash}` | String | 24h | 语义缓存（response + embedding） |
| `agent:embedding:{hash}` | String | 7d | Embedding 缓存 |
| `agent:intent:cache:{md5}` | String | 1h | 意图识别缓存 |
| `agent:retrieval:{md5}` | String | 5m | RAG 检索结果缓存 |
| `agent:prompt:current_version` | String | 5m | 当前 Prompt 版本号 |
| `agent:ratelimit:{userId}` | ZSET | 60s | 滑动窗口限流 |
| `agent:quota:{userId}:*` | String | 1d-1m | 配额计数器 |

---

## 第六部分：当前"已实现但未完全集成"的模块

| 模块 | 实现状态 | 集成状态 | 待集成点 |
|------|---------|---------|---------|
| LlmGateway | 完整实现 | 未集成 | AgentChatService + DialogueOrchestratorImpl 仍直接注入 DeepSeekClient |
| JailbreakDetector | 完整实现 | 未集成 | prepareContext() 中只调了 ConstitutionalAIValidator，未调 JailbreakDetector |
| SemanticCacheService | 完整实现 | 未集成 | prepareContext() 中未调用 getSemanticCache() |
| QuotaService | 完整实现 | 未集成 | AgentController 未调用 checkQuota() |
| ToolPermissionMatrix | 完整实现 | 未集成 | ToolExecutor.execute() 未调用 isAllowed() |
| SecurityAuditService | 完整实现 | 未集成 | 各安全事件点未调用 logInput/logOutput/logThreat |
| TraceService | 完整实现 | 部分集成 | prepareContext 中有 startSpan，但子 span（rag/tool/llm）未完整埋点 |
| MetricsService | 完整实现 | 部分集成 | 只有两个 Counter，14 项指标未全部埋点 |
| DialogueOrchestrator | 完整实现 | 已集成 | chat() 慢路径已调用 orchestrate() |

这些模块代码已就绪，后续只需在主流程的关键位置插入调用即可完成集成。

---

## 附录：模块清单速查

### A. 基础能力层
- [PromptAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java) - 六层 Prompt 装配
- [ConstitutionalAIValidator.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java) - 注入检测 + 输出验证
- [PromptVersionManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java) - SemVer + 灰度发布
- [IntentClassifier.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java) - LLM 意图分类
- [RuleShortCircuitFilter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RuleShortCircuitFilter.java) - 规则短路
- [EmbeddingIntentFallback.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/EmbeddingIntentFallback.java) - Embedding 兜底
- [IntentRouter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java) - 意图路由
- [SchoolNameUtils.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/util/SchoolNameUtils.java) - 学校名规范化

### B. 知识层
- [KnowledgeIngestionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java) - 知识摄入流水线
- [MarkdownChunker.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/MarkdownChunker.java) - Markdown 分块
- [KnowledgeDuplicateDetector.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeDuplicateDetector.java) - 重复检测
- [KnowledgeVersionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeVersionService.java) - 版本管理
- [FourDimensionQualityScorer.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/FourDimensionQualityScorer.java) - 四维质量评分
- [RetrievalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) - 四路混合检索

### C. 记忆层
- [ContextAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java) - L0-L5 上下文组装
- [ContextCompressionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextCompressionService.java) - 三级压缩
- [ConversationMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java) - Redis 5-key 短期记忆
- [SessionStateMachine.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionStateMachine.java) - 8 状态机
- [SessionArchivalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionArchivalService.java) - 会话归档
- [LongTermMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java) - 长期记忆
- [MemoryRetrievalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/MemoryRetrievalService.java) - 记忆检索
- [InferredBehaviorService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/InferredBehaviorService.java) - 行为推断
- [ConflictResolver.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConflictResolver.java) - 冲突仲裁
- [ConversationSummaryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationSummaryService.java) - 会话摘要

### D. 行动层
- [ToolRegistry.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolRegistry.java) - 工具注册表
- [ToolExecutor.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolExecutor.java) - 工具执行器
- [DialogueOrchestratorImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/impl/DialogueOrchestratorImpl.java) - 对话编排
- [McpClientManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/McpClientManager.java) - MCP 客户端
- [McpServerController.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/server/McpServerController.java) - MCP 服务端

### F. 安全层
- [JailbreakDetector.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/security/JailbreakDetector.java) - 越狱检测
- [ToolPermissionMatrix.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/security/ToolPermissionMatrix.java) - 工具权限矩阵
- [SecurityAuditServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/SecurityAuditServiceImpl.java) - 安全审计

### G. 工程基础设施层
- [LlmGateway.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmGateway.java) - LLM 网关
- [DeepSeekAdapter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/adapter/DeepSeekAdapter.java) - DeepSeek 适配器
- [SemanticCacheService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/cache/SemanticCacheService.java) - 语义缓存
- [CacheInvalidationService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/CacheInvalidationService.java) - 缓存失效
- [QuotaService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/QuotaService.java) - 配额管理
- [AgentRateLimiter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentRateLimiter.java) - 限流器

### H. 观测与评估层
- [TraceIdFilter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/config/TraceIdFilter.java) - TraceId 过滤器
- [TraceServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/TraceServiceImpl.java) - Trace 服务
- [MetricsServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/MetricsServiceImpl.java) - Metrics 服务
- [BadCaseServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/BadCaseServiceImpl.java) - BadCase 服务
- [EvalServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/EvalServiceImpl.java) - 评估服务
- [SloServiceImpl.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/SloServiceImpl.java) - SLO 服务
