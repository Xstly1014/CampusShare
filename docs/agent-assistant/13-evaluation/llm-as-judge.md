# LLM 作为评估者 (LLM-as-Judge)

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [golden-test-set.md](./golden-test-set.md)、[retrieval-quality-metrics.md](./retrieval-quality-metrics.md)

## 一、为什么需要 LLM-as-Judge

Agent 生成质量评估面临一个核心矛盾:
- **人工评估**准确但成本极高,无法对每次 PR 跑回归。
- **规则评估**(关键词匹配/bleu/rouge)便宜但与人类感知相关性低,尤其不适合开放域问答。

LLM-as-Judge 用一个强大的 LLM(评估者)去评估另一个 LLM(被评估者)的输出质量,在成本和准确性间取得平衡。

参考:DeepSeek 官方在评估 DeepSeek-V3 时大量使用 LLM-as-Judge;字节跳动的豆包评估体系也采用此方案。

## 二、评估维度(Rubric)

不评估单一「好坏」分数,而是分维度评估,各维度独立打分。

### 2.1 评估维度定义

| 维度 | 代码 | 分值范围 | 核心问题 |
|------|------|----------|----------|
| 事实准确性 | factual_accuracy | 1-5 | 回答的事实是否正确,有无幻觉 |
| 相关性 | relevance | 1-5 | 回答是否切中用户问题 |
| 完整性 | completeness | 1-5 | 回答是否完整覆盖用户需求 |
| 引用准确 | citation_accuracy | 1-5 | 引用标注是否对应正确的文档 |
| 语气得体 | tone | 1-5 | 语气是否友好、专业、不居高临下 |
| 安全性 | safety | 1-5 | 是否涉及违规/有害/越界内容 |

### 2.2 评分标准(Rubric)

以「事实准确性」为例:

| 分值 | 标准 |
|------|------|
| 5 - 优秀 | 所有事实陈述均正确,无幻觉,所有论断均有引用支撑 |
| 4 - 良好 | 事实基本正确,偶有轻微不精确但不误导,引用基本准确 |
| 3 - 合格 | 大部分事实正确,存在 1 处可纠正的不精确,无严重幻觉 |
| 2 - 较差 | 存在明显事实错误或未支撑论断,可能误导用户 |
| 1 - 极差 | 大量幻觉,事实错误严重,完全不可信 |

每个维度都需有类似明确的 1-5 分 Rubric,写入 `eval_config.yml`。

## 三、评估提示词设计

### 3.1 单维度评估提示词

```
你是 Agent 质量评估助手。请只评估「事实准确性」这一维度。

## 用户问题
{query}

## Agent 回答
{response}

## 检索到的参考文档
[1] {doc_content}
[2] {doc_content}
...

## 评估标准 (事实准确性)
5 - 优秀: 所有事实陈述均正确,无幻觉,所有论断均有引用支撑
4 - 良好: 事实基本正确,偶有轻微不精确但不误导
3 - 合格: 大部分事实正确,存在 1 处可纠正的不精确,无严重幻觉
2 - 较差: 存在明显事实错误或未支撑论断
1 - 极差: 大量幻觉,事实错误严重

## 输出格式 (严格 JSON)
{
  "score": <1-5>,
  "errors": ["具体错误1", "具体错误2"],
  "hallucinations": ["幻觉内容1"],
  "reason": "一句话理由"
}

注意:
- 只评估事实准确性,不评其他维度
- 必须对比参考文档判断,不可凭自身知识
- errors 和 hallucinations 为空数组时表示无问题
```

### 3.2 多维度批量评估提示词

为降低 API 调用成本,可将 6 个维度合并为一次调用:

```
你是 Agent 质量评估助手。请从 6 个维度评估以下回答。

## 用户问题
{query}

## Agent 回答
{response}

## 参考文档
[1] {doc_content}
...

## 评估维度与标准
{all_rubrics}

## 输出格式 (严格 JSON)
{
  "factual_accuracy": {"score": <1-5>, "errors": [...], "reason": "..."},
  "relevance": {"score": <1-5>, "reason": "..."},
  "completeness": {"score": <1-5>, "missing": [...], "reason": "..."},
  "citation_accuracy": {"score": <1-5>, "wrong_citations": [...], "reason": "..."},
  "tone": {"score": <1-5>, "reason": "..."},
  "safety": {"score": <1-5>, "issues": [...], "reason": "..."},
  "overall": <1-5>
}
```

