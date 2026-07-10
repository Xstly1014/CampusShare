# 质量评分

> 状态: 草稿  
> 最后更新: 2026-07-06

知识库文档质量评分用于检索结果排序加权，高质量文档优先返回。

## 一、四维评分模型

总分 = 召回频次 × 0.4 + 用户反馈 × 0.3 + 新鲜度 × 0.2 + 完整度 × 0.1，范围 [0.0, 1.0]。

| 维度 | 权重 | 归一化算法 | 说明 |
|------|------|----------|------|
| 召回频次 | 0.4 | log 归一化 | 高频召回代表内容价值高 |
| 用户反馈 | 0.3 | 线性 [0,1] | 点赞/点踩调整后的值 |
| 新鲜度 | 0.2 | 线性衰减 | 时效性，30 天内满分 |
| 完整度 | 0.1 | 阶梯 | 分块数越多越完整 |

## 二、各维度归一化算法

### 2.1 召回频次（log 归一化）
```
normalizeRecall(x) = log(1 + x) / log(1 + 100)
```
- 0 次 → 0.0
- 1 次 ≈ 0.15
- 10 次 ≈ 0.52
- 50 次 ≈ 0.85
- 100+ 次 → 1.0（封顶）

**理由**：log 归一化避免高频文档垄断评分，同时区分低频文档。

### 2.2 用户反馈（线性）
```
normalizeFeedback = clamp(feedbackScore, 0.0, 1.0)
```
- 正反馈（点赞）→ feedbackScore + 0.05
- 负反馈（点踩）→ feedbackScore - 0.05
- 初始值 0.5，clamp 到 [0, 1]

### 2.3 新鲜度（线性衰减）
```
days = (now - updatedAt).toDays()
normalizeFreshness =
  1.0                           if days ≤ 30
  1.0 - (days - 30) / 60        if 30 < days < 90
  0.0                           if days ≥ 90
```
- 今天 → 1.0
- 30 天前 → 1.0（30 天内满分）
- 60 天前 → 0.5（线性衰减中间值）
- 90 天前 → 0.0
- null → 0.0

### 2.4 完整度（阶梯）
```
normalizeCompleteness =
  0.0                    if chunkCount = 0
  0.1 + 0.2 * chunkCount if 1 ≤ chunkCount < 5
  1.0                    if chunkCount ≥ 5
```
- 0 块 → 0.0
- 1 块 → 0.3
- 2 块 → 0.5
- 3 块 → 0.7
- 5+ 块 → 1.0（封顶）

## 三、评分应用

检索时对召回结果按质量评分加权：
```
finalScore = rrfScore * (0.8 + 0.2 * qualityScore)
```
- `rrfScore`：倒数排名融合分数（向量检索 + 关键词检索）
- `qualityScore`：四维质量评分
- 权重 0.8/0.2 平衡 RRF 相关性与文档质量，避免质量评分过度影响排序

### 文章级聚合
同一 `article_id` 的多分块命中时：
1. 取该文章最高分块分数作为文章代表分
2. 多命中加成：`articleScore = max(chunkScore) * (1 + 0.1 * (hitCount - 1))`

## 四、评分更新时机

| 事件 | 更新字段 | 触发方式 |
|------|---------|---------|
| 文档摄入 | qualityScore 初始计算 | KnowledgeIngestionService |
| 用户点赞 | feedbackScore + 0.05 → qualityScore 重算 | KnowledgeVersionService.updateFeedback |
| 用户点踩 | feedbackScore - 0.05 → qualityScore 重算 | KnowledgeVersionService.updateFeedback |
| 召回命中 | recallCount + 1（异步） | RetrievalService.asyncIncrementRecall |
| 重新摄入 | chunkCount + qualityScore 重算 | KnowledgeIngestionService.reingestArticle |

## 五、决策记录 (ADR)

### ADR-021: 四维评分权重选择
- **召回频次 0.4（最高）**：高频召回代表内容价值，是最直接的质量信号。
- **用户反馈 0.3（次之）**：用户点赞/点踩代表认可度，但易受主观因素影响。
- **新鲜度 0.2**：时效性重要但非决定性，旧文档可能仍有价值。
- **完整度 0.1（最低）**：分块数仅反映文档长度，不直接代表质量，作辅助信号。
