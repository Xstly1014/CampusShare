# 黄金测试集设计

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[retrieval-quality-metrics.md](./retrieval-quality-metrics.md)

## 一、黄金测试集是什么

黄金测试集(Golden Set)是一组**人工标注的 query-期望输出对**,作为 agent 质量回归的「标尺」。

它不是训练集(agent 不做 fine-tune),不是验证集(不用于超参搜索),而是**回归测试集**:每次 agent 变更(换 prompt、换检索策略、换 LLM)后,跑一遍黄金集,对比指标是否退化。

## 二、设计原则

### 原则 1: 小而精,而非大而全
- 目标规模:**200-500 条**(MVP 200 条,稳定后扩充至 500)。
- 宁可 200 条高质量标注,不要 2000 条低质量标注。
- 理由:黄金集每条都需要人工标注相关文档,成本极高;且回归测试每周/每 PR 跑,规模过大耗时长。

### 原则 2: 覆盖意图全集,而非堆叠热门
按 04-intent-understanding 的五大意图分布:

| 意图 | 占比 | 条数(MVP 200) | 说明 |
|------|------|-----------------|------|
| HOW_TO | 30% | 60 | 软件使用教程、帮助中心 |
| SEARCH | 35% | 70 | 资源/讨论帖搜索 |
| NAVIGATE | 15% | 30 | 板块定位 |
| CLARIFY | 10% | 20 | 模糊问题需澄清 |
| OUT_OF_SCOPE | 10% | 20 | 越界问题应拒绝 |

### 原则 3: 难度分层
| 难度 | 占比 | 特征 |
|------|------|------|
| 简单 | 40% | 单轮、单意图、明确关键词 |
| 中等 | 40% | 单轮多关键词、或需要 1 次工具调用 |
| 困难 | 20% | 多轮、指代消解、多步推理、需要澄清 |

### 原则 4: 真实优先
- **70% 来自真实日志**:从 agent_sessions 中抽取真实用户提问(脱敏后)。
- **30% 人工构造**:补充日志中未覆盖的边界 case(如越界问题、极端长输入)。
- 理由:纯人工构造会偏离真实分布,纯日志采样会遗漏边界。

## 三、标注规范

### 3.1 每条样本的字段

```jsonl
{
  "query_id": "q-001",
  "query": "怎么成为创作者",
  "intent": "HOW_TO",
  "difficulty": "easy",
  "input_length": "short",
  "turn": 1,
  "context": [],
  "relevant_docs": [
    {
      "doc_id": "kb-012",
      "doc_type": "knowledge",
      "relevance": 3,
      "note": "创作者认证条件章节,直接回答了获赞和发帖门槛"
    },
    {
      "doc_id": "post-8842",
      "doc_type": "post",
      "relevance": 2,
      "note": "认证流程经验帖,补充了申请步骤"
    }
  ],
  "expected_tool": "search_knowledge",
  "expected_clarify": false,
  "expected_response_keywords": ["总获赞", "10000", "发帖", "50", "申请"],
  "expected_response_must_not": ["不知道", "无法回答", "建议联系客服"],
  "expected_citation_count": ">=1",
  "annotator": "human_a",
  "reviewer": "human_b",
  "iaa_score": 0.85,
  "version": "v1.0",
  "tags": ["creator", "verification"]
}
```

### 3.2 相关度标注标准(relevance 分级)

| 分值 | 定义 | 示例 |
|------|------|------|
| 3 - 完全匹配 | 文档直接、完整地回答了 query | query「怎么成为创作者」→ 创作者认证条件章节 |
| 2 - 高度相关 | 文档包含关键信息,但需与其他文档整合 | query 同上 → 认证流程经验帖 |
| 1 - 部分相关 | 文档提供背景或上下文,但不直接回答 | query 同上 → 平台用户角色体系说明 |
| 0 - 不相关 | 文档与 query 无关 | query 同上 → 如何修改密码教程 |

### 3.3 expected_response_keywords 的使用
- **不是**要求回答必须包含这些词(那是关键词匹配,不是语义评估)。
- **是**用于 LLM-as-Judge 的参考信号:如果回答缺失所有关键词,大概率质量差。
- 关键词数量:3-8 个,选取核心实体/数字/动作。

