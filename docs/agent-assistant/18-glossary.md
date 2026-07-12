# 18 - 术语表

> 状态: 草稿
> 最后更新: 2026-06-30
> 适用范围: CampusShare Agent 助手全部文档

本术语表收录 Agent 文档中出现的所有关键技术术语,按字母排序,附中文解释和文档引用。

---

## A

**AC 自动机 (Aho-Corasick Automaton)**
高效多模式字符串匹配算法,用于敏感词库检测。一次扫描文本即可匹配所有敏感词。见 [14-content-safety.md](./14-safety-and-guardrails/content-safety.md)。

**ADR (Architecture Decision Record)**
架构决策记录。记录每个重要技术决策的背景、方案、理由。本系列文档共 ADR-001 ~ ADR-196。

**Agent**
智能助手。能理解用户意图、调用工具、检索知识、生成回答的 AI 系统。见 [07-agent-design/](./07-agent-design/)。

**Adoption Rate (采纳率)**
用户采纳 agent 回答的比例。核心在线指标。见 [13-user-satisfaction-metrics.md](./13-evaluation/user-satisfaction-metrics.md)。

**Artifacts**
Claude 的侧边面板功能,渲染代码/文档/图表。CampusShare 不采用(移动端无侧边栏)。见 [16-claude-analysis.md](./16-technical-references/claude-analysis.md)。

---

## B

**BGE-M3**
北京智源研究院开源的 embedding 模型,支持多语言/多粒度,输出 1024 维稠密向量。CampusShare 用于知识/帖子向量化。见 [05-embedding.md](./05-knowledge-base/embedding-strategy.md)。

**bge-reranker-v2-m3**
BGE 系列的 cross-encoder 重排模型,对 query-doc pair 精排。见 [06-reranking.md](./06-retrieval/reranking-strategy.md)。

**BM25**
经典关键词检索算法,基于词频和逆文档频率。CampusShare 用 pg_trgm 替代(简化部署)。见 [06-hybrid-retrieval.md](./06-retrieval/hybrid-retrieval.md)。

---

## C

**Chain of Thought (CoT)**
思维链。LLM 显式输出推理过程,提升复杂问题准确率。见 [08-cot-prompting.md](./08-prompt-engineering/cot-prompting.md)。

**Citation (引用溯源)**
回答中标注信息来源 `[n]`,用户可点击查看原文。见 [10-sse-streaming-api.md](./10-tools-and-apis/sse-streaming-api.md)。

**Cohen's Kappa**
衡量两个标注者一致性的统计量。≥0.6 为可接受。见 [13-golden-test-set.md](./13-evaluation/golden-test-set.md)。

**Constitutional AI**
Anthropic 提出的安全方法,LLM 基于「宪法」规则自我约束。见 [16-claude-analysis.md](./16-technical-references/claude-analysis.md)。

**Context Window (上下文窗口)**
LLM 单次可处理的最大 token 数。DeepSeek-V3 理论 128K,CampusShare 实际预算 8K。见 [09-context-window-management.md](./09-context-engineering/context-window-management.md)。

**Cross-Encoder**
重排模型架构,将 query 和 doc 拼接后输入模型,输出相关度分数。比 bi-encoder 准但慢。见 [06-reranking.md](./06-retrieval/reranking-strategy.md)。

**CSAT (Customer Satisfaction Score)**
用户满意度评分,1-5 分。会话结束后采集。见 [13-user-satisfaction-metrics.md](./13-evaluation/user-satisfaction-metrics.md)。

---

## D

**DeepSeek-V3**
深度求索的通用 LLM,MoE 架构,671B 总参数/37B 激活。CampusShare 主生成模型。见 [16-deepseek-analysis.md](./16-technical-references/deepseek-analysis.md)。

**DeepSeek-R1**
深度求索的推理模型,RL 训练,擅长复杂推理。CampusShare 用于反思。见 [07-reflection-self-validation.md](./07-agent-design/reflection-self-validation.md)。

