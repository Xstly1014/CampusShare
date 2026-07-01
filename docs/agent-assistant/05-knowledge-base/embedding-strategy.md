# Embedding 策略

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、模型选型

- **主模型**：BGE-M3（BAAI/智源）
  - 维度：1024
  - 多语言（中文 SOTA）
  - 支持稠密 + 稀疏 + ColBERT 三模式
  - 上下文窗口：8192 token
- **备选**：`bge-large-zh-v1.5`（1024 维，纯稠密）、`text-embedding-3-large`（OpenAI，3072 维）。

## 二、向量化内容构造

### 2.1 帖子向量
```
[帖子标题] | 分类: {category_name} > {subcategory_name} | 学校: {school_name} | 类型: {resource|discussion}
{正文前 500 字}
```
- 拼接分类/学校/类型作为前缀，让向量携带结构信息，提升过滤内检索质量。
- 例：「操作系统期末复习笔记 | 分类: 校园 > 清华大学 | 学校: 清华大学 | 类型: 资源贴\n进程管理是操作系统的核心功能...」

### 2.2 知识库向量
```
[文档标题] | 分类: {category}
{文档全文}
```

### 2.3 Query 向量
- 用户 query 原文（或 HyDE 生成的假设文档）直接向量化，不加前缀。

## 三、多模式检索（BGE-M3 特性）

BGE-M3 同时输出稠密、稀疏、ColBERT 三种向量：
- **稠密（Dense）**：语义检索主力。
- **稀疏（Sparse）**：关键词加权，类似 BM25，捕获专有名词。
- **ColBERT**：token 级交互，精度高但存储大。

MVP 策略：
- 主用**稠密**（性价比高）。
- 进阶叠加**稀疏**做混合（替代 MySQL FULLTEXT 的 BM25）。
- ColBERT 暂不用（存储成本高，PG-Vector 不原生支持）。

## 四、归一化与距离度量

- BGE-M3 输出已归一化，用**余弦相似度**（cosine）。
- PG-Vector 用 `<=>` 操作符（cosine distance）。

## 五、批量化与缓存

- 帖子批量向量化：单次 API 调用批量 64 条，控制单条 ≤ 512 token。
- Query 向量化：单条，结果缓存（Redis: `emb:{sha256(query)}` → vector，TTL 1h）。

## 六、维度与存储

- 1024 维 × 4 字节(float) = 4KB/向量。
- 10 万帖 ≈ 400MB，50 万帖 ≈ 2GB（PG-Vector HNSW 索引加成约 2-3x）。
- HNSW 索引参数：`m=16, ef_construction=64`，查询时 `ef_search=40`（可调）。

## 七、决策记录 (ADR)

### ADR-020: BGE-M3 主用稠密 + 稀疏混合
- **理由**：稀疏模式天然替代 BM25，且与稠密同源，避免维护两套索引。
- **MVP 简化**：先用稠密 + MySQL FULLTEXT，进阶切 BGE 稀疏替代 FULLTEXT。

### ADR-021: 向量拼接结构前缀
- **理由**：让向量携带分类/学校信息，即便不做元数据过滤也能语义上偏向正确板块。
- **风险**：前缀过长稀释正文语义 → 控制前缀简洁。
