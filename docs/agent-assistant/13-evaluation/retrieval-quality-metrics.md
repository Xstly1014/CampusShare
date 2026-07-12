# 检索质量指标体系

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [06-retrieval/](../06-retrieval/)、[golden-test-set.md](./golden-test-set.md)

## 一、为什么需要这套指标

Agent 的检索层是「召回 + 精排 + 重排」三段式(见 06-retrieval)。任何一段的改动(换 embedding 模型、调 RRF 权重、换 reranker)都可能影响最终召回质量。

没有量化指标,优化就是瞎调。本文件定义一套贯穿召回 → 精排 → 重排的指标体系,使每一次检索变更都可量化、可对比、可回归。

## 二、指标全景

```
┌──────────────┬─────────────────────────┬──────────────────────┐
│   检索阶段    │       核心指标           │       衡量什么        │
├──────────────┼─────────────────────────┼──────────────────────┤
│  召回(向量+关键词)│ Recall@K              │ 相关文档有没有被捞回来 │
│              │ Hit Rate@K              │ 至少捞到一个相关的概率 │
├──────────────┼─────────────────────────┼──────────────────────┤
│  精排(RRF 融合)│ Precision@K            │ 捞回来的有多少相关     │
│              │ MRR                     │ 第一个相关的排第几     │
├──────────────┼─────────────────────────┼──────────────────────┤
│  重排(cross-encoder)│ NDCG@10            │ 综合排序质量           │
│              │ MAP                     │ 所有相关文档的排序均值  │
├──────────────┼─────────────────────────┼──────────────────────┤
│  生成下游     │ Citation Accuracy       │ 引用的文档是否真的相关  │
│              │ Groundedness            │ 回答是否只基于召回内容  │
├──────────────┼─────────────────────────┼──────────────────────┤
│  系统健康     │ Coverage                │ 知识库被引用的覆盖率    │
│              │ Diversity               │ 结果多样性(避免同质)   │
│              │ Latency P95             │ 检索延迟               │
└──────────────┴─────────────────────────┴──────────────────────┘
```

## 三、基础指标定义

### 3.1 Recall@K(召回率)

> 在所有相关文档中,前 K 个结果里捞回了多少比例。

```
Recall@K = |relevant ∩ retrieved_topK| / |relevant|
```

- **K 的选择**:MVP 阶段 K=10(重排输入上限),进阶阶段补充 K=20、K=50 评估召回上限。
- **相关文档集 `|relevant|` 的来源**:黄金测试集人工标注(见 golden-test-set.md),每条 query 标注 3-10 个相关文档 ID。
- **目标**:Recall@10 ≥ 0.85(即 85% 的相关文档能进入重排候选)。
- **退化阈值**:相比基线下降 >3% 触发告警,下降 >5% 阻断合并。

### 3.2 Precision@K(精确率)

> 前 K 个结果中,有多少比例是相关的。

```
Precision@K = |relevant ∩ retrieved_topK| / K
```

- **K 的选择**:K=3(用户首屏可见引用数)、K=5(引用卡片展示上限)。
- **目标**:Precision@3 ≥ 0.70,Precision@5 ≥ 0.60。
- **意义**:Precision 低意味着重排后仍有噪声,会污染 LLM 生成。

### 3.3 MRR(Mean Reciprocal Rank,平均倒数排名)

> 第一个相关文档排在第几位,取倒数,再对所有 query 求平均。

```
RR = 1 / rank_of_first_relevant
MRR = mean(RR) over all queries
```

- **适用场景**:用户提问往往只关心第一个正确答案(如「怎么成为创作者」)。
- **目标**:MRR ≥ 0.65(即第一个相关结果平均排在 top-2 以内)。
- **特点**:对「第一个相关结果位置」敏感,但对后续相关结果位置不敏感。

### 3.4 NDCG@K(Normalized Discounted Cumulative Gain)

> 综合考虑相关度分级和排序位置的指标,是检索质量评估的「金标准」。

```
DCG@K = Σ_{i=1}^{K} rel_i / log2(i + 1)
NDCG@K = DCG@K / IDCG@K   (IDCG 为理想排序的 DCG)
```

- **相关度分级**:`rel_i` 不是 0/1,而是分级:
  - `3` = 完全匹配(直接回答了问题)
  - `2` = 高度相关(包含关键信息但需整合)
  - `1` = 部分相关(提供背景但不够直接)
  - `0` = 不相关
- **K 的选择**:K=10(重排输出)。
- **目标**:NDCG@10 ≥ 0.75。
- **为何用 NDCG 而非 MAP**:NDCG 支持分级相关度,更贴合 Agent 检索场景(文档不是非黑即白)。

### 3.5 MAP(Mean Average Precision)

> 所有相关文档位置的精确率均值,再对所有 query 求平均。

```
AP = (1/|relevant|) × Σ_{k=1}^{n} Precision@k × rel(k)
MAP = mean(AP) over all queries
```