### 3.4 expected_response_must_not 的使用
- 标注**绝对不应出现**的内容。
- 用于检测幻觉或拒答失当。
- 示例:query「怎么成为创作者」不应回答「我不知道」「建议联系客服」(因为答案在知识库中)。

## 四、构建流程

```
┌─────────────────────────────────────────────────────────┐
│                  黄金测试集构建流程                        │
└─────────────────────────────────────────────────────────┘

 阶段 1: 种子采集 (Week 1)
 ├── 1a. 从 agent_sessions 抽取真实 query (脱敏)
 ├── 1b. 按意图/难度分层抽样
 └── 1c. 人工补充边界 case
       ↓
 阶段 2: LLM 辅助预标注 (Week 1-2)
 ├── 2a. 用 DeepSeek-V3 对每条 query 生成候选相关文档
 ├── 2b. LLM 预判 relevance 分级 (粗标)
 └── 2c. 输出待人工审核的标注草稿
       ↓
 阶段 3: 人工双标 + 仲裁 (Week 2-3)
 ├── 3a. 标注员 A 独立标注 (query → relevant_docs + relevance)
 ├── 3b. 标注员 B 独立标注 (同上)
 ├── 3c. 计算双人一致性 (Cohen's Kappa)
 ├── 3d. 分歧样本进入仲裁 (标注员 C 或专家)
 └── 3e. IAA < 0.7 的样本剔除或重写
       ↓
 阶段 4: 质量校验 (Week 3)
 ├── 4a. 跑一遍当前 agent,检查标注合理性
 ├── 4b. 检查意图/难度分布是否符合原则 2/3
 ├── 4c. 检查 relevant_docs 覆盖率 (每条至少 1 个 relevance>=2)
 └── 4d. 锁定 v1.0 版本
       ↓
 阶段 5: 持续维护 (持续)
 ├── 5a. 每月从新日志补充 10-20 条
 ├── 5b. 发现 bad case 及时补入
 ├── 5c. 季度审查:剔除过时样本 (知识库已变更)
 └── 5d. 版本递增: v1.0 → v1.1 → v2.0
```

### 4.1 LLM 辅助预标注提示词

```
你是检索质量评估助手。给定用户问题和候选文档列表,请为每个文档标注相关度。

用户问题: {query}

候选文档:
[1] {doc_id}: {doc_content}
[2] {doc_id}: {doc_content}
...

请对每个文档输出:
- doc_id
- relevance: 0/1/2/3 (3=完全匹配, 2=高度相关, 1=部分相关, 0=不相关)
- reason: 一句话理由

注意:
- 只标注与问题直接相关的文档,宁缺毋滥
- 如果文档需要与其他文档组合才能回答,relevance 最高给 2
- 完全不相关的给 0,不要凑数
```

### 4.2 双标一致性计算(Cohen's Kappa)

```
Kappa = (Po - Pe) / (1 - Pe)

Po = 两人一致的比例
Pe = 随机一致的概率 (基于边际分布计算)
```

- Kappa ≥ 0.8:几乎完全一致
- 0.6 ≤ Kappa < 0.8: substantial 一致(可接受)
- 0.4 ≤ Kappa < 0.6: moderate 一致(需讨论标准)
- Kappa < 0.4:一致性差(标注标准有问题,需重新定义)

**仲裁规则**:Kappa < 0.6 的样本进入仲裁;仲裁结果作为 ground truth,并用于校准标注标准。

## 五、版本管理

### 5.1 版本号规则
- **主版本号**(v1 → v2):标注标准变更(如 relevance 分级重新定义)、大规模重标。
- **次版本号**(v1.0 → v1.1):新增/删除样本,标注标准不变。

### 5.2 版本存储
```
golden_set/
├── v1.0.jsonl          # 200 条,初始版本
├── v1.1.jsonl          # 220 条,补充 20 条
├── v2.0.jsonl          # 250 条,relevance 标准调整
├── changelog.md        # 每版本变更记录
└── archived/
    └── v0.9-draft.jsonl  # 草稿归档
```

