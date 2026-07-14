# CampusShare Agent 规划方向 vs 代码实现状态对比

> 对比日期：2026-07-14（最新更新）
> 规划文档：`docs/agent-design/Agent搭建系列文档规划指南.md`（8 大层 23 个方向）
> 代码位置：`backend/campushare-agent/src/main/java/com/campushare/agent/`
> 验证方式：源码走查 + 设计文档对照

---

## 一、总览表

| # | 方向 | 层 | 核心方向？ | 完成度 | 状态 |
|---|------|----|---------|--------|------|
| 1 | System Prompt 工程 | A | ✅ 核心 | 85% | 🟢 已实现 |
| 2 | 意图识别与路由 | A | ✅ 核心 | 95% | 🟢 已实现 |
| 3 | 多模态理解 | A | ⏳ 扩展 | 0% | ⚪ 未实现 |
| 4 | 知识库管理（RAG 数据侧） | B | ✅ 核心 | 90% | 🟢 已实现 |
| 5 | RAG 检索增强（查询侧） | B | ✅ 核心 | 85% | 🟢 已实现 |
| 6 | 知识图谱 | B | ⏳ 扩展 | 0% | ⚪ 未实现 |
| 7 | 上下文工程 | C | ✅ 核心 | 90% | 🟢 已实现 |
| 8 | 长期记忆 | C | ✅ 核心 | 95% | 🟢 已实现 |
| 9 | 工具调用（Function Calling） | D | ✅ 核心 | 85% | 🟢 已实现 |
| 10 | MCP 协议 | D | ✅ 核心 | 80% | 🟢 已实现 |
| 11 | 代码执行沙箱 | D | ⏳ 扩展 | 0% | ⚪ 未实现 |
| 12 | 对话编排 | E | ✅ 核心 | 70% | 🟡 部分实现 |
| 13 | 多 Agent 协作 | E | ⏳ 扩展 | 0% | ⚪ 未实现 |
| 14 | 安全护栏 | F | ✅ 核心 | 85% | 🟢 已实现 |
| 15 | 内容审核与 PII 脱敏 | F | ⏳ 扩展 | 10% | 🟠 仅基础 |
| 16 | LLM 网关与多模型路由 | G | ✅ 核心 | 70% | 🟢 已实现 |
| 17 | 缓存层 | G | ⏳ 扩展 | 85% | 🟢 已实现 |
| 18 | 限流配额与成本控制 | G | ⏳ 扩展 | 70% | 🟡 部分实现 |
| 19 | 可观测性 | H | ✅ 核心 | 85% | 🟢 已实现 |
| 20 | 评估体系 | H | ✅ 核心 | 80% | 🟢 已实现 |
| 21 | A/B 测试与灰度发布 | H | ⏳ 扩展 | 30% | 🟡 部分实现 |
| 22 | 分层部署与在离线协同 | G | ✅ 核心 | 15% | 🟠 仅基础 |
| 23 | 性能 SLO 工程 | H | ✅ 核心 | 75% | 🟡 部分实现 |

**统计**：
- 🟢 已实现（≥80%）：13 个方向（新增 2 个：缓存层、可观测性提升）
- 🟡 部分实现（30%-79%）：5 个方向（缓存层从部分实现升级为已实现）
- 🟠 仅基础（10%-29%）：2 个方向（无变化）
- ⚪ 未实现（<10%）：3 个方向（无变化）

**核心方向完成情况**（15 个核心方向）：已实现 12 个，部分实现 2 个，仅基础 1 个，未实现 0 个。

---

## 二、阶段一：基础能力层（A 层）

### 方向 1：System Prompt 工程 — 85% 🟢

**已实现**：
- [PromptAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptAssembler.java) 六层结构装配（System / 用户画像 / 工具 Schema / RAG 结果 / 历史 / 当前输入）
- [ConstitutionalAIValidator.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java) 注入检测 + 输出验证（关键词 + 系统提示检测，完成度约 80%）
- [PromptVersionManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java) SemVer 版本管理 + 灰度发布机制
- [PromptConstants.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptConstants.java) 人格 / 能力边界 / 输出格式

**缺失项**：
- Constitutional AI 5 条规则未完整实现（仅保留关键词检测，缺角色 / 能力 / 指令 / 隐式指令 / 信息锁定的完整规则）
- 注入对抗测试集（8 大攻击模式 + 100 条测试用例）未建立
- 秒级回滚机制未完整实现

---

### 方向 2：意图识别与路由 — 95% 🟢

**已实现**：
- [IntentClassifier.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java) 三层漏斗完整实现：
  - [RuleShortCircuitFilter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RuleShortCircuitFilter.java) 规则短路
  - LLM 分类（DeepSeek Function Calling）
  - [EmbeddingIntentFallback.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/EmbeddingIntentFallback.java) Embedding 降级
- [IntentRouter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentRouter.java) 路由策略：OUT_OF_SCOPE / NAVIGATE 走快路径模板回复，HOW_TO / SEARCH / CLARIFY 走慢路径
- [Intent.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/enums/Intent.java) 五类意图体系
- [SchoolNameUtils.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/util/SchoolNameUtils.java) 学校名规范化 + 槽位提取
- [IntentCacheService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentCacheService.java) 意图缓存