- **适用场景**:需要所有相关文档都排靠前(如「搜索所有关于线性代数的资源贴」)。
- **目标**:MAP ≥ 0.55。
- **与 NDCG 的区别**:MAP 只考虑二值相关,且对所有相关位置同等加权;NDCG 对分级相关度和位置衰减更敏感。

### 3.6 Hit Rate@K(命中率)

> 前 K 个结果中至少有一个相关的 query 占比。

```
Hit Rate@K = |queries_with_at_least_one_relevant_in_topK| / |total_queries|
```

- **K 的选择**:K=1(最严格)、K=3(实际可用)。
- **目标**:Hit Rate@3 ≥ 0.90。
- **意义**:Hit Rate@3 = 0.90 意味着 90% 的提问在前 3 条结果里至少有一个可用答案。

## 四、下游生成质量指标

检索的最终目标是服务生成,因此需要评估「检索结果对生成的贡献」。

### 4.1 Citation Accuracy(引用准确率)

> 回答中引用的文档 `[n]` 是否真的支持该论断。

```
Citation Accuracy = correctly_grounded_citations / total_citations
```

- **评估方式**:LLM-as-Judge 逐条校验引用(见 llm-as-judge.md)。
- **目标**:≥ 0.90。
- **退化影响**:引用错误会误导用户点击不相关文档,损害信任。

### 4.2 Groundedness(接地度)

> 回答是否只基于召回内容,而非 LLM 幻觉。

```
Groundedness = grounded_claims / total_claims
```

- **评估方式**:将回答拆分为原子论断,逐条判断是否可在召回文档中找到支撑。
- **目标**:≥ 0.85。
- **与引用准确率的区别**:Groundedness 关注「有没有编造」,Citation Accuracy 关注「引用对不对」。

### 4.3 Context Utilization(上下文利用率)

> 召回的文档中有多少被实际引用。

```
Context Utilization = cited_docs / retrieved_docs
```

- **目标**:0.40 - 0.80。
- **过低(<0.40)**:召回噪声多,精确率不足。
- **过高(>0.80)**:可能召回不足,遗漏了更好的证据(Recall 低)。

## 五、系统健康指标

### 5.1 Coverage(知识覆盖率)

> 知识库中被至少一次检索命中的文档占比。

```
Coverage = retrieved_docs_at_least_once / total_knowledge_docs
```

- **观测周期**:周。
- **目标**:**没有硬性目标,但需监控长尾**。
- **长尾问题**:如果 Coverage < 30%,说明大量知识文档从未被召回,可能是 embedding 索引问题或知识分块不合理。
- **行动**:Coverage 过低时,抽样检查未被命中的文档,分析原因(分块过大?标题不匹配?embedding 质量差?)。

### 5.2 Diversity(结果多样性)

> 同一 query 的 top-K 结果是否过度同质。

```
Diversity = 1 - avg_pairwise_similarity(topK_docs)
```

- **计算**:对 top-K 文档的 embedding 两两计算余弦相似度,取平均,用 1 减去。
- **目标**:Diversity ≥ 0.30。
- **过低的影响**:用户问「考研数学资料」,返回 5 条都是同一个帖子的不同分块,体验差。
- **改进手段**:MMR(Maximal Marginal Relevance)重排,在相关性和多样性间平衡。

### 5.3 Latency P95(检索延迟)

> 单次检索(召回+精排+重排)的 P95 延迟。

- **目标**:P95 ≤ 300ms。
- **分解预算**:
  - 向量检索:≤ 80ms(HNSW ef_search=128)
  - 关键词检索:≤ 50ms(pg_trgm)
  - RRF 融合:≤ 10ms
  - Reranker:≤ 150ms(bge-reranker-v2-m3,batch=10)
- **退化阈值**:P95 > 400ms 告警,> 600ms 降级(跳过 reranker)。

## 六、评估执行流程

```
┌──────────────────────────────────────────────────────────┐
│                    检索质量评估流水线                       │
└──────────────────────────────────────────────────────────┘

 1. 加载黄金测试集 (golden_set.jsonl)
    ↓
 2. 对每条 query 执行检索流水线 (召回 → 精排 → 重排)
    ↓
 3. 对比检索结果与人工标注的相关文档
    ↓
 4. 计算 Recall@K / Precision@K / MRR / NDCG@K / MAP / Hit Rate
    ↓
 5. [可选] 将检索结果送入 LLM 生成回答
    ↓
 6. LLM-as-Judge 评估 Citation Accuracy / Groundedness
    ↓
 7. 按意图/难度/长度切片统计
    ↓
 8. 写入 agent_eval_results 表
    ↓
 9. 对比基线,生成回归报告
    ↓
10. 退化 > 阈值 → 阻断 CI / 告警
```

### 6.1 评估脚本结构

