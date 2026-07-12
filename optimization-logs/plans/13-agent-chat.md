# 优化计划 13：AI 对话（SSE 流式）

> **优先级：P2** | **服务：agent-service(8083)** | **接口数：1**

---

## 一、业务背景

### 1.1 业务背景类型：主动需求型（用户/业务方驱动）

**为什么现在做**：AI 对话是平台差异化功能，首 token 延迟（TTFT）和总响应时间直接影响用户体验。当前依赖外部 DeepSeek API + RAG 向量检索，链路长、外部依赖多，延迟劣化因素复杂。

**如果不做会怎样**：首 token 延迟 > 3s，用户感知卡顿；RAG 检索慢拖累整体响应；外部 API 限流时无降级。

**做成之后意味着什么**：首 token < 2s，RAG 检索 < 200ms，外部 API 异常时有降级。

### 1.2 涉及接口

| # | 接口 | 方法 | 路径 | 当前实现要点 |
|---|------|------|------|-------------|
| 1 | AI对话 | POST | `/agent/chat` | SSE流式 → RAG检索(pgvector) → DeepSeek API流式生成 |

---

## 二、当前实现分析

> ⚠️ 优化前需 `Read` agent-service Controller + Service 确认

### 2.1 疑似瓶颈

| 编号 | 疑点 | 风险等级 | 说明 |
|------|------|----------|------|
| AC1 | **RAG 向量检索慢** | 中 | pgvector 检索 + embedding 生成 |
| AC2 | **外部 API 延迟** | 高 | DeepSeek API 首 token 延迟不可控 |
| AC3 | **无 API 熔断** | 中 | DeepSeek 限流/宕机时无降级 |
| AC4 | **会话历史加载** | 低 | 长上下文加载 |
| AC5 | **WebFlux 阻塞调用** | 高 | 历史踩坑：reactor 线程上 .block()（已修复，需复检） |
| AC6 | **embedding 生成延迟** | 中 | 向量化用户 query 需调外部 API |

### 2.3 已知约束（来自 AGENT-WORKFLOW.md）

- agent-service 基于 WebFlux，阻塞调用需 boundedElastic
- start-period=90s（双数据源初始化慢）
- DeepSeek API key/URL 通过 .env 配置
- pgvector PostgreSQL 向量库

### 2.4 已知历史踩坑

- WebFlux reactor 线程 .block() 导致 RAG reindex 全失败（见 insights/reliability）
- Controller 返回同步类型（已修复）

---

## 三、优化方案

### 3.1 方案选型对比

| 方案 | 核心思路 | 优点 | 缺点 | 选用？ |
|------|----------|------|------|--------|
| **A: RAG 缓存 + API 熔断 + 复检阻塞** | query embedding 缓存、DeepSeek 熔断降级、复检 boundedElastic | 改动中等、提升稳定性 | 不解决 API 本身延迟 | ✅ |
| B: 本地模型 | 部署本地 LLM | 无外部延迟 | GPU 成本高 | ❌ 当前不需要 |
| C: RAG 预热 | 热门问题预检索 | 加速常见问题 | 有限的场景覆盖 | ⚠️ 第二阶段 |

### 3.2 第一阶段优化（方案A）

**优化1：RAG 检索缓存**
- key: `cache:rag:query:{hash(query)}`，value: 检索结果，TTL 1小时
- 相同/相似 query 命中缓存，跳过向量检索

**优化2：query embedding 缓存**
- 相同 query 的 embedding 缓存，避免重复调外部 API 生成向量

**优化3：DeepSeek API 熔断降级**
- 引入 Resilience4j（已在 pom.xml）
- API 限流/超时时降级：返回预设回复 + 提示用户重试
- 熔断器：失败率 > 50% 时熔断 30s

**优化4：复检 WebFlux 阻塞调用**
- 确认所有阻塞调用（JDBC、Feign、外部API）都包在 `Mono.fromCallable + subscribeOn(boundedElastic)`
- Controller 返回 `Mono<T>` 而非同步 `T`

**优化5：会话历史优化**
- 上下文窗口管理（保留最近N轮 + System prompt）
- 避免加载全部历史

### 3.3 Agent 质量优化（后续专项）

> 此处只列接口延迟相关，召回质量/幻觉降低是后续 agent 专项

- RAG 召回质量：top-k 调优、相似度阈值
- Prompt 优化：减少 token 消耗
- 幻觉降低：引用溯源、置信度过滤

---

## 四、成功指标

| 指标 | 优化前（待测） | 目标 | 测量方式 |
|------|---------------|------|----------|
| 首 token 延迟(TTFT) | 待测 | < 2s | 前端计时 |
| RAG 检索 P95 | 待测 | < 200ms | agent 日志 |
| 总响应时间(短回答) | 待测 | < 5s | 前端计时 |
| API 熔断后降级响应 | - | < 1s | 模拟API超时 |

---

## 五、前置条件与风险

- **前置**：基线测试；确认 Resilience4j 已引入；复检 WebFlux 阻塞调用
- **风险1**：RAG 缓存命中率取决于 query 多样性
- **风险2**：熔断降级回复质量需评估
- **风险3**：外部 API 延迟不可控，优化有上限

---

## 六、关联记录

- 优化完成后记录：`records/13-agent-chat.md`（待创建）
- 关联踩坑：AGENT-WORKFLOW.md §8.5（WebFlux 阻塞调用）
- 关联历史：[insights/reliability WebFlux阻塞调用导致RAG索引全失败](../../insights/reliability/2026-07-02_postmortem_WebFlux阻塞调用导致RAG索引全失败.md)
- 关联文档：docs/agent-assistant/（93份Agent设计文档）