**缺失项**：
- 路由策略对 HOW_TO / SEARCH / CLARIFY 仍统一走 RAG 路径，未做细分差异化处理（约 70% 完整）
- 别名规范化只针对学校，扩展到其他板块（音乐 / 动漫）需补充配置

---

### 方向 3：多模态理解 — 0% ⚪

**未实现**：
- 无多模态 Embedding
- 无图文混合输入处理
- 无语音转文字

---

## 三、阶段二：知识层（B 层）

### 方向 4：知识库管理（RAG 数据侧） — 90% 🟢

**已实现**：
- [KnowledgeIngestionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java) 完整摄入流水线：上传 → 解析 → 清洗 → 分块 → Embedding → 入库
- [MarkdownChunker.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/MarkdownChunker.java) Markdown 结构分块（按 H2 标题）
- [KnowledgeDuplicateDetector.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeDuplicateDetector.java) + [ThresholdDuplicateDetector.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ThresholdDuplicateDetector.java) 重复检测（用 chunk0 的 embedding）
- [KnowledgeVersionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeVersionService.java) 版本管理（SemVer）+ 快照
- [FourDimensionQualityScorer.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/FourDimensionQualityScorer.java) 四维质量评分（召回频次 / 用户反馈 / 新鲜度 / 完整度，100% 完整）
- MD5 内容比对实现增量更新
- 双数据库存储：MySQL 主表 + PostgreSQL 向量库
- [PostVectorService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/PostVectorService.java) + [PostVectorScheduler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/config/PostVectorScheduler.java) 帖子库自动同步

