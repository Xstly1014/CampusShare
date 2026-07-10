# 工具详细规格

> 状态: 草稿
> 最后更新: 2026-06-30

逐一定义每个工具的参数、返回、超时、降级策略。所有工具返回统一三段式：`{summary: string, data: object, refs: array}`。

## 一、search_posts（搜索帖子）

### 1.1 用途
混合检索（向量+BM25+结构化）召回帖子，返回 Top-K 摘要。

### 1.2 参数

```json
{
  "type": "object",
  "properties": {
    "query": {"type": "string", "description": "搜索词，已过查询改写"},
    "category": {"type": "string", "description": "主分类名，如'软件'"},
    "school": {"type": "string", "description": "学校名，如'清华大学'"},
    "post_type": {"type": "string", "enum": ["resource", "discussion"]},
    "limit": {"type": "integer", "default": 10, "max": 20}
  },
  "required": ["query"]
}
```

### 1.3 返回

```json
{
  "summary": "找到 12 篇相关帖子，Top-3: ...",
  "data": {
    "total": 12,
    "posts": [
      {
        "post_id": "uuid",
        "title": "考研真题合集",
        "post_type": "resource",
        "category": "软件",
        "school": null,
        "author_name": "张三",
        "author_verified": true,
        "excerpt": "前 200 字摘要...",
        "like_count": 156,
        "view_count": 2340,
        "created_at": "2026-06-20",
        "score": 0.92
      }
    ]
  },
  "refs": ["post:uuid1", "post:uuid2"]
}
```

### 1.4 超时与降级

- timeout: 3000ms
- 失败降级：仅走 BM25 关键词检索（跳过向量），返回 `degraded=true`。
- 全失败：返回空结果 + summary="检索服务暂时不可用，请稍后再试"。

## 二、get_post_detail（帖子详情）

### 2.1 用途
获取指定帖子的完整信息，用于用户追问"第 X 条详细说说"。

### 2.2 参数

```json
{
  "type": "object",
  "properties": {
    "post_id": {"type": "string", "format": "uuid"},
    "include_comments": {"type": "boolean", "default": false, "description": "是否包含评论摘要"}
  },
  "required": ["post_id"]
}
```

### 2.3 返回

```json
{
  "summary": "《考研真题合集》资源贴，作者张三（认证用户），含 3 个附件",
  "data": {
    "post_id": "uuid",
    "title": "考研真题合集",
    "content": "完整正文...",
    "post_type": "resource",
    "category": "软件",
    "author": {"id":"uuid","name":"张三","verified":true},
    "attachments": [{"file_name":"真题.pdf","file_size":5242880,"file_url":"..."}],
    "like_count": 156,
    "comment_count": 23,
    "comments_preview": [{"id":"uuid","author":"李四","content":"感谢分享"}]
  },
  "refs": ["post:uuid"]
}
```

### 2.4 超时与降级
- timeout: 2000ms
- 失败：返回 summary="获取详情失败"，data={}。

## 三、get_hot_posts（热门帖）

### 3.1 用途
按分类/学校获取热门帖，用于 NAVIGATE 意图"软件分类下有什么"。

### 3.2 参数

```json
{
  "type": "object",
  "properties": {
    "category": {"type": "string"},
    "school": {"type": "string"},
    "time_window": {"type": "string", "enum": ["day","week","month","all"], "default": "week"},
    "limit": {"type": "integer", "default": 10, "max": 20}
  }
}
```

### 3.3 返回
同 search_posts 的 data.posts 结构，但无 score 字段，按 like_count+view_count 加权排序。

### 3.4 超时与降级
- timeout: 2000ms
- 失败：返回空数组。

## 四、search_knowledge（知识库检索）

### 4.1 用途
检索帮助文档/使用教程，用于 HOW_TO 意图。

### 4.2 参数

```json
{
  "type": "object",
  "properties": {
    "query": {"type": "string"},
    "topic": {"type": "string", "description": "主题分类，如'通知设置'/'创作者认证'"},
    "limit": {"type": "integer", "default": 5, "max": 10}
  },
  "required": ["query"]
}
```

### 4.3 返回

```json
{
  "summary": "找到 3 篇相关帮助文档",
  "data": {
    "articles": [
      {
        "article_id": 12,
        "title": "如何关闭通知",
        "topic": "通知设置",
        "content_excerpt": "前 300 字...",
        "full_url": "/help/article/12",
        "score": 0.89
      }
    ]
  },
  "refs": ["knowledge:12", "knowledge:13"]
}
```

### 4.4 超时与降级
- timeout: 2000ms
- 失败：降级为关键词匹配（跳过向量）。

## 五、get_help_article（帮助文档全文）

### 5.1 用途
根据 article_id 获取完整文档，用于 LLM 生成详细步骤回答。

### 5.2 参数

```json
{
  "type": "object",
  "properties": {
    "article_id": {"type": "integer"}
  },
  "required": ["article_id"]
}
```

### 5.3 返回

```json
{
  "summary": "《如何关闭通知》共 5 步",
  "data": {
    "article_id": 12,
    "title": "如何关闭通知",
    "topic": "通知设置",
    "content": "完整 Markdown 正文...",
    "updated_at": "2026-06-15"
  },
  "refs": ["knowledge:12"]
}
```

## 六、list_categories（列出分类）