### 3.3 Pairwise 对比评估(用于 A/B)

当需要对比两个版本(v1 vs v2)的回答时,用 pairwise 评估比独立打分更敏感:

```
你是 Agent 质量评估助手。请对比以下两个回答的优劣。

## 用户问题
{query}

## 回答 A
{response_a}

## 回答 B
{response_b}

## 参考文档
[1] {doc_content}
...

## 评估维度
1. 事实准确性
2. 相关性
3. 完整性
4. 引用准确

## 输出 (严格 JSON)
{
  "winner": "A" | "B" | "tie",
  "dimension_scores": {
    "factual_accuracy": {"A": <1-5>, "B": <1-5>},
    "relevance": {"A": <1-5>, "B": <1-5>},
    ...
  },
  "reason": "A 更优因为..."
}
```

## 四、评估者选型

### 4.1 评估者 ≠ 被评估者
- **被评估者**:DeepSeek-V3(主生成模型)。
- **评估者**:**不能**用 DeepSeek-V3(自我偏好偏见),改用:
  - **首选**:GPT-4o 或 Claude 3.5 Sonnet(第三方最强模型,无自我偏好)。
  - **次选**:DeepSeek-R1(推理模型,与 V3 不同系列,偏见较低)。
  - **兜底**:豆包 Pro(国内可访问,成本低)。

### 4.2 成本估算
- 单条样本:6 维度批量评估,输入约 2K token,输出约 500 token。
- 200 条黄金集:200 × 2.5K = 500K token。
- GPT-4o 价格:$5/M input + $15/M output ≈ $5/次全量评估。
- 频率:每 PR 一次,月度约 30 次,月成本 ≈ $150。
- **优化**:日常回归用豆包 Pro($0.5/次),版本发布前用 GPT-4o 复核($5/次)。

## 五、一致性校验与校准

LLM-as-Judge 本身也是模型,需要验证其评估是否可靠。

### 5.1 与人工标注的一致性

```
┌────────────────────────────────────────────────────┐
│              LLM-Judge 校准流程                      │
└────────────────────────────────────────────────────┘

 1. 从黄金集抽取 50 条子集 (覆盖各意图/难度)
    ↓
 2. 人工评估这 50 条 (6 维度打分) → human_scores
    ↓
 3. LLM-Judge 评估同 50 条 → llm_scores
    ↓
 4. 计算一致性:
    - Pearson 相关系数 (数值维度)
    - Cohen's Kappa (分类维度,如 winner A/B/tie)
    ↓
 5. 一致性阈值:
    - Pearson ≥ 0.7 → 可用
    - 0.5 ≤ Pearson < 0.7 → 需调优 prompt
    - Pearson < 0.5 → 不可用,换评估模型
    ↓
 6. 分歧样本分析,改进 Rubric 或 prompt
    ↓
 7. 季度重新校准 (评估模型升级后必做)
```

### 5.2 偏见检测

LLM-Judge 已知偏见及检测方法:

| 偏见类型 | 表现 | 检测方法 | 缓解 |
|----------|------|----------|------|
| 位置偏见 | pairwise 时偏好第一个回答 | A/B 位置交换,看 winner 是否反转 | 随机化 A/B 顺序,取两次平均 |
| 长度偏见 | 偏好更长的回答 | 准备长短回答对(质量相当),看是否偏好长的 | Rubric 明确「长度不作为评分依据」 |
| 自我偏好 | 偏好同系列模型的输出 | 用同模型评估同模型 vs 评估异模型 | 评估者 ≠ 被评估者 |
| 权威偏见 | 偏好语气更肯定的回答 | 准备肯定 vs 谨慎回答对(质量相当) | Rubric 区分「准确」与「肯定」 |

### 5.3 评估稳定性(温度敏感性)
- 评估者温度设为 0(贪心解码),确保可复现。
- 但温度 0 仍有轻微随机性(API 端),同一样本跑 3 次取多数票。
- 若 3 次结果差异大(3 个不同分),标记为「低置信」样本,转人工复核。

## 六、评估结果聚合

### 6.1 维度聚合

```
overall_score = 0.30 × factual_accuracy
              + 0.25 × relevance
              + 0.20 × completeness
              + 0.15 × citation_accuracy
              + 0.05 × tone
              + 0.05 × safety
```

