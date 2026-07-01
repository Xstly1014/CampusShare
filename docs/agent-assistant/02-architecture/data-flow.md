# 数据流

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、主问答数据流（典型 SEARCH 场景）

```
用户输入「求清华操作系统期末复习资料」
  │
  ▼
[前端] POST /api/assistant/chat (session_id, message) → 升级为 SSE
  │
  ▼
[gateway] JWT 认证 → 注入 X-User-Id → 路由到 agent-service
  │
  ▼
[agent-service] ConversationManager 接收
  │
  ├─ 1. 加载短期记忆（Redis: session:{id}:memory）
  ├─ 2. IntentRouter 意图分类
  │     → LLM(意图分类 prompt) → SEARCH
  ├─ 3. QueryRewriter 查询改写
  │     → 「清华 操作系统 期末 复习 资料」+ 同义词(OS=操作系统)
  ├─ 4. AgentOrchestrator (ReAct)
  │     Thought: 需检索帖子
  │     Action: search_posts(query=..., school=清华, type=resource)
  │     │
  │     ▼
  │     [Tool: search_posts]
  │       ├─ Feign→post-service /api/internal/posts/semantic-search
  │       │   (向量检索 + FULLTEXT + 过滤)
  │       ├─ 召回 Top-20 → 重排 Top-5
  │       └─ 返回 Observation: [帖子1, 帖子2, ...]
  │     Thought: 已有足够信息，生成答案
  │     Action: finalize
  │     │
  │     ▼
  │     [LLM 生成] 流式输出答案 + 引用编号
  │
  ▼
[前端] SSE 接收:
  - event: status  data: "正在检索..."
  - event: tool    data: {tool:"search_posts", count:5}
  - event: token   data: "根据检索..." (逐 token)
  - event: refs    data: [{id, title, type, jump}]
  - event: done    data: {message_id}
  │
  ▼
[前端] 渲染气泡 + 引用卡片 + 👍👎
  │
  ▼ (用户点 👍)
[前端] POST /api/assistant/feedback {message_id, rating:up}
  │
  ▼
[agent-service] 入库 agent_feedback
```

## 二、HOW_TO 场景数据流

```
用户「怎么成为创作者」
  → 意图: HOW_TO
  → 查询改写: 创作者认证
  → Agent Action: search_knowledge(query="创作者认证")
      → 知识库向量检索 → 返回帮助文档片段
  → LLM 生成: 步骤 + 跳转入口(/creator-verification)
  → 前端渲染: 文本 + 跳转按钮
```

## 三、多轮 CLARIFY 数据流

```
T1: 「求 OS 资料」→ SEARCH → 检索清华帖 → 返回列表
T2: 「那个有下载链接的是哪个」
  → 意图: CLARIFY (指代 T1 结果)
  → 上下文: T1 返回的帖子列表 + 当前 query
  → 不再检索，直接从上下文帖子中筛选 file_url 非空的
  → 返回具体帖 + 引用
```

## 四、帖子向量化数据流（离线同步）

```
[post-service] 帖子创建/更新
  → 写 posts 表
  → 发事件到 Redis Stream / 或定时任务扫描 update_time
[agent-service] 增量同步消费者
  → Feign 取帖子内容(标题+正文摘要)
  → 调 Embedding API 向量化
  → 写向量库 (PG-Vector)
```

## 五、流式协议（SSE）事件定义

| event | data | 说明 |
|-------|------|------|
| `status` | 字符串 | "正在理解问题..." / "正在检索..." / "正在生成回答..." |
| `tool` | `{tool, args, result_summary}` | 工具调用过程（可折叠展示） |
| `token` | 字符串 | LLM 输出的 token 片段 |
| `refs` | `[{ref_id, type, title, jump_url, snippet}]` | 引用列表 |
| `error` | `{code, message}` | 错误 |
| `done` | `{message_id}` | 结束 |

## 六、错误与降级流

| 故障 | 降级 |
|------|------|
| LLM 主模型超时 | 切兜底模型（见 `03-llm-strategy`） |
| LLM 全部不可用 | 返回「服务暂时繁忙」+ 已检索到的帖子卡（若有） |
| post-service Feign 失败 | 返回知识库结果 + 提示「帖子检索暂不可用」 |
| 向量库不可用 | 退化到纯 FULLTEXT 关键词检索 |
| Embedding 失败 | 用关键词检索兜底 |
