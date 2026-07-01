# MVP 阶段详细规划

> 状态: 草稿
> 最后更新: 2026-06-30
> 阶段: Phase 1 MVP
> 周期: 4-6 周

## 一、MVP 目标

**一句话**:用户能在 CampusShare 助手页面提问,agent 流式返回带引用的回答,覆盖 HOW_TO / SEARCH / NAVIGATE 三大意图。

**验收标准**:
1. 前端 NavBar 新增「助手」入口,点击进入聊天页面。
2. 用户提问后 SSE 流式输出回答,支持中断。
3. 回答含 `[n]` 引用,点击可查看引用卡。
4. 检索覆盖知识库(教程) + 帖子(资源/讨论)。
5. 单次对话延迟 P95 ≤ 10s(无 reranker/反思)。
6. 基础安全:输入护栏(prompt 注入规则 + PII 脱敏) + 敏感词过滤。
7. 基础监控:QPS / 延迟 / 错误率(Grafana)。

## 二、周计划

### Week 1: 后端骨架 + 数据库

**任务**:
- [ ] 创建 `campushare-agent` Maven 模块(见 12-service-structure)
- [ ] 配置 pom.xml(WebFlux / Feign / MyBatis Plus / pgvector / jtokkit)
- [ ] 配置 application.yml(双数据源 MySQL+PG / Redis / Feign / 8083 端口)
- [ ] 创建数据库表(见 12-database-schema):
  - MySQL: agent_sessions / agent_turns / agent_tool_registry / knowledge_articles
  - PostgreSQL: post_vectors / knowledge_vectors(HNSW + pg_trgm 索引)
- [ ] 编写 agent-init.sql
- [ ] Dockerfile agent-service target

**交付物**:可启动的 agent-service 骨架(空接口)。

**重启命令**(记录到 AGENT-WORKFLOW.md):
```bash
# 单独构建 agent-service
docker-compose build agent-service
# 启动
docker-compose up -d agent-service
# 查看日志
docker-compose logs -f agent-service
```

### Week 2: 知识库 + 向量索引

**任务**:
- [ ] 编写初始知识库文档(见 05-knowledge-base):
  - 平台使用教程(注册/登录/发帖/搜索/收藏/关注/私信/通知设置/创作者认证)
  - 帮助中心 FAQ(约 20 篇)
- [ ] 知识文档分块(Recursive Character Splitter, 512 token)
- [ ] 部署 BGE-M3 embedding 服务(TEI 镜像,端口 8084)
- [ ] 编写 EmbeddingService:调用 BGE 生成向量
- [ ] 编写 KnowledgeReindexScheduler:知识文档 → embedding → PG 入库
- [ ] 帖子向量索引:post-service 发帖时通过 Feign 通知 agent-service 生成索引

**交付物**:知识库和帖子可通过向量检索。

**重启命令**:
```bash
# BGE embedding 服务
docker-compose up -d bge-service
# 重建知识索引(管理接口)
curl -X POST http://localhost:8083/api/admin/agent/knowledge/reindex
```

### Week 3: 检索 + 意图 + Agent 核心

**任务**:
- [ ] 编写 HybridRetrievalService:
  - 向量检索(PG pgvector, HNSW, top-20)
  - 关键词检索(PG pg_trgm, top-20)
  - RRF 融合(top-10)
- [ ] 编写 IntentClassifier:
  - MVP 用 DeepSeek-V3 分类(5 意图)
  - OUT_OF_SCOPE 直接拒答
- [ ] 编写 QueryRewriter:
  - 同义词扩展(基础)
  - 指代消解(多轮,基础)
- [ ] 编写 AgentOrchestrator(ReAct 循环):
  - 意图 → 查询改写 → 检索 → 工具调用(可选)→ LLM 生成
  - 工具调用上限 3 次
- [ ] 实现 3 个核心工具:
  - search_posts(Feign 调 post-service)
  - search_knowledge(PG 检索)
  - list_categories(Feign 调 post-service)

**交付物**:输入 query → 返回带引用的回答(非流式,先验证逻辑)。

### Week 4: SSE 流式 + 前端

**任务**:
- [ ] 后端 SSE 实现(Spring WebFlux):
  - POST /api/agent/chat(流式)
  - 7 种事件:status / tool / token / refs / clarify / error / done
- [ ] 前端 AssistantPage(见 11-frontend-integration):
  - 聊天布局(Header + 消息流 + 输入区)
  - streamMessage() fetch + ReadableStream
  - 消息气泡(用户/助手/工具状态/错误)
  - 引用角标 `[n]` + 引用卡
  - 中断按钮(AbortController)
- [ ] 前端 NavBar 集成:
  - 4 tab → 5 tab,插入 Bot 图标到 /assistant
  - PrivateRoute 守卫