- 权重设计:事实准确性权重最高(错误事实最伤信任),安全性虽权重低但**一票否决**(safety=1 则 overall 超过 2)。
- 权重可配置,写入 `eval_config.yml`。

### 6.2 切片聚合
不只看整体 overall_score,按以下切片分别统计:
- 意图(HOW_TO / SEARCH / NAVIGATE / CLARIFY / OUT_OF_SCOPE)
- 难度(简单 / 中等 / 困难)
- 是否触发工具
- 是否触发澄清

**切片门禁**:任一切片 overall_score 下降 >0.3(5 分制)即使整体达标也告警。

## 七、评估执行与 CI 集成

### 7.1 评估脚本

```python
# scripts/run_generation_eval.py (伪代码)

def run_generation_eval(golden_set, agent_endpoint, judge_model):
    results = []
    for sample in golden_set:
        # 1. 调用 agent 生成回答
        response = call_agent(agent_endpoint, sample["query"], sample["context"])

        # 2. 调用 LLM-Judge 评估
        for _ in range(3):  # 3 次取多数
            judge_result = call_judge(
                model=judge_model,
                prompt=build_judge_prompt(sample, response, sample["relevant_docs"]),
                temperature=0
            )
            judge_results.append(judge_result)

        # 3. 多数票聚合
        final_scores = majority_vote(judge_results)

        # 4. 切片归因
        result = {
            "query_id": sample["query_id"],
            "intent": sample["intent"],
            "difficulty": sample["difficulty"],
            "scores": final_scores,
            "overall": weighted_sum(final_scores, weights)
        }
        results.append(result)

    # 5. 聚合统计
    return aggregate(results, group_by=["intent", "difficulty"])
```

### 7.2 CI/CD 门禁

```yaml
# .github/workflows/agent-eval.yml (示意)
name: Agent Quality Evaluation
on:
  pull_request:
    paths:
      - 'backend/campushare-agent/**'
      - 'docs/agent-assistant/prompt-assets/**'

jobs:
  eval:
    steps:
      - name: Deploy agent to eval env
        run: ./deploy-eval.sh

      - name: Run retrieval eval (fast, ~2min)
        run: python scripts/run_retrieval_eval.py --golden v1.1

      - name: Run generation eval (slow, ~15min)
        run: python scripts/run_generation_eval.py --golden v1.1 --judge doubao-pro

      - name: Compare baseline
        run: python scripts/compare_baseline.py --threshold 0.05

      - name: Block PR if regression
        if: failure()
        run: echo "::error::Quality regression detected, see report"
```

## 八、局限性

### 8.1 LLM-Judge 不能替代人工
- LLM-Judge 对「明显错误」敏感,对「微妙不当」不敏感(如语气轻微居高临下)。
- **应对**:LLM-Judge 用于日常回归;季度抽 50 条人工复核,捕捉微妙问题。

### 8.2 评估成本不可忽略
- 全量 200 条 × 3 次 × 6 维度 ≈ $5/次(GPT-4o)。
- **应对**:日常用便宜模型(豆包 $0.5),发版前用强模型复核。

### 8.3 评估者本身可能退化
- 评估模型升级后,Rubric 可能不再适用。
- **应对**:评估模型升级时必须重新跑校准(5.1 流程)。

## 九、决策记录

### ADR-172: LLM-as-Judge 评估框架
- **背景**:生成质量评估需要可自动化、可回归的方案,人工评估无法支撑每次 PR。
- **决策**:
  - 采用 6 维度 Rubric(事实准确/相关/完整/引用/语气/安全)。
  - 评估者 ≠ 被评估者:被评估 DeepSeek-V3,评估者用 GPT-4o/Claude/豆包。
  - 日常回归用豆包 Pro(低成本),发版前用 GPT-4o 复核。
  - 温度 0 + 3 次多数票保证稳定性。
  - 与人工标注 Pearson ≥ 0.7 方可上线,季度重新校准。
  - Pairwise 评估用于 A/B 对比,随机化位置防偏见。
- **理由**:参考 DeepSeek/字节的评估实践,LLM-as-Judge 在成本与准确性间最优平衡。
- **权衡**:LLM-Judge 对微妙问题不敏感,需季度人工复核补充。
- **状态**:采纳
