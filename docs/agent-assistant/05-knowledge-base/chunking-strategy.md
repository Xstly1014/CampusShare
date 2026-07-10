# 分块策略

> 状态: 草稿  
> 最后更新: 2026-07-06

分块（Chunking）直接决定检索粒度与生成质量。

## 一、知识库分块

### 1.1 Markdown 结构分块（H2→H3→段落→句子）
- 知识文档按 Markdown 结构四级分块，保留语义完整性。
- **分块顺序**：H2 二级标题 → H3 三级标题 → 段落（`\n\n`）→ 句子（`[。！？.!?]`）。
- **目标 256 Token / 最大 512 Token / 重叠 50 Token**（jtokkit cl100k_base 计数，近似 BGE-M3 tokenizer）。
- 每块自带 **headingPath 上下文头**（如 `# 如何申请创作者认证 > ## 审核流程`），确保独立可理解。
- metadata：`{article_id, chunk_index, title, topic, heading_path, content_md5, version, quality_score, token_count}`。

### 1.2 超长单句兜底
- 单句超过 maxTokens(512) 时强制作为独立块，不再拆分（避免破坏语义）。
- 段落超过 maxTokens 时按句子边界拆分，累积达 targetTokens(256) 输出 chunk。

### 1.3 Q-A 对额外索引
- 文档内「常见问题」的每个 Q-A 单独作为一块，便于精准命中。
- metadata 标 `chunk_type: "qa"`。

## 二、帖子分块

帖子是检索主体，分块策略更关键。

### 2.1 帖子级 chunk（主）
- 一个帖子 = 一个 chunk，向量来源：`标题 + 正文(截断前 500 字) + 分类名 + 学校名`。
- 不拆分到段落级，因为：
  - 帖子平均长度短（多数 < 1000 字）。
  - 用户要的是整帖，段落级召回后还要聚合。
- metadata：`{post_id, title, author_id, school_id, category_id, sub_category_id, post_type, like_count, comment_count, view_count, file_url, create_time, status, deleted}`。

### 2.2 评论级 chunk（进阶）
- 高质量评论（点赞数 > 阈值）单独索引，用于「帖子内容问答」场景（用户问 TCP 三次握手，可能答案在某帖的评论里）。
- metadata：`{comment_id, post_id, content, like_count}`，并标注 `chunk_type: "comment"`。

### 2.3 文件名/文件类型索引
- 资源贴的 `file_name`、`file_type` 单独作为可检索字段，便于「求 PDF」「求卷子」类需求。

## 三、分块大小控制

| chunk 类型 | 目标 Token | 最大 Token | 重叠 Token |
|-----------|----------|----------|----------|
| 知识文档(H2→H3→段落→句子) | 256 | 512 | 50 |
| 帖子(整帖) | 100-1000 字 | 2000 字 | — |
| 评论 | 50-300 字 | 500 字 | — |

> 知识文档按 Token 计（jtokkit cl100k_base）；帖子/评论按字数计，Embedding 前按 BGE-M3 的 512 token 窗口截断。

## 四、分块质量保障

- 每块必须自带可独立理解的上下文头（如「[创作者认证申请] 审核流程：...」）。
- 避免「断句」式拆分破坏语义。
- 分块时记录 `chunk_index`、`parent_doc_id`，便于回溯。

## 五、增量与去重

- 帖子更新（编辑）→ 重新向量化该 post_id 的 chunk，覆盖旧向量。
- 帖子删除（逻辑删除 deleted=1）→ 从向量库移除（或标记不可见）。
- 去重：同 post_id 只保留一份向量。

## 六、决策记录 (ADR)

### ADR-018: 帖子整帖为一个 chunk
- **理由**：帖子短、用户要整帖、避免聚合复杂度。
- **例外**：超长帖（>2000 字）按段落拆，但每段带帖子标题头。

### ADR-019: 评论单独索引（进阶）
- **理由**：内容问答场景答案常在评论；但 MVP 先不做，避免索引膨胀。

### ADR-020: 知识库分块采用 H2→H3→段落→句子四级分块（覆盖 ADR-018/019 知识文档部分）
- **决策**：知识文档从"整篇优先"改为 Markdown 结构四级分块，目标 256 Token，最大 512 Token，重叠 50 Token。
- **理由**：
  - 知识文档结构化程度高（H2/H3 标题明确），按结构分块保留语义边界。
  - 256 Token 目标平衡召回精度与上下文完整性（BGE-M3 最优窗口 512 Token，留 50 Token 重叠）。
  - 整篇优先导致长文档召回精度低（整篇 embedding 稀释关键信息）。
- **覆盖范围**：仅知识文档（`knowledge_articles`），帖子分块策略（ADR-018）不变。
- **实现**：`MarkdownChunker`（jtokkit cl100k_base 计数 + headingPath 上下文头 + overlapTokens 重叠）。