### 5.3 变更记录格式(changelog.md)

```markdown
## v1.1 (2026-07-15)

### 新增
- q-201: "考研数学有什么好的复习资料" (SEARCH, medium)
- q-202: "怎么关闭点赞通知" (HOW_TO, easy)
- ... (共 20 条)

### 修改
- q-045: 补充 relevant_docs (原标注遗漏 post-9921)

### 删除
- q-012: 知识库已删除该文档,样本失效

### 理由
- 补充 SEARCH 意图样本,当前占比偏低 (30% → 35%)
```

## 六、维护与更新策略

### 6.1 定期补充(月度)
- 从 agent_sessions 抽取近 30 天新 query。
- 聚类去重,选取与现有黄金集差异最大的 10-20 条。
- 走阶段 2-4 流程标注。

### 6.2 Bad Case 驱动补充
- 用户反馈 👎 的 query,经分析确认是 agent 质量问题(非用户误操作)。
- 将该 query + 正确标注补入黄金集。
- 理由:用户反馈是最高质量的负样本来源。

### 6.3 过时样本清理(季度)
- 知识库文档删除/更新后,引用该文档的黄金集样本可能失效。
- 每季度扫描黄金集,检查 `relevant_docs` 中的 doc_id 是否仍存在。
- 失效样本:更新 doc_id 或删除。

### 6.4 防过拟合
- **风险**:agent 开发者可能针对黄金集优化,导致「黄金集分数高但真实体验没提升」。
- **应对**:
  - 黄金集只用于回归门禁(防退化),不用于选优。
  - 真实质量看在线 A/B 和用户满意度。
  - 黄金集定期换血(每季度 20% 样本轮换)。

## 七、黄金集存储表(agent_golden_set)

```sql
CREATE TABLE agent_golden_set (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_id VARCHAR(32) NOT NULL UNIQUE COMMENT '如 q-001',
    query TEXT NOT NULL,
    intent ENUM('HOW_TO','SEARCH','NAVIGATE','CLARIFY','OUT_OF_SCOPE') NOT NULL,
    difficulty ENUM('easy','medium','hard') NOT NULL,
    input_length ENUM('short','medium','long') NOT NULL,
    turn INT DEFAULT 1 COMMENT '第几轮 (多轮测试)',
    context_json JSON COMMENT '上文上下文 (多轮)',

    relevant_docs_json JSON NOT NULL COMMENT '[{doc_id, doc_type, relevance, note}]',
    expected_tool VARCHAR(64),
    expected_clarify BOOLEAN DEFAULT FALSE,
    expected_keywords_json JSON COMMENT '["关键词1", "关键词2"]',
    expected_must_not_json JSON,
    expected_citation_count VARCHAR(16) COMMENT '如 >=1',

    annotator VARCHAR(32) NOT NULL,
    reviewer VARCHAR(32),
    iaa_score DECIMAL(4,3),

    version VARCHAR(16) NOT NULL COMMENT '如 v1.0',
    tags_json JSON COMMENT '["creator", "verification"]',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_version (version),
    INDEX idx_intent_difficulty (intent, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 黄金测试集';
```

## 八、决策记录

### ADR-171: 黄金测试集设计规范
- **背景**:黄金集是回归测试的标尺,质量直接影响评估可信度。
- **决策**:
  - 规模 200-500 条,小而精。
  - 意图分布:HOW_TO 30% / SEARCH 35% / NAVIGATE 15% / CLARIFY 10% / OUT_OF_SCOPE 10%。
  - 难度分布:简单 40% / 中等 40% / 困难 20%。
  - 70% 真实日志 + 30% 人工构造。
  - 双人独立标注 + 仲裁,Cohen's Kappa ≥ 0.6 方可入集。
  - 月度补充 10-20 条,季度轮换 20%。
- **理由**:小规模高质量比大规模低质量更有价值;双标+仲裁控制标注噪声;定期轮换防过拟合。
- **权衡**:双标成本高,但单标噪声大不可靠。
- **状态**:采纳
