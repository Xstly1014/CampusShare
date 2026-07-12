# 重排序（Re-ranking）

> 状态: 草稿  
> 最后更新: 2026-06-30

重排是混合检索后的精排步骤，用更强的模型对 Top-20 候选重新打分，把最相关的提到 Top-5。这是检索质量的第二道护城河。

## 一、为什么需要重排

- 向量检索（双塔）是「近似」：query 和 doc 独立编码后算相似度，交互不足。
- Cross-encoder（单塔）把 (query, doc) 拼接输入，token 级交互，精度显著更高。
- 代价：慢（每对一次模型调用），所以只对 Top-20 重排。

## 二、重排模型

- **主选**：`bge-reranker-v2-m3`（BAAI）
  - 多语言、中文强、cross-encoder。
  - API：硅基流动 `/v1/rerank`。
- 备选：`bge-reranker-large`、Cohere rerank（海外）。

## 三、重排流程

```
Top-20 候选(融合后)
  │
  ▼
构造 (query, doc) 对
  doc = 帖子标题 + 正文前 200 字 + 分类/学校
  │
  ▼
调用 rerank API
  POST /v1/rerank
  {model:"BAAI/bge-reranker-v2-m3", query, documents:[...], top_n:20}
  │
  ▼
返回 [{index, relevance_score}]
  │
  ▼
按 relevance_score 重排 → 取 Top-5
```

## 四、重排时机

- **MVP**：可先不做重排（用 RRF 融合结果），先验证端到端。
- **进阶**：开启重排，预期 Top-5 命中率从 75% 提升到 85%+。
- **触发条件**：候选数 > 10 时重排；≤ 10 时 RRF 已够。

## 五、重排降级

- rerank API 失败 → 用 RRF 融合结果（不重排）。
- rerank 超时(>3s) → 用 RRF 结果 + 日志告警。

## 六、重排与业务规则结合

重排分数 + 业务加权：
```
final_score = α * rerank_score + (1-α) * business_score
business_score = f(like_count, comment_count, recency, is_creator_author)
```
- α 默认 0.7（重排为主），可调。
- 业务加权用于打破重排分数接近时的并列，优先热门/新/创作者帖。
- 进阶：个性化（用户偏好分类加分）。

## 七、评估

- 开/关重排 A/B：Top-5 命中率、MRR、用户 👍 率。
- 预期：命中率 +10pp，延迟 +200ms（可接受）。

## 八、决策记录 (ADR)

### ADR-025: 重排放进阶阶段
- **理由**：MVP 用 RRF 已达标 75%+，重排增加 200ms 延迟与成本；进阶再开。
- **风险**：MVP 命中率未达 85% 目标 → 若未达则提前开重排。

### ADR-026: 重排分数 + 业务加权混合
- **理由**：纯重排可能把冷门高质量帖排太低；业务加权保证基本热度。
- **α 调参**：通过 A/B 找最优 α。