### 6.1 用途
返回所有主分类与子分类树，用于 NAVIGATE。

### 6.2 参数
无参数。

### 6.3 返回

```json
{
  "summary": "共 12 个主分类，66 个子分类",
  "data": {
    "categories": [
      {"id":1,"name":"软件","icon":"💻","sub_categories":[
        {"id":11,"name":"开发工具"},
        {"id":12,"name":"设计软件"}
      ]}
    ]
  },
  "refs": []
}
```

### 6.4 缓存
结果 Redis 缓存 1 小时（分类极少变更）。

## 七、list_schools（列出学校）

### 7.1 参数
无。

### 7.2 返回

```json
{
  "summary": "共 8 所高校",
  "data": {
    "schools": [
      {"id":1,"name":"清华大学","logo_url":"...","post_count":1234}
    ]
  },
  "refs": []
}
```

### 7.3 缓存
Redis 缓存 30 分钟。

## 八、get_category_path（推断分类路径）

### 8.1 用途
用户问"我想找 Python 教程"，推断应去"软件→开发工具"分类。LLM 调用此工具验证推断。

### 8.2 参数

```json
{
  "type": "object",
  "properties": {
    "keywords": {"type": "array", "items": {"type": "string"}}
  },
  "required": ["keywords"]
}
```

### 8.3 返回

```json
{
  "summary": "关键词'Python 教程'最匹配分类: 软件→开发工具",
  "data": {
    "matched_paths": [
      {"category":"软件","sub_category":"开发工具","score":0.91},
      {"category":"读书","sub_category":"编程书籍","score":0.65}
    ]
  },
  "refs": ["category:1","sub_category:11"]
}
```

## 九、get_user_profile（用户信息）

### 9.1 用途
获取某用户的公开信息，用于"这个作者是谁"类追问。

### 9.2 参数

```json
{"type":"object","properties":{"user_id":{"type":"string","format":"uuid"}},"required":["user_id"]}
```

### 9.3 返回

```json
{
  "summary": "张三，认证创作者，发帖 156 篇",
  "data": {
    "user_id":"uuid","nickname":"张三","avatar_url":"...",
    "verified":true,"bio":"分享学习资料","post_count":156
  },
  "refs": ["user:uuid"]
}
```

### 9.4 隐私
仅返回用户公开字段（受 user-service 隐私设置控制）。

## 十、verify_citation（引用校验，反思阶段）

### 10.1 用途
反思阶段校验 LLM 生成的引用 [n] 是否真实对应检索结果。

### 10.2 参数

```json
{
  "type":"object",
  "properties":{
    "citations":{"type":"array","items":{"type":"object","properties":{
      "marker":{"type":"string","description":"如 [1]"},
      "claimed_ref":{"type":"string","description":"如 post:uuid"},
      "context_quote":{"type":"string"}
    }}}
  },
  "required":["citations"]
}
```

### 10.3 返回

```json
{
  "summary":"3 条引用，2 条有效，1 条虚构",
  "data":{
    "results":[
      {"marker":"[1]","valid":true,"actual_ref":"post:uuid1"},
      {"marker":"[2]","valid":true,"actual_ref":"post:uuid2"},
      {"marker":"[3]","valid":false,"reason":"未在检索结果中找到对应记录"}
    ]
  },
  "refs":[]
}
```

## 十一、通用错误码

| 错误码 | 含义 | LLM 应对策略 |
|--------|------|------------|
| TOOL_TIMEOUT | 工具超时 | 换降级路径或告诉用户"暂时无法查询" |
| TOOL_ARGS_INVALID | 参数校验失败 | LLM 修正参数重试 |
| TOOL_NOT_FOUND | 工具未注册 | 不应发生，记录 bug |
| TOOL_FORBIDDEN | 无权限 | 告诉用户无权操作 |
| TOOL_UPSTREAM_ERROR | Feign 调用上游失败 | 重试或降级 |
| TOOL_BUDGET_EXCEEDED | 会话工具预算耗尽 | 停止调用，用已有信息回答 |

## 十二、决策记录 (ADR)

### ADR-075: 工具返回三段式 summary+data+refs
- **理由**：summary 让 LLM 自然语言理解结果，data 提供结构化字段供后续工具使用，refs 用于前端引用渲染。
- **替代**：纯 JSON 返回——LLM 需要自己写 summary，浪费 output token。

### ADR-076: 工具结果按 L3 预算截断
- **理由**：单个工具返回过大（如 20 篇帖子全文）会撑爆上下文。截断策略：保留 summary + Top-K data + 全部 refs。
- **可调**：LLM 可通过 `limit` 参数主动控制返回量。

### ADR-077: list_categories/list_schools 长缓存
- **理由**：分类与学校极少变更（月级），Redis 缓存 1h 避免重复查询 DB。
- **失效**：管理后台变更分类时主动 DEL 缓存。

### ADR-078: 引用校验作为独立工具
- **理由**：反思阶段需要可重复调用的校验逻辑，封装为工具便于 LLM 编排。
- **代价**：增加一次工具调用开销，但仅在反思阶段触发，低频。

### ADR-079: 错误码 LLM 可读
- **理由**：错误码 + 文字说明一起返回给 LLM，让 LLM 自行决定是重试、降级还是告诉用户失败。
- **实现**：tool_result 里 `error_code` + `error_message` 双字段。
