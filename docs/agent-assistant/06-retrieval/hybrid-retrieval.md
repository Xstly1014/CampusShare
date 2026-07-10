# 混合检索（Hybrid Retrieval）

> 状态: 草稿  
> 最后更新: 2026-06-30

混合检索是本 Agent 区别于「廉价 RAG」的第一道护城河。单一向量检索在专有名词、缩写、精确匹配上弱；单一 BM25 在语义、同义上弱。两者融合 + 结构化过滤才能达到目标命中率。

## 一、三路检索并行

```
                rewritten_query + slots
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
   ① 向量检索      ② 关键词检索     ③ 结构化检索
   (稠密语义)      (BM25/FULLTEXT)  (过滤+排序)
   Top-20          Top-20           Top-20
        │               │               │
        └───────────────┼───────────────┘
                        ▼
                ④ 融合(RRF/加权)
                        ▼
                Top-20 候选
                        ▼
                ⑤ 重排(进阶 Cross-encoder)
                        ▼
                Top-5 最终
```

### ① 向量检索（语义）
- BGE-M3 稠密向量，PG-Vector HNSW 余弦。
- 输入：query 向量（或 HyDE 文档向量）。
- 过滤：slots 中的 school_id/category_id/post_type 作为 WHERE 条件。
- 召回 Top-20。

### ② 关键词检索（BM25 近似）
- MySQL FULLTEXT（已有 `posts` 表 FULLTEXT 索引）。
- 输入：rewritten_query 的关键词（同义词扩展为 OR）。
- 过滤：同向量检索的 slots。
- 召回 Top-20。
- **进阶**：用 BGE-M3 稀疏向量替代 FULLTEXT，质量更高。

### ③ 结构化检索（精确匹配）
- 当 slots 含明确实体（school + category + type），直接 SQL 查 posts 表按计数排序。
- 用于「北大 计算机网络 期末 卷子」这类强结构化需求。
- 召回 Top-20，按 like_count + comment_count + view_count 加权排序。

## 二、融合策略

### 2.1 RRF（Reciprocal Rank Fusion，推荐）
- 不依赖各路分数的绝对值（向量 cosine 与 BM25 分数量纲不同），只用排名。
- 公式：`score(d) = Σ 1/(k + rank_i(d))`，k 通常取 60。
- 优点：无需调权重，鲁棒。

### 2.2 加权融合（备选）
- 归一化各路分数后加权：`0.5*vec + 0.3*bm25 + 0.2*struct`。
- 权重可 A/B 调，但需归一化处理。

### 2.3 去重
- 同一 post_id 在多路出现只保留 RRF 分数和最高的一次。

## 三、结构化过滤

slots → WHERE 条件：
```sql
WHERE deleted = 0 AND status = 1
  AND (school_id = ? OR ? IS NULL)
  AND (category_id = ? OR ? IS NULL)
  AND (sub_category_id = ? OR ? IS NULL)
  AND (post_type = ? OR ? IS NULL)
```

排序偏好（slots.sort）：
- `latest`：ORDER BY create_time DESC
- `hot`：ORDER BY (like_count*2 + comment_count*3 + view_count*0.1) DESC

## 四、召回为空的处理

- 三路全空 → 触发「澄清」：返回「未找到完全匹配，是否要找 [相近分类]？」
- 向量有但关键词无 → 用向量结果即可。
- 结构化过滤太严导致空 → 逐步放宽（先去 sub_category，再去 category）。

## 五、性能预算

| 步骤 | 延迟 |
|------|------|
| query 向量化 | 100-200ms |
| 向量检索 | 30-80ms |
| 关键词检索 | 50-150ms |
| 结构化检索 | 30-80ms |
| 融合 + 去重 | < 10ms |
| **并行三路总** | **≤ 300ms** |

三路并行（CompletableFuture），取最慢一路。

## 六、决策记录 (ADR)

### ADR-023: 用 RRF 融合而非加权
- **理由**：RRF 无需调权重、对分数量纲不敏感、工业界验证充分（Elasticsearch 默认融合用 RRF）。
- **备选**：加权融合需归一化与调参，维护成本高。

### ADR-024: 三路并行 + 结构化过滤内嵌
- **理由**：把结构化过滤下推到每路检索的 WHERE，避免召回大量无关再过滤（向量库过滤后置性能差）。
- **PG-Vector 优势**：HNSW + WHERE 可联合，PG-Vector 原生支持。