**缺失项**：
- 重复检测存在 Bug：更新文档时若 chunk0 未变会误判为重复跳过更新（详见 [code-review-issues.md](file:///d:/WorkSpace-java/CS/docs/agent-design/code-review-issues.md) 问题 8）
- 知识库治理（过期清理、版本回滚链路）部分实现

---

### 方向 5：RAG 检索增强（查询侧） — 85% 🟢

**已实现**：
- [RetrievalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) 四路混合召回：
  - 知识库向量召回（pgvector）
  - 知识库 pg_trgm 模糊匹配
  - 帖子向量召回 + slots 过滤
  - 帖子标题 / 内容 BM25-like 检索
- RRF（Reciprocal Rank Fusion）跨源融合
- 质量加权（结合 FourDimensionQualityScorer 评分）
- 跨源去重（基于 article_id + post_id）
- Token 预算截断（按 score 降序保留）
- 异步召回计数
- 帖子文章聚合检索
- 查询缓存

**缺失项**：
- Cross-encoder 重排模型未接入（当前用质量评分代替）
- 缓存命中 / 写入跳过 clarify 的逻辑需细化

---

### 方向 6：知识图谱 — 0% ⚪

**未实现**：
- 无实体抽取
- 无关系建模
- 无子图检索
- 无多跳推理

---

## 四、阶段三：记忆层（C 层）

### 方向 7：上下文工程 — 90% 🟢

**已实现**：
- [ContextAssembler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextAssembler.java) L0-L5 六层装载：
  - L0 System Prompt
  - L1 用户画像（长期记忆注入）
  - L2 工具 Schema
  - L3 RAG 检索结果
  - L4 对话历史
  - L5 当前输入
- [TokenCounter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/util/TokenCounter.java) + TokenBudget 预算管理
- [ContextCompressionService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextCompressionService.java) 三级渐进压缩（90% 完整）：
  - Rolling Summary 滚动摘要
  - Slot Freezing 槽位冻结
  - Pin Message 钉住消息
- [SessionStateMachine.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionStateMachine.java) 8 状态机 + CAS 语义
- [ContextSnapshotService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ContextSnapshotService.java) 上下文快照持久化
- [ConversationMemoryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationMemoryService.java) Redis 5-key 短期记忆
- [SessionArchivalService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/SessionArchivalService.java) 僵尸会话清理

**缺失项**：
- P0-P4 优先级淘汰策略未显式实现
- 部分降级策略待完善

---

### 方向 8：长期记忆 — 95% 🟢

**已实现**：
- [LongTermMemoryService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/LongTermMemoryService.java)：
  - `loadUserProfile()` 画像装载（Top-K + 相关性排序 + 优先级排序）
  - `extractMemories()` EXPLICIT 通道 LLM 抽取显式偏好
  - `upsertMemory()` UPSERT 累加（confidence + 0.1，封顶 1.0）
  - 周衰减定时任务（BEHAVIOR *0.9 / TASK *0.7 / PREFERENCE_FACT *0.03 / EXPLICIT *0.02）
  - 软删除（confidence ≤ 0.2 或 TASK 4 周未更新）
  - 高频访问增强（近 7 天访问 ≥ 3 次衰减率减半）
  - `getAllMemories()` / `getActiveMemories()` / `searchMemories()` / `deleteMemory()` 用户管理方法
- [MemoryVectorStore.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/store/MemoryVectorStore.java) PostgreSQL 向量检索完整实现（search / upsert / delete / recordAccess）
- [MemoryRetrievalService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/MemoryRetrievalService.java) 双路检索（向量 + 关键词）+ RRF 融合 + 相似度过滤
- [ConflictResolver.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConflictResolver.java) 冲突仲裁：
  - 基于时间戳 + 置信度（支持 KEEP_NEW / KEEP_OLD / KEEP_BOTH 策略）
  - LLM 冲突仲裁（`arbitrateWithLlm()`，支持开关配置 `app.memory-conflict.llm-arbitration-enabled`）
- [InferredBehaviorService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/InferredBehaviorService.java) INFERRED 行为推断通道完整实现：
  - `inferFromBehavior()` 从用户行为推断记忆
  - `recordEvidence()` 写入 `user_memory_evidence` 证据表
  - `recoverMemory()` 记忆恢复（软删除期间再次提及可恢复）
  - `physicalCleanup()` 物理清除（保留指定天数后永久删除）
- [UserMemoryController.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/controller/UserMemoryController.java) 用户记忆管理 REST API：
  - `GET /api/agent/memory/user/{userId}` 获取用户所有记忆
  - `GET /api/agent/memory/user/{userId}/active` 获取活跃记忆
  - `GET /api/agent/memory/user/{userId}/search` 搜索记忆
  - `POST /api/agent/memory/user/{userId}` 创建记忆
  - `PUT /api/agent/memory/user/{userId}/{key}` 更新记忆
  - `DELETE /api/agent/memory/user/{userId}/{key}` 删除记忆
  - `POST /api/agent/memory/user/{userId}/recover/{key}` 恢复记忆
  - `GET /api/agent/memory/user/{userId}/profile` 获取用户画像
- `user_memory_evidence` 证据表已启用（通过 InferredBehaviorService）
- `user_memory_history` 审计表已启用（通过 LongTermMemoryService.logHistory）
- L1 层注入（画像文本作为 L1 层追加到 system prompt）
- `used_memory_ids` 回写已实现（`AgentChatService` 收集 → `ContextAssembler` 组装 → `ContextSnapshotService` 写入）

**缺失项**：
- 记忆导出 / 备份功能

---

## 五、阶段四：行动层（D 层）

### 方向 9：工具调用（Function Calling） — 85% 🟢

**已实现**：
- [ToolRegistry.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolRegistry.java) 工具注册表（注解驱动 + 按意图过滤）
- [ToolExecutor.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/ToolExecutor.java) 工具执行引擎
- [AgentChatService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) `runToolCallLoop()` 工具调用循环（最多 5 轮）
- 3 个工具实现：
  - [NavigateToPageTool.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/impl/NavigateToPageTool.java) 页面导航
  - [SearchKnowledgeTool.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/impl/SearchKnowledgeTool.java) 知识搜索
  - [SearchPostsTool.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/tool/impl/SearchPostsTool.java) 帖子搜索
- DeepSeekClient Function Calling 支持
- 工具 Ref 引用源数据返回（前端渲染可点击引用卡片）

**缺失项**：
- 工具执行超时 / 熔断机制未完整实现（仅有 DeepSeekClient 层的 CircuitBreaker）
- 工具参数脱敏未实现
- 越权防护矩阵未实现（仅有 userId 校验）
- 次数上限未实现

---

### 方向 10：MCP 协议 — 80% 🟢

**已实现**：
- [McpClientManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/McpClientManager.java) MCP Client 管理
- [McpServerController.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/server/McpServerController.java) MCP Server 实现（把现有工具暴露为 MCP Server）
- [McpToolDiscoveryService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/McpToolDiscoveryService.java) 工具发现与动态注册
- [McpToolAdapter.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/McpToolAdapter.java) MCP 工具适配器（适配为统一 Tool 接口）
- [McpProtocol.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/McpProtocol.java) 协议定义
- [McpClientConfig.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/mcp/McpClientConfig.java) 客户端配置

**缺失项**：
- 跨 Agent 工具复用机制未完整验证
- 社区 MCP Server 生态接入未实现

---

### 方向 11：代码执行沙箱 — 0% ⚪

**未实现**：
- 无沙箱隔离
- 无代码执行能力
- 无结果回传

---

## 六、阶段五：推理层（E 层）

### 方向 12：对话编排 — 70% 🟡

**已实现**：
- [AgentChatService.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) `runToolCallLoop()` 实现了类 ReAct 范式：LLM 思考 → 工具调用 → 观察结果 → 继续思考
- 快路径模板回复（OUT_OF_SCOPE / NAVIGATE）
- 慢路径工具调用循环 + 最终流式回答
- CLARIFY 意图识别（但未完整实现追问澄清流程）
- [ConversationSummaryService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/ConversationSummaryService.java) 对话总结服务：
  - `summarizeSession()` 会话级总结（完整对话摘要）
  - `summarizeTurn()` 轮次级总结（滚动摘要）
  - LLM 驱动总结 + 降级策略
- [OrchestrationMode.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/OrchestrationMode.java) 编排模式枚举（REACT/COT/PLAN_AND_EXECUTE/REFLEXION/CLARIFY）
- [DialogueOrchestrator.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/DialogueOrchestrator.java) 对话编排器接口：
  - `orchestrate()` 统一编排入口，自动选择模式
  - `clarify()` 追问澄清（支持最多 3 轮）
  - `summarize()` 会话总结
  - `planAndExecute()` 计划与执行（支持最多 5 步）
  - `reflexion()` 反思优化（基于历史尝试分析）
  - `selectMode()` 模式选择策略（基于意图、槽位、复杂度、失败历史）
- [DialogueOrchestratorImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/orchestration/impl/DialogueOrchestratorImpl.java) 完整实现：
  - ReAct：思考-行动-观察循环
  - CoT：链式思考推理
  - Plan-and-Execute：任务分解 + 逐步执行
  - Reflexion：分析历史尝试 + 优化查询
  - Clarify：缺失槽位追问澄清

**缺失项**：
- 与 AgentChatService 的集成未完成（当前 AgentChatService 使用独立的工具调用循环）
- Reflexion 的失败检测逻辑需完善
- 并行工具调用未实现

---

### 方向 13：多 Agent 协作 — 0% ⚪

**未实现**：
- 无 Agent 角色定义（规划者 / 执行者 / 审核者）
- 无协作模式（流水线 / 辩论 / 层级）
- 无消息传递与状态同步

---

## 七、阶段六：横切关注点（F + G + H 层）

### 方向 14：安全护栏 — 85% 🟢

**已实现**：
- [ConstitutionalAIValidator.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/ConstitutionalAIValidator.java)：
  - 输入层注入检测（关键词 + 模式匹配）
  - 输出层 Constitutional AI 验证
  - 防御"忽略上述所有"/"忽略上述规则"/"jailbreak"攻击变体关键词
- [JailbreakDetector.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/security/JailbreakDetector.java) 三层检测完整实现：
  - Layer1：关键词检测（30+ jailbreak phrases）
  - Layer2：模式匹配（10+ regex patterns，含 sudo/bash/cmd 等）
  - Layer3：上下文分析（长度/行数/异常字符/PII关键词）
  - 威胁等级计算（NONE/LOW/MEDIUM/HIGH/CRITICAL）
  - PII 关键词检测（身份证/手机号/银行卡等 10+ 类）
- [ToolPermissionMatrix.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/security/ToolPermissionMatrix.java) 工具权限矩阵：
  - 按工具名配置角色权限（USER/ADMIN）
  - 每分钟速率限制
  - 每日最大调用次数
  - 参数白名单支持
- [SecurityAuditServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/SecurityAuditServiceImpl.java) 安全审计日志写入（`logInput()` 等方法）
- [SecurityAuditLog.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/entity/SecurityAuditLog.java) 安全审计表实体类（`agent_security_audit_log`）
- [AgentSessionServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentSessionServiceImpl.java) `getSessionAndVerifyOwner()` userId 权限校验（会话归属校验）
- [InternalApiAuthFilter.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/InternalApiAuthFilter.java) 内部 API 鉴权
- 两次注入检测：意图路由之前 + RAG 链路 LLM 调用之前

**缺失项**：
- 参数白名单未实际应用（ToolPermissionMatrix 已定义但未在执行引擎中校验）
- PII 脱敏未实现（仅检测，未脱敏输出）
- 降级与熔断（5 级降级 + 攻击者熔断）未实现

---

### 方向 15：内容审核与 PII 脱敏 — 10% 🟠

**已实现**：
- [JailbreakDetector.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/security/JailbreakDetector.java) 包含 PII 关键词检测（身份证/手机号/银行卡/邮箱/姓名等 10+ 类）

**缺失项**：
- 内容分类审核未实现
- PII 脱敏输出未实现（仅检测，未脱敏）
- 合规策略未实现
- 敏感词过滤未实现

---

### 方向 16：LLM 网关与多模型路由 — 70% 🟢

**已实现**：
- [LlmClient.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmClient.java) 统一接口抽象（chatCompletion / chatCompletionStream / embedding / isHealthy）
- [LlmGateway.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmGateway.java) 多模型路由核心：
  - Provider 注册与管理（CopyOnWriteArrayList + ConcurrentHashMap）
  - 意图驱动动态规则路由（NAVIGATE/OUT_OF_SCOPE → DeepSeek；SEARCH/HOW_TO → DeepSeek/OpenAI）
  - 三级 Fallback 降级链（按优先级尝试所有健康 Provider）
  - 流式 / 非流式 Fallback
  - Provider 健康状态管理（isHealthy / markUnhealthy）
- [DeepSeekAdapter.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/adapter/DeepSeekAdapter.java) Provider Adapter 模式（适配 DeepSeek）
- [LlmGatewayConfig.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmGatewayConfig.java) 配置管理：
  - 多 Provider 配置（apiKey / baseUrl / model / temperature / maxTokens）
  - 优先级配置
  - 健康检查间隔
- [LlmGatewayInitializer.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/gateway/LlmGatewayInitializer.java) 初始化注册
- [DeepSeekClient.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/DeepSeekClient.java)：HTTP 请求超时、CircuitBreaker + Retry、流式 / 非流式接口
- [EmbeddingClient.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/llm/EmbeddingClient.java) BGE-M3 Embedding 调用

**缺失项**：
- 未适配 GPT / Claude / 通义千问等其他 Provider（仅 DeepSeekAdapter）
- API Key 池轮询未实现
- 自动摘除（429 冷却 / 401 永久摘除）未实现（仅 markUnhealthy）
- 5 维度成本追踪未实现
- per-model CircuitBreaker 未实现（仅有全局 CircuitBreaker）
- 主动健康探测未实现（仅被动标记 unhealthy）

---

### 方向 17：缓存层 — 85% 🟢

**已实现**：
- [IntentCacheService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentCacheService.java) 意图识别结果缓存（Redis，MD5 key 精确匹配）
- [RetrievalService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/RetrievalService.java) RAG 检索结果缓存
- [PromptVersionManager.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java) 当前版本号 Redis 缓存
- [CacheConfig.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/CacheConfig.java) Caffeine 本地缓存配置：
  - `intentCache`（意图缓存）
  - `retrievalCache`（检索缓存）
  - `semanticCache`（语义缓存）
  - `embeddingCache`（Embedding 缓存）
- [SemanticCacheService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/cache/SemanticCacheService.java) 多层缓存策略（Caffeine L1 + Redis L2）：
  - 语义相似度缓存（基于 Embedding 向量相似度匹配）
  - Embedding 向量缓存（StringRedisTemplate + JSON 序列化）
  - 缓存命中 / 写入逻辑
  - TTL 过期策略
- [CacheInvalidationService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/CacheInvalidationService.java) 事件驱动缓存失效：
  - `onKnowledgeUpdated/deleted/batchUpdated()` 知识库变更触发失效
  - `onPostUpdated/deleted()` 帖子变更触发失效
  - `onMemoryUpdated()` 记忆变更触发失效
  - `invalidateSemanticCache()` / `invalidateIntentCache()` / `invalidateAllCaches()` 分级失效
  - 本地缓存 + Redis 缓存同步失效

**缺失项**：
- 细粒度缓存失效（按 key 精确失效，而非全量失效）

---

### 方向 18：限流配额与成本控制 — 70% 🟡

**已实现**：
- [AgentRateLimiter.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentRateLimiter.java) 基于 Redis 的简单限流（滑动窗口 / 令牌桶）
- [AgentController.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/controller/AgentController.java) 接口层限流
- [QuotaService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/QuotaService.java) 配额管理：
  - 用户层级管理（FREE/PRO/ENTERPRISE）
  - Token 配额管理（每日/每月限额）
  - 每日配额检查 `checkQuota()`
  - 成本归因 5 维度（用户/会话/模型/意图/天），通过 `consumeQuota()` 记录到 Redis
  - `checkCostAlerts()` WARNING/CRITICAL 两级成本告警（日/月维度）
  - `getSystemCostOverview()` 系统级成本概览（总日/月成本、活跃用户数）
- [ToolPermissionMatrix.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/security/ToolPermissionMatrix.java) 工具级速率限制和每日调用次数限制

**缺失项**：
- 多租户隔离未实现
- 成本告警通知机制（邮件/短信/Webhook）未实现

---

### 方向 19：可观测性 — 85% 🟢

**已实现**：
- [AgentChatService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) TraceService 集成到请求链路：
  - `prepareContext()` 生成 traceId 并启动根 span
  - 意图识别、RAG 检索创建子 span
  - `completeTurn()` / `completeShortCircuitTurn()` 结束根 span 并记录 LLM 使用情况
  - MDC traceId 注入（boundedElastic 线程切换后仍保留）
- [TraceServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/TraceServiceImpl.java) 完整实现：
  - `generateTraceId()` / `startSpan()` / `endSpan()` / `endSpanWithError()`
  - `recordLlmUsage()` / `recordIntent()` / `recordToolCall()`
  - `getTrace()` / `getSessionTraces()` / `getTraceSummary()`
- [TraceSpan.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/entity/TraceSpan.java) Trace 链路表实体类（`agent_trace_spans`）：
  - traceId / spanId / parentSpanId
  - 7 阶段耗时记录（意图识别/RAG/Prompt 装配/LLM 流式/工具调用/记忆写入/完成）
  - 模型名称、Token 计数、意图、工具名称等字段
- [TraceIdFilter.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/TraceIdFilter.java) X-Trace-Id Header 传递过滤器：
  - 接收/生成 traceId，写入响应头
  - Reactor Context + MDC 双重传递，解决 WebFlux 线程切换问题
- [MetricsService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/MetricsService.java) 统一 Metrics 服务接口：
  - 6 大维度 14 项核心指标：延迟 5（chat/intent/rag/llm/tool）+ Token 2 + 成本 2 + 错误 2 + 工具 2 + 缓存 3
- [MetricsServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/MetricsServiceImpl.java) 完整实现：
  - @PostConstruct 预创建所有 Timer/Counter/DistributionSummary
  - 支持百分位数（P50/P95/P99）和直方图
  - 动态工具/缓存指标（ConcurrentHashMap 按需创建）
- [IntentMetricsConfig.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/IntentMetricsConfig.java) 意图识别指标
- [KnowledgeMetricsConfig.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/KnowledgeMetricsConfig.java) 知识库指标
- [logback-spring.xml](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/resources/logback-spring.xml) 结构化 JSON 日志配置（logstash-logback-encoder）
- `agent_turns` 表记录对话 turn

**缺失项**：
- 告警规则体系未实现
- Grafana 仪表盘未配置

---

### 方向 20：评估体系 — 80% 🟢

**已实现**：
- [EvalTestCase.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/entity/EvalTestCase.java) 评估测试用例实体类：
  - 用户查询、预期意图、预期子意图、预期答案、预期引用、预期导航路径
  - 分类、是否黄金用例、优先级
- [EvalResult.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/entity/EvalResult.java) 评估结果实体类：
  - 实际意图、实际答案、匹配度、LLM 评分、通过率
- [EvalService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/EvalService.java) 评估服务接口：
  - 测试用例 CRUD、黄金用例查询
  - `runEval()` / `runGoldenEval()` 执行评估
  - `getRunResults()` / `getRunSummary()` 获取结果
  - `evaluateSingle()` 单条评估
  - `llmJudge()` LLM 评分
- [EvalServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/EvalServiceImpl.java) 完整实现：
  - `llmJudge()` 通过 DeepSeekClient 调用 LLM 进行多维评分（相关性/准确性/完整性/表达质量）
  - `evaluateSingle()` 正确获取 AgentChatService 返回的流式事件结果
  - 意图匹配、答案匹配（余弦相似度）、引用匹配、导航匹配
  - 综合评分计算（80 分以上为通过）
- 评估指标体系：意图匹配 + 答案匹配 + 引用匹配 + 导航匹配 + LLM 评分
- [BadCaseService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/BadCaseService.java) + [BadCaseServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/BadCaseServiceImpl.java) BadCase 自动采集服务：
  - 从错误 Turn 和用户负反馈自动采集 BadCase
  - 相似度去重（余弦相似度阈值 0.8）
  - BadCase 转测试用例（convertToTestCase）
  - 状态管理（NEW/IN_PROGRESS/RESOLVED）
- [BadCaseScheduler.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/BadCaseScheduler.java) 定时任务：
  - 每日凌晨 2 点执行全量采集
  - 每小时执行增量采集

**缺失项**：
- CI/CD 集成（PR 触发评估）
- 影子评估（线上流量采样对比）
- 回归检测（与上次版本对比）

---

### 方向 21：A/B 测试与灰度发布 — 30% 🟡

**已实现**：
- [PromptVersionManager.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java) Prompt 版本灰度发布（按 userId 百分比）
- [PromptVersionController.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/controller/PromptVersionController.java) 版本管理 API

**缺失项**：
- 无 A/B 实验平台
- 无流量分配
- 无统计显著性计算
- 无实验结果对比

---

### 方向 22：分层部署与在离线协同 — 10% 🟠

**已实现**：
- [PostVectorScheduler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/config/PostVectorScheduler.java) 异步帖子向量同步
- [KnowledgeScheduler.java](file:///d:/WorkSpace-java/CS/backend/campushare-agent/src/main/java/com/campushare/agent/config/KnowledgeScheduler.java) 知识库定时任务
- 长期记忆衰减定时任务
- 会话归档定时任务

**缺失项**：
- 无组件分层部署决策（在线 / 异步 / 离线未显式分层）
- 无事件总线 / 消息队列
- 无 CDC 数据同步
- 无定时任务编排
- 无数据一致性保证机制
- 无资源隔离与弹性伸缩

---

### 方向 23：性能 SLO 工程 — 75% 🟡

**已实现**：
- [SloConfig.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/SloConfig.java) SLO 配置类：
  - 可用性目标（默认 99.9%）
  - 延迟目标（P50/P95/P99）
  - 错误率阈值（默认 1%）
  - 燃烧率阈值、时间窗口配置
- [SloService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/SloService.java) SLO 服务接口：
  - `recordLatency()` / `recordError()` 记录延迟和错误
  - `getSloStatus()` / `getAllSloStatus()` 获取状态
  - `isBreaching()` 判断是否违反 SLO
  - `calculateBurnRate()` 计算燃烧率
  - `getLatencyPercentiles()` / `getErrorRate()` 获取指标
  - `checkBurnRateAlerts()` / `getRecentAlerts()` 燃烧率告警
- [SloServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/SloServiceImpl.java) 完整实现：
  - 内存 + Redis 双层存储（保留 7 天数据）
  - 分钟级延迟统计（Redis List）
  - 日级成功/错误计数（Redis Hash）
  - P50/P95/P99 百分位数计算
  - 燃烧率计算（实际错误率 / 允许错误率）
  - SLO 违规判断
  - 多窗口燃烧率告警（1/5/15 分钟窗口，不同燃烧率阈值）
  - 告警冷却机制（5 分钟冷却期避免告警风暴）
  - 告警记录到 Redis（保留 24 小时）
- [AgentChatService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java) 集成：
  - `completeTurn()` 中调用 `sloService.recordLatency()` 和 `sloService.recordError()`
  - 记录 chat 和 intent-recognition 的延迟和错误
- [SloController.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/controller/SloController.java) REST API：
  - `/api/agent/slo/status` 获取所有 SLO 状态
  - `/api/agent/slo/status/{objective}` 获取单个目标状态
  - `/api/agent/slo/burn-rate/{objective}` 检查燃烧率告警
  - `/api/agent/slo/alerts/{objective}` 获取最近告警记录

**缺失项**：
- 延迟预算分配（总预算拆分到各环节）
- SLO 驱动架构决策（超预算时的降级策略）
- SLO 仪表盘（Grafana）

---

## 八、按完成度分组汇总

### 🟢 已实现（13 个，完成度 ≥ 80%）

| # | 方向 | 完成度 |
|---|------|--------|
| 2 | 意图识别与路由 | 95% |
| 8 | 长期记忆 | 95% |
| 4 | 知识库管理 | 90% |
| 7 | 上下文工程 | 90% |
| 1 | System Prompt 工程 | 85% |
| 5 | RAG 检索增强 | 85% |
| 9 | 工具调用 | 85% |
| 14 | 安全护栏 | 85% |
| 17 | 缓存层 | 85% |
| 19 | 可观测性 | 85% |
| 10 | MCP 协议 | 80% |
| 20 | 评估体系 | 80% |

**结论**：A-E 层核心能力（基础能力 + 知识 + 记忆 + 行动）已基本完整，安全护栏、可观测性、评估体系和缓存层也达到生产可用水平。Agent 能完成"听懂 → 检索 → 记住 → 行动 → 安全防护 → 可观测 → 评估 → 缓存加速"的完整闭环。

---

### 🟡 部分实现（5 个，完成度 30%-79%）

| # | 方向 | 完成度 | 主要缺失 |
|---|------|--------|---------|
| 16 | LLM 网关与多模型路由 | 70% | 多 Provider 适配、API Key 池、成本追踪 |
| 23 | 性能 SLO 工程 | 75% | 延迟预算分配、SLO 驱动降级、仪表盘 |
| 18 | 限流配额与成本控制 | 70% | 多租户隔离、成本告警通知 |
| 12 | 对话编排 | 40% | 范式选型、追问澄清、Plan-and-Execute、Reflexion |
| 21 | A/B 测试与灰度发布 | 30% | A/B 实验平台、统计显著性 |

---

### 🟠 仅基础（2 个，完成度 10%-29%）

| # | 方向 | 完成度 | 现状 |
|---|------|--------|------|
| 15 | 内容审核与 PII 脱敏 | 10% | 仅 PII 关键词检测，无脱敏输出 |
| 22 | 分层部署与在离线协同 | 15% | 仅定时任务，无事件总线、无 CDC |

---

### ⚪ 未实现（4 个，完成度 < 10%）

| # | 方向 | 类型 |
|---|------|------|
| 3 | 多模态理解 | 扩展 |
| 6 | 知识图谱 | 扩展 |
| 11 | 代码执行沙箱 | 扩展 |
| 13 | 多 Agent 协作 | 扩展 |

---

## 九、核心方向 vs 扩展方向完成情况

### 核心方向（15 个）

| 状态 | 数量 | 占比 |
|------|------|------|
| 🟢 已实现 | 12 | 80% |
| 🟡 部分实现 | 2 | 13% |
| 🟠 仅基础 | 1 | 7% |
| ⚪ 未实现 | 0 | 0% |

**核心方向整体完成度**：约 88%

### 扩展方向（8 个）

| 状态 | 数量 | 占比 |
|------|------|------|
| 🟢 已实现 | 1 | 12% |
| 🟡 部分实现 | 2 | 25% |
| 🟠 仅基础 | 1 | 12% |
| ⚪ 未实现 | 4 | 50% |

**扩展方向整体完成度**：约 25%

---

## 十、按层完成情况

| 层 | 名称 | 方向数 | 已实现 | 部分实现 | 仅基础 | 未实现 | 层完成度 |
|----|------|--------|--------|---------|--------|--------|---------|
| A | 基础能力层 | 3 | 2 | 0 | 0 | 1 | 60% |
| B | 知识层 | 3 | 2 | 0 | 0 | 1 | 58% |
| C | 记忆层 | 2 | 2 | 0 | 0 | 0 | 95% |
| D | 行动层 | 3 | 2 | 0 | 0 | 1 | 55% |
| E | 推理层 | 2 | 0 | 1 | 0 | 1 | 20% |
| F | 安全层 | 2 | 1 | 0 | 1 | 0 | 48% |
| G | 工程基础设施层 | 4 | 1 | 2 | 1 | 0 | 55% |
| H | 观测与评估层 | 4 | 3 | 1 | 0 | 0 | 85% |

**整体完成度**：约 70%（按 23 个方向平均）

---

## 十一、关键差距与优先级建议

### 高优先级（影响生产可用性）

1. **评估体系（20，70%）**：核心框架已实现，需补全 CI/CD 集成、BadCase 数据飞轮。建议优先建立黄金测试集并接入 CI。
2. **性能 SLO 工程（23，60%）**：核心服务已实现，需补全延迟预算分配、SLO 仪表盘、多窗口燃烧率告警。建议优先实现告警体系。
3. **可观测性（19，80%）**：TraceService 已集成，需补全 X-Trace-Id Header 传递、统一 Metrics 体系、BadCase 自动采集。

### 中优先级（影响用户体验）

4. **长期记忆（8，90%）**：补全用户记忆管理 API（UserMemoryController），允许用户查看/删除记忆。
5. **缓存层（17，70%）**：多层缓存策略已实现，需完善事件驱动失效。
6. **限流配额（18，55%）**：成本归因已实现，需补全多租户隔离、成本告警。
7. **LLM 网关（16，70%）**：核心框架已完成，需适配更多 Provider（GPT/通义千问）、API Key 池轮询。

### 低优先级（影响系统成熟度）

8. **对话编排（12，40%）**：当前 ReAct 已满足基本需求，Plan-and-Execute / Reflexion 待业务驱动。
9. **分层部署与在离线协同（22，15%）**：当前定时任务已满足需求，事件总线 / CDC 待业务规模增长后实现。

### 按需实现（扩展方向）

10. **多模态（3）/ 知识图谱（6）/ 代码沙箱（11）/ 多 Agent（13）/ 内容审核（15）**：根据业务需求决定。

---

## 附录：本次更新的主要变化

### 完成度提升的方向

| 方向 | 之前完成度 | 当前完成度 | 主要新增实现 |
|------|-----------|-----------|-------------|
| 缓存层（17） | 70% | 85% | CacheInvalidationService 事件驱动缓存失效（知识库/帖子/记忆变更触发）、本地+Redis同步失效 |
| 可观测性（19） | 80% | 85% | TraceIdFilter X-Trace-Id Header 传递过滤器、统一 MetricsService（6 大维度 14 项指标）、MetricsServiceImpl 预创建实现 |
| 限流配额（18） | 55% | 70% | QuotaService.checkCostAlerts() 两级成本告警、getSystemCostOverview() 系统成本概览、用户层级（FREE/PRO/ENTERPRISE） |
| 评估体系（20） | 80% | 85% | BadCaseScheduler 定时任务（每日全量 + 每小时增量）、BadCaseController REST API |

### 核心方向整体完成度提升

- **之前**：约 85%（11 个已实现）
- **当前**：约 88%（12 个已实现）

### 整体完成度提升

- **之前**：约 65%
- **当前**：约 70%

### 已实现的关键新增组件

| 组件 | 文件路径 | 功能描述 |
|------|---------|---------|
| TraceIdFilter | [TraceIdFilter.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/TraceIdFilter.java) | X-Trace-Id Header 传递，解决 WebFlux 线程切换 MDC 丢失 |
| MetricsService | [MetricsService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/MetricsService.java) | 统一 Metrics 接口，6 大维度 14 项核心指标 |
| MetricsServiceImpl | [MetricsServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/impl/MetricsServiceImpl.java) | 预创建所有 Timer/Counter，支持百分位数和直方图 |
| CacheInvalidationService | [CacheInvalidationService.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/service/CacheInvalidationService.java) | 事件驱动缓存失效，知识库/帖子/记忆变更触发 |
| BadCaseScheduler | [BadCaseScheduler.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/config/BadCaseScheduler.java) | BadCase 自动采集定时任务 |
| BadCaseController | [BadCaseController.java](file:///E:/workspace_work/CampusShare/backend/campushare-agent/src/main/java/com/campushare/agent/controller/BadCaseController.java) | BadCase 管理 REST API |

### 仍待重点关注的问题

1. **评估体系（20，85%）**：核心框架已实现，需补全 CI/CD 集成、影子评估、回归检测
2. **性能 SLO 工程（23，75%）**：核心服务已实现，需补全延迟预算分配、SLO 驱动降级、Grafana 仪表盘
3. **LLM 网关（16，70%）**：核心框架已完成，需适配更多 Provider（GPT/通义千问）、API Key 池轮询
4. **对话编排（12，40%）**：当前 ReAct 已满足基本需求，Plan-and-Execute / Reflexion 待业务驱动

**code-review-issues.md 中已修复的长期记忆问题**：
- 问题 9：INFERRED 行为推断通道 ✅ 已实现（InferredBehaviorService）
- 问题 10：MemoryVectorStore ✅ 已实现（PostgreSQL 向量检索）
- 问题 11：ConflictResolver ✅ 已实现（基于时间戳 + 置信度）
- 问题 12：`used_memory_ids` 回写 ✅ 已实现
- 问题 13：用户记忆管理 API ✅ 已实现（UserMemoryController）
- 问题 14：证据表和审计表 ✅ 已启用（通过 InferredBehaviorService 和 LongTermMemoryService）
- 问题 15：物理清除和记忆恢复 ✅ 已实现（InferredBehaviorService.physicalCleanup 和 recoverMemory）

建议后续更新 [code-review-issues.md](file:///E:/workspace_work/CampusShare/docs/agent-design/code-review-issues.md) 中问题 9、10、11、12、13、14、15 的状态为"已实现"。