**Dify**
开源 LLM 应用开发平台,可视化编排。CampusShare 借鉴设计但不引入。见 [16-dify-langchain-analysis.md](./16-technical-references/dify-langchain-analysis.md)。

---

## E

**Embedding**
将文本映射为稠密向量,使语义相近的文本向量距离近。见 [05-embedding-strategy.md](./05-knowledge-base/embedding-strategy.md)。

**Entailment (蕴含)**
NLI(自然语言推理)判定之一,表示文档支持论断(非幻觉)。见 [14-output-guardrails.md](./14-safety-and-guardrails/output-guardrails.md)。

---

## F

**Few-Shot Prompting**
在 prompt 中提供少量示例,引导 LLM 按示例格式输出。见 [08-few-shot.md](./08-prompt-engineering/few-shot-prompting.md)。

**Function Calling**
LLM 调用外部函数/工具的能力。DeepSeek/OpenAI 等模型支持。见 [07-tool-use.md](./07-agent-design/tool-use.md)。

---

## G

**Golden Set (黄金测试集)**
人工标注的 query-期望输出对,用于回归测试。见 [13-golden-test-set.md](./13-evaluation/golden-test-set.md)。

**Groundedness (接地度)**
回答是否基于检索内容而非幻觉的比例。见 [13-retrieval-quality-metrics.md](./13-evaluation/retrieval-quality-metrics.md)。

---

## H

**HNSW (Hierarchical Navigable Small World)**
近似最近邻搜索算法,pgvector 支持的索引类型。见 [05-vector-store.md](./05-knowledge-base/vector-store.md)。

**HyDE (Hypothetical Document Embedding)**
查询扩展技术:先让 LLM 生成假设性回答,用回答的 embedding 检索。见 [06-query-expansion.md](./06-retrieval/query-expansion.md)。

---

## I

**Intent Classification (意图分类)**
将用户查询分类为预定义意图(HOW_TO/SEARCH/NAVIGATE/CLARIFY/OUT_OF_SCOPE)。见 [04-intent-classification.md](./04-intent-understanding/intent-classification.md)。

---

## J

**jtokkit**
Java 的 token 估算库,无需调用 API 即可估算 token 数。见 [12-service-structure.md](./12-backend-microservice/service-structure.md)。

---

## K

