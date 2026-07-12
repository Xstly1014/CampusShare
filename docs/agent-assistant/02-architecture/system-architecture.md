# 系统架构

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、架构总览图（文字版）

```
┌─────────────────────────────────────────────────────────────────────┐
│                           用户浏览器                                │
│  前端 Agent 页面（/assistant，NavBar 第 2 个入口）                  │
│  - 聊天 UI + 流式渲染 + 引用卡片 + 👍👎                            │
└───────────────┬─────────────────────────────────────────────────────┘
                │ SSE (Server-Sent Events) + REST
                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Nginx(frontend:80)  →  /api/*  →  gateway(8080)                    │
│  gateway: JWT 认证 → 注入 X-User-Id/X-Username                      │
└───────────────┬─────────────────────────────────────────────────────┘
                │ /api/assistant/** → campushare-agent
                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                campushare-agent 微服务 (端口 8083)                  │
│                                                                     │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────────────┐  │
│  │ 对话管理器    │→ │ 意图理解/路由 │→ │  Agent 编排器(ReAct)    │  │
│  │ Session/Mem  │   │ Intent+Rewrite│  │  Plan → Tool → Reflect │  │
│  └──────────────┘   └──────────────┘   └─────────┬──────────────┘  │
│                                                   │                 │
│        ┌──────────────────────────────────────────┼──────────┐      │
│        ▼              ▼                ▼           ▼          ▼      │
│   ┌─────────┐  ┌───────────┐   ┌──────────┐ ┌────────┐ ┌────────┐  │
│   │知识库检索│  │帖子混合检索│   │板块/分类 │ │用户信息│ │LLM 调用│  │
│   │(帮助中心)│  │向量+BM25  │   │定位工具  │ │工具    │ │层      │  │
│   └────┬────┘  └─────┬─────┘   └────┬─────┘ └───┬────┘ └───┬────┘  │
│        │             │              │           │          │       │
│        ▼             ▼              ▼           ▼          ▼       │
│   ┌─────────────────────────────────────────────────────────┐       │
│   │  向量库(Milvus/PG-Vector) │ Redis(缓存/会话) │ Embedding│       │
│   └─────────────────────────────────────────────────────────┘       │
└────┬──────────────────────────────────────────────┬─────────────────┘
     │ Feign (Internal API, /api/internal/**)       │ LLM API (HTTPS)
     ▼                                              ▼
┌──────────────────────┐               ┌────────────────────────────┐
│  post-service(8082)  │               │  LLM Provider              │
│  InternalPostController              │  (DeepSeek / 豆包 / Qwen)   │
│  - 语义检索内部接口  │               │                            │
│  - 帖子详情/列表      │               └────────────────────────────┘
└──────────────────────┘
┌──────────────────────┐
│  user-service(8081)  │
│  InternalUserController
│  - 用户公开信息      │
└──────────────────────┘
```

## 二、核心组件职责

### 2.1 对话管理器 (Conversation Manager)
- 维护会话状态（session_id、短期记忆、用户画像缓存）。
- 负责流式协议（SSE）的生命周期管理。
- 多轮上下文拼接与压缩（见 `09-context-engineering`）。

### 2.2 意图理解与路由 (Intent & Router)
- 意图分类（HOW_TO/SEARCH/NAVIGATE/CLARIFY/OUT_OF_SCOPE）。
- 查询改写（同义词、缩写、HyDE、指代消解）。
- 路由到对应处理管线（见 `04-intent-understanding`）。

### 2.3 Agent 编排器 (Agent Orchestrator)
- ReAct 风格循环：Thought → Action(Tool) → Observation → ... → Final Answer。
- 进阶：多 Agent 分工（检索 Agent / 知识 Agent / 导航 Agent），由 Planner 编排。
- 反思自校验（见 `07-agent-design`）。

### 2.4 工具集 (Tools)
- 知识库检索工具（帮助中心）。
- 帖子混合检索工具。
- 板块/分类定位工具。
- 用户信息工具（查当前用户公开统计，用于个性化）。
- 详见 `10-tools-and-apis`。

### 2.5 LLM 调用层
- 封装多 Provider（DeepSeek/豆包/Qwen），统一接口。
- 流式/非流式切换。
- 重试、超时、降级（见 `03-llm-strategy`）。

### 2.6 检索层
- 帮助中心知识库：独立小向量库（更新频繁）。
- 帖子检索：向量库 + BM25 + 结构化过滤 + 重排（见 `06-retrieval`）。

## 三、与现有架构的契合点

1. **遵循微服务铁律**：Agent 服务不直连 posts/users 表，跨服务走 Feign Internal API。
2. **复用网关认证**：Agent 接口走 gateway，JWT 认证后注入 `X-User-Id`，无需自己鉴权。
3. **复用监控体系**：OTel Agent 注入，trace 上报到现有 Tempo，metrics 到 Prometheus。
4. **复用 Docker 编排**：在 `docker-compose.yml` 新增 `agent-service`，依赖 mysql/redis。
5. **前端复用**：复用 `services/http.ts`、Toast、AuthContext、路由守卫。

## 四、新增依赖

| 依赖 | 用途 | MVP 是否必须 |
|------|------|:---:|
| 向量库(Milvus/PG-Vector) | 帖子+知识库向量存储 | ✅ |
| Embedding 模型 API | 文本向量化 | ✅ |
| LLM API | 生成 | ✅ |
| BM25 引擎(MySQL FULLTEXT/Elasticsearch) | 关键词检索 | ✅（先用 MySQL FULLTEXT） |
| Cross-encoder 重排模型 | 重排序 | 进阶 |
| Redis(已有) | 会话缓存、热点缓存 | ✅ |

## 五、决策记录 (ADR)

### ADR-003: 向量库选型 PG-Vector 优先
- **背景**：Milvus 强但运维重；项目已用 MySQL，未用 PostgreSQL。
- **决策**：MVP 用 PG-Vector（轻量、SQL 友好、易部署）；帖子量超 50 万或性能不足时迁移 Milvus。
- **理由**：降低运维复杂度，复用关系数据库能力做结构化过滤；与现有 MySQL 并存（PG 专存向量）。
- **备选**：Milvus（性能强但需独立集群）、Qdrant、Elasticsearch kNN。

### ADR-004: 不引入 Elasticsearch（MVP）
- **背景**：ES 提供很强 BM25 + kNN 混合检索。
- **决策**：MVP 用 MySQL FULLTEXT 做 BM25 近似 + PG-Vector 做向量，进阶再评估 ES。
- **理由**：避免引入 ES 集群增加运维负担；当前数据量 MySQL FULLTEXT 足够。
