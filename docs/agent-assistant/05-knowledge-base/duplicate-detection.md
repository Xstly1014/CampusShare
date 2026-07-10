# 重复检测

> 状态: 草稿  
> 最后更新: 2026-07-06

知识库文档摄入时检测重复内容，避免冗余索引。

## 一、双阈值检测

| 相似度 | 级别 | 处理 |
|--------|------|------|
| ≥ 0.95 | DUPLICATE | 跳过摄入，记录日志 |
| ≥ 0.85 | SIMILAR | 仍摄入，warn 日志提示合并 |
| < 0.85 | UNIQUE | 正常摄入 |

**实现**：`ThresholdDuplicateDetector.detect(content, embedding)`

## 二、代表性分块设计

仅用 `chunk_index=0`（首个分块）的 embedding 查询最相似文章：
```
SELECT article_id, 1 - (embedding <=> ?::vector) AS similarity
FROM knowledge_vectors
WHERE chunk_index = 0 AND status = 'PUBLISHED'
ORDER BY embedding <=> ?::vector
LIMIT 1
```

**理由**：
1. 首块通常含标题 + 概述，最具代表性。
2. 避免全分块查询的性能开销（N 个分块 → N 次查询）。
3. 与摄入时检测逻辑一致（摄入时用 `embeddings.get(0)` 检测）。

## 三、异常降级策略

| 异常场景 | 降级行为 |
|---------|---------|
| embedding 为 null 或空 | 直接返回 UNIQUE（不调 findSimilar） |
| findSimilar 抛异常 | 降级为 UNIQUE，记录 warn 日志 |
| findSimilar 返回 null | 返回 UNIQUE |

**设计原则**：重复检测异常不应阻塞摄入流程，降级为 UNIQUE 保证可用性。

## 四、Metrics 监控

```
agent.knowledge.duplicate.detected{level=DUPLICATE|SIMILAR|UNIQUE}
```
- counter 类型，按 level 分标签统计。
- 用于监控重复检测分布，异常多的 DUPLICATE 可能提示知识库内容重复严重。

## 五、决策记录 (ADR)

### ADR-022: 双阈值选择理由
- **0.95 高阈值（DUPLICATE）**：确保近乎完全重复才跳过，避免误杀相似但不同的文档。
- **0.85 中阈值（SIMILAR）**：提示合并但允许人工判断，留出 0.85-0.95 的灰度区间。
- **0.85 以下（UNIQUE）**：正常摄入，不干预。
- **为何不用单阈值**：单阈值（如 0.9）无法区分"几乎完全相同"与"主题相似但内容不同"，双阈值提供更精细的控制。