- [ ] 前端 Zustand assistantStore:
  - sessionId / messages / isStreaming
  - createSession / sendMessage / stopGeneration

**交付物**:完整的前后端联调,用户可流式对话。

### Week 5: 安全护栏 + 基础监控

**任务**:
- [ ] 输入护栏(见 14-input-guardrails):
  - Prompt 注入规则匹配(正则库)
  - PII 脱敏(手机/身份证/邮箱)
  - 输入长度限制(2000 字符)
- [ ] 输出护栏(基础):
  - 敏感词过滤(AC 自动机)
  - 引用编号校验(越界检查)
  - 格式校验(markdown 合法性)
- [ ] 基础限流(见 14-abuse-prevention):
  - 单用户 10 条/分钟,500 条/日(Redis 滑动窗口)
- [ ] 基础监控(见 15-observability):
  - Micrometer 指标:QPS / TTFB / E2E 延迟 / 错误率
  - Prometheus 抓取配置
  - Grafana Agent 总览看板(基础版)
- [ ] 结构化日志(JSON,含 traceId/sessionId)

**交付物**:安全护栏 + 监控就位,MVP 可上线。

### Week 6: 测试 + 联调 + 上线

**任务**:
- [ ] 端到端测试:
  - 手动测试 20+ 场景(覆盖 5 意图)
  - 多轮对话测试(指代消解)
  - 中断/重试测试
  - 限流测试
  - 安全测试(注入尝试)
- [ ] Docker Compose 集成:
  - agent-postgres / bge-service / agent-service
  - 网关路由配置(/api/agent/**)
  - 资源预算检查(见 12-docker-integration)
- [ ] AGENT-WORKFLOW.md 更新:
  - 微服务划分(agent-service 8083)
  - 启动/重启命令
  - 开发注意事项
- [ ] changelog 记录
- [ ] 上线 master 分支

**交付物**:MVP 上线,用户可使用。

## 三、MVP 技术债务

| 债务 | 简化方式 | 风险 | 偿还 |
|------|----------|------|------|
| 无 reranker | RRF 融合直接输出 | 排序质量一般 | 进阶 Week 1 |
| 无反思 | 单次生成无校验 | 复杂查询可能错 | 进阶 Week 2 |
| 无上下文压缩 | 截断旧消息 | 长对话丢失上下文 | 进阶 Week 3 |
| 无长期记忆 | 每次会话独立 | 用户偏好不记忆 | 进阶 Week 4 |
| 意图用 LLM | 每次调 DeepSeek | 延迟 +500ms | 进阶(小模型) |
| 关键词安全 | AC 自动机 | 漏检语义级有害 | 进阶(LLM 分类) |
| 无 A/B | 直接全量上线 | 无法验证优化 | 进阶 Week 5 |
| 单实例 | 1 个 agent-service | 无高可用 | 远期 |

## 四、MVP 验收清单

### 功能验收
- [ ] NavBar 5 tab,助手在首页和仓库之间
- [ ] 助手页面可发送消息,流式显示回答
- [ ] 回答含引用 `[n]`,点击展开引用卡
- [ ] 可中断生成(停止按钮)
- [ ] HOW_TO 意图:能回答「怎么发帖」「怎么成为创作者」等
- [ ] SEARCH 意图:能搜索帖子并返回结果
- [ ] NAVIGATE 意图:能定位到对应板块
- [ ] OUT_OF_SCOPE:礼貌拒答
- [ ] 多轮对话:第 2 轮能理解「第一个」「它」等指代
- [ ] 错误处理:网络断开/LLM 超时有友好提示

### 安全验收
- [ ] Prompt 注入被拦截("忽略以上指令")
- [ ] PII 被脱敏(输入手机号不回显)
- [ ] 敏感词被过滤(回答中不含违禁词)
- [ ] 超长输入被截断(>2000 字符提示)
- [ ] 限流生效(>10条/分钟被限流)

### 性能验收
- [ ] TTFB P95 ≤ 3s(无 reranker)
- [ ] E2E P95 ≤ 10s
- [ ] 错误率 ≤ 5%
- [ ] 单次对话成本 ≤ ¥0.01

### 监控验收
- [ ] Grafana 可看到 Agent 总览看板
- [ ] 日志含 traceId,可通过 traceId 查日志
- [ ] Prometheus 指标可查询

## 五、决策记录

### ADR-194: MVP 范围 - 基础可用,6 周交付
- **背景**:Agent 工程庞大,需定义最小可用范围。
- **决策**:MVP 包含单 Agent ReAct + 混合检索 + SSE 流式 + 基础安全 + 基础监控,6 周交付。
- **不包含**:reranker、反思、上下文压缩、长期记忆、A/B、完整安全、高级监控。
- **理由**:先让用户用起来,再逐步优化;6 周是平衡速度与质量的周期。
- **状态**:采纳