```
agent-eval/
├── golden_set/
│   ├── v1.0.jsonl          # 黄金集 v1.0
│   └── v1.1.jsonl          # 增量版本
├── scripts/
│   ├── run_retrieval_eval.py    # 检索指标计算
│   ├── run_generation_eval.py   # 生成指标计算 (LLM-as-Judge)
│   └── compare_baseline.py      # 基线对比
├── config/
│   ├── eval_config.yml          # K 值、阈值、切片维度
│   └── judge_prompts/           # LLM-as-Judge 提示词
└── reports/
    └── 2026-06-30_v1.1.html     # HTML 报告
```

### 6.2 黄金集数据格式

```jsonl
{
  "query_id": "q-001",
  "query": "怎么成为创作者",
  "intent": "HOW_TO",
  "difficulty": "easy",
  "relevant_docs": [
    {"doc_id": "kb-012", "relevance": 3, "note": "创作者认证条件章节"},
    {"doc_id": "post-8842", "relevance": 2, "note": "认证流程帖"}
  ],
  "expected_tool": "search_knowledge",
  "expected_response_keywords": ["总获赞", "10000", "发帖", "50", "申请"]
}
```

### 6.3 评估结果表(agent_eval_results)

```sql
CREATE TABLE agent_eval_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    eval_run_id VARCHAR(64) NOT NULL COMMENT '评估批次 ID (UUID)',
    eval_type ENUM('RETRIEVAL', 'GENERATION', 'E2E') NOT NULL,
    golden_set_version VARCHAR(16) NOT NULL,
    agent_version VARCHAR(32) NOT NULL COMMENT '被评估的 agent 版本',

    -- 整体指标
    metric_name VARCHAR(64) NOT NULL COMMENT '如 recall@10, ndcg@10',
    metric_value DECIMAL(8,4) NOT NULL,
    baseline_value DECIMAL(8,4) COMMENT '基线值',
    delta_pct DECIMAL(8,4) COMMENT '相比基线变化百分比',

    -- 切片维度
    slice_dimension VARCHAR(32) COMMENT 'intent/difficulty/length/...',
    slice_value VARCHAR(32),

    -- 元信息
    sample_count INT NOT NULL,
    eval_started_at DATETIME NOT NULL,
    eval_finished_at DATETIME NOT NULL,
    report_url VARCHAR(256) COMMENT 'HTML 报告地址',

    INDEX idx_run (eval_run_id),
    INDEX idx_version_metric (agent_version, metric_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 评估结果';
```

## 七、回归门禁规则

CI/CD 中,黄金集回归评估触发以下门禁:

| 指标 | 基线 | 告警线 | 阻断线 |
|------|------|--------|--------|
| Recall@10 | 0.85 | 下降 >3% | 下降 >5% |
| NDCG@10 | 0.75 | 下降 >3% | 下降 >5% |
| MRR | 0.65 | 下降 >3% | 下降 >5% |
| Hit Rate@3 | 0.90 | 下降 >2% | 下降 >4% |
| Citation Accuracy | 0.90 | 下降 >2% | 下降 >4% |
| Groundedness | 0.85 | 下降 >3% | 下降 >5% |

**切片门禁**:任一切片(intent/难度)下降 >10% 即使整体达标也告警,提示局部退化。

## 八、指标局限性与注意事项

### 8.1 Recall 的标注成本
Recall 需要知道「所有相关文档」,但知识库可能有数千文档,人工无法穷举标注。**应对**:只标注「应该出现在 top-10」的文档,而非全集;Recall@10 实际是「目标文档命中率」。

### 8.2 NDCG 的相关度主观性
`rel_i` 的 1/2/3 分级依赖标注者判断。**应对**:双人标注 + 仲裁,计算 inter-annotator agreement(IAA),IAA < 0.7 的样本剔除。

### 8.3 离线指标 ≠ 用户体验
Recall 高不代表用户满意(可能检索对了但回答差)。**应对**:离线指标只做回归门禁,真实质量看在线满意度信号(见 user-satisfaction-metrics.md)。

### 8.4 Reranker 评估的独立性
Reranker 改动只影响 NDCG@10(重排输出),不影响 Recall@50(召回输出)。**应对**:分阶段评估,召回阶段用 Recall@50,重排阶段用 NDCG@10,避免混淆。

## 九、决策记录

### ADR-170: 检索质量指标体系选型
- **背景**:检索指标众多(Recall/Precision/MRR/NDCG/MAP/Hit Rate),需要选定核心集避免评估过载。
- **决策**:
  - 召回阶段核心指标:**Recall@10**(召回上限是否够)。
  - 重排阶段核心指标:**NDCG@10**(排序质量金标准)。
  - 体验指标:**Hit Rate@3**(用户首屏命中率)。
  - 生成指标:**Citation Accuracy + Groundedness**(检索对生成的贡献)。
  - 辅助指标:Precision@K、MRR、MAP、Coverage、Diversity 用于诊断而非门禁。
- **理由**:NDCG 支持分级相关度比 MAP 更贴合场景;Recall@10 而非 @50 因为重排输入上限就是 10。
- **状态**:采纳