**Kappa (Cohen's Kappa)**
见 Cohen's Kappa。

---

## L

**LangChain**
LLM 应用开发框架(Python/JS)。CampusShare 借鉴设计但不引入。见 [16-dify-langchain-analysis.md](./16-technical-references/dify-langchain-analysis.md)。

**LLM-as-Judge**
用一个 LLM 评估另一个 LLM 的输出质量。见 [13-llm-as-judge.md](./13-evaluation/llm-as-judge.md)。

**Loki**
轻量级日志聚合系统,与 Grafana 集成。见 [15-logging-and-tracing.md](./15-observability/logging-and-tracing.md)。

---

## M

**MRR (Mean Reciprocal Rank)**
检索指标,第一个相关文档排名的倒数的平均。见 [13-retrieval-quality-metrics.md](./13-evaluation/retrieval-quality-metrics.md)。

**MMR (Maximal Marginal Relevance)**
在相关性和多样性间平衡的重排算法。见 [06-reranking.md](./06-retrieval/reranking-strategy.md)。

**MoE (Mixture of Experts)**
混合专家模型架构,DeepSeek-V3 采用。总参数大但激活参数小,降低推理成本。见 [16-deepseek-analysis.md](./16-technical-references/deepseek-analysis.md)。

**MDE (Minimum Detectable Effect)**
A/B 测试中可检测的最小效应。用于计算样本量。见 [13-ab-testing-framework.md](./13-evaluation/ab-testing-framework.md)。

---

## N

**NDCG (Normalized Discounted Cumulative Gain)**
检索质量金标准指标,支持分级相关度。见 [13-retrieval-quality-metrics.md](./13-evaluation/retrieval-quality-metrics.md)。

**NLI (Natural Language Inference)**
自然语言推理,判断论断与文档的关系(蕴含/矛盾/中立)。用于幻觉检测。见 [14-output-guardrails.md](./14-safety-and-guardrails/output-guardrails.md)。

**NPS (Net Promoter Score)**
净推荐值,%推荐者 - %贬损者。季度采集。见 [13-user-satisfaction-metrics.md](./13-evaluation/user-satisfaction-metrics.md)。

---

## O

**OpenFeign**
Spring Cloud 的声明式 HTTP 客户端,用于微服务间通信。见 [10-feign-clients.md](./10-tools-and-apis/feign-clients.md)。

**OpenTelemetry (OTel)**
开源可观测性框架,自动注入 traceId/spanId。见 [15-logging-and-tracing.md](./15-observability/logging-and-tracing.md)。

---

## P

**Pairwise Evaluation**
对比评估,让 LLM 判断两个回答(A/B)哪个更好。见 [13-llm-as-judge.md](./13-evaluation/llm-as-judge.md)。

**pg_trgm**
PostgreSQL 的 trigram 扩展,用于模糊匹配/关键词检索。CampusShare 替代 BM25。见 [06-hybrid-retrieval.md](./06-retrieval/hybrid-retrieval.md)。

**pgvector**
PostgreSQL 的向量搜索扩展,支持 HNSW/IVFFlat 索引。见 [05-vector-store.md](./05-knowledge-base/vector-store.md)。

**PII (Personally Identifiable Information)**
个人身份信息(手机/身份证/邮箱等)。输入时脱敏,输出时检测泄露。见 [14-input-guardrails.md](./14-safety-and-guardrails/input-guardrails.md)。

**Pin Message**
用户固定的消息,不被上下文压缩删除。见 [09-context-compression.md](./09-context-engineering/context-compression.md)。

**Prefix Cache**
DeepSeek API 的前缀缓存,固定前缀按 10% 计费。见 [16-deepseek-analysis.md](./16-technical-references/deepseek-analysis.md)。

**Prompt Injection**
Prompt 注入攻击,恶意输入劫持 LLM 行为。见 [14-input-guardrails.md](./14-safety-and-guardrails/input-guardrails.md)。

---

## R

**ReAct (Reasoning + Acting)**
Agent 架构,LLM 交替输出推理(Thought)和行动(Action)。见 [07-agent-architecture.md](./07-agent-design/agent-architecture.md)。

**Recall@K**
检索指标,前 K 结果中相关文档占所有相关文档的比例。见 [13-retrieval-quality-metrics.md](./13-evaluation/retrieval-quality-metrics.md)。

**Red Teaming (红队测试)**
主动构造攻击/越界样本,测试 agent 安全边界。见 [13-e2e-quality-evaluation.md](./13-evaluation/e2e-quality-evaluation.md)。

**Resilience4j**
Spring 生态的容错库,提供熔断器/限流/重试。见 [10-feign-clients.md](./10-tools-and-apis/feign-clients.md)。

**Reranker (重排器)**
对检索结果精排的模型,通常 cross-encoder。见 [06-reranking.md](./06-retrieval/reranking-strategy.md)。

**RRF (Reciprocal Rank Fusion)**
倒数排名融合,将多路检索结果按排名倒数融合。见 [06-hybrid-retrieval.md](./06-retrieval/hybrid-retrieval.md)。

**Rolling Summary (滚动摘要)**
将旧对话消息压缩为摘要,保留关键信息。见 [09-context-compression.md](./09-context-engineering/context-compression.md)。

**Rubric**
评估标准,定义各分数级别的具体标准。见 [13-llm-as-judge.md](./13-evaluation/llm-as-judge.md)。

---

## S

**ShedLock**
分布式定时任务锁,确保多实例中只有一个执行。见 [12-scheduling-tasks.md](./12-backend-microservice/scheduling-tasks.md)。

**SLO (Service Level Objective)**
服务级别目标,如 P95 延迟 ≤ 8s。见 [15-metrics-system.md](./15-observability/metrics-system.md)。

**SSE (Server-Sent Events)**
服务器推送事件,用于流式输出。CampusShare 用 POST + fetch/ReadableStream(非 EventSource)。见 [10-sse-streaming-api.md](./10-tools-and-apis/sse-streaming-api.md)。

**Slot Freezing (槽位冻结)**
上下文压缩策略,将已确认的实体槽位冻结为 KV,不再保留原始对话。见 [09-context-compression.md](./09-context-engineering/context-compression.md)。

**Spring WebFlux**
Spring 的响应式 Web 框架,支持 SSE 流式。CampusShare agent-service 用 WebFlux 而非 Web MVC。见 [12-service-structure.md](./12-backend-microservice/service-structure.md)。

---

## T

**TEI (Text Embeddings Inference)**
HuggingFace 的 embedding 服务镜像,用于部署 BGE 模型。见 [12-docker-integration.md](./12-backend-microservice/docker-integration.md)。

**TTFB (Time To First Byte)**
首 token 延迟,从发送到收到第一个 token 的时间。见 [15-metrics-system.md](./15-observability/metrics-system.md)。

**Tempo**
Grafana 的分布式链路追踪后端。见 [15-logging-and-tracing.md](./15-observability/logging-and-tracing.md)。

---

## W

**WebFlux**
见 Spring WebFlux。

---

## Z

**Zustand**
React 轻量级状态管理库。CampusShare 前端用 Zustand 管理 assistantStore。见 [11-state-management.md](./11-frontend-integration/state-management.md)。

---

## 缩写速查

| 缩写 | 全称 | 含义 |
|------|------|------|
| ADR | Architecture Decision Record | 架构决策记录 |
| API | Application Programming Interface | 应用编程接口 |
| BGE | BAAI General Embedding | 智源通用 Embedding |
| CoT | Chain of Thought | 思维链 |
| CSAT | Customer Satisfaction Score | 满意度评分 |
| E2E | End-to-End | 端到端 |
| HNSW | Hierarchical Navigable Small World | 层级可导航小世界图 |
| HyDE | Hypothetical Document Embedding | 假设性文档嵌入 |
| LLM | Large Language Model | 大语言模型 |
| MDE | Minimum Detectable Effect | 最小可检测效应 |
| MoE | Mixture of Experts | 混合专家 |
| MRR | Mean Reciprocal Rank | 平均倒数排名 |
| MMR | Maximal Marginal Relevance | 最大边际相关性 |
| NDCG | Normalized Discounted Cumulative Gain | 归一化折损累计增益 |
| NLI | Natural Language Inference | 自然语言推理 |
| NPS | Net Promoter Score | 净推荐值 |
| OTel | OpenTelemetry | 开放遥测 |
| PII | Personally Identifiable Information | 个人身份信息 |
| RAG | Retrieval-Augmented Generation | 检索增强生成 |
| ReAct | Reasoning + Acting | 推理+行动 |
| RRF | Reciprocal Rank Fusion | 倒数排名融合 |
| SLO | Service Level Objective | 服务级别目标 |
| SSE | Server-Sent Events | 服务器推送事件 |
| TEI | Text Embeddings Inference | 文本嵌入推理 |
| TTFB | Time To First Byte | 首字节时间 |

---

## 决策记录

### ADR-197: 术语表维护
- **背景**:Agent 文档涉及大量技术术语,需统一解释避免歧义。
- **决策**:维护本术语表,新术语出现时及时补充。按字母排序,附文档引用。
- **状态**:采纳
