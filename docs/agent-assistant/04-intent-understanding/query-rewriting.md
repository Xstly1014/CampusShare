# 查询改写

> 状态: 草稿  
> 最后更新: 2026-06-30

查询改写（Query Rewriting）是把用户原始 query 转成更适合检索的形态，是检索质量的第一道关。

## 一、改写子任务

### 1.1 规范化
- 全角→半角、繁→简、去多余空格、标点统一。
- 大小写：英文缩写统一大写（os→OS、kpop→K-POP）。

### 1.2 同义词/缩写扩展
- 维护领域同义词表（见下），query 命中后扩展为 OR 查询。
- 例：OS → 操作系统；计网 → 计算机网络；高数 → 高等数学；KPOP/K-POP → 韩流/韩国流行音乐。

### 1.3 HyDE（Hypothetical Document Embeddings）
- 进阶：让 LLM 先生成一段「假设的理想答案文档」，用该文档做向量检索（而非原 query）。
- 适用：用户 query 短且模糊（如「求 OS 资料」），生成假设文档「这是一份清华大学操作系统期末复习资料，包含进程管理、内存管理、文件系统...」后检索更准。
- 成本：多一次 LLM 调用，仅对 SEARCH 类、query < 15 字时启用。

### 1.4 指代消解（多轮）
- CLARIFY 意图下，把「那个」「它」「上面那个」解析为上文具体实体。
- 例：T1 返回帖子 A/B/C，T2「那个有下载的」→ 改写为「在 [A,B,C] 中筛选 file_url 非空」。

### 1.5 结构化槽位抽取
- 从 query 抽取检索过滤槽：
  - `school`：清华/北大/复旦/...（映射到 school_id）
  - `category`：音乐/游戏/面经/...（映射到 category_id）
  - `post_type`：资源/讨论
  - `sort`：最新/最热
- 例：「北大 计算机网络 期末 卷子」→ {school:北大, query:"计算机网络 期末 卷子"}。

## 二、同义词表（初始，可扩展）

```json
{
  "OS": ["操作系统"],
  "计网": ["计算机网络"],
  "计组": ["计算机组成原理"],
  "高数": ["高等数学"],
  "线代": ["线性代数"],
  "概率": ["概率论与数理统计"],
  "KPOP": ["K-POP", "韩流", "韩国流行音乐"],
  "秋招": ["秋季招聘"],
  "春招": ["春季招聘"],
  "面经": ["面试经验"],
  "复习资料": ["复习", "笔记", "重点"],
  "期末": ["期末考试", "期末卷"]
}
```

> 该表存 Redis / 配置表，后台可维护，无需重启。

## 三、改写流程

```
raw_query
  │
  ├─ 规范化(规则)
  ├─ 指代消解(若 CLARIFY，用上下文)
  ├─ 同义词扩展(查表)
  ├─ 槽位抽取(LLM 或规则)
  ├─ HyDE(进阶，条件触发)
  ▼
rewritten_query + slots + hyde_doc(可选)
```

## 四、与意图分类合并

为省一次 LLM 调用，**意图分类与查询改写合并为一次 prompt**（见 `08-prompt-engineering/system-prompts.md`）：
- 输入：raw_query + 上下文摘要
- 输出 JSON：
```json
{
  "intent": "SEARCH",
  "sub_intent": "resource",
  "confidence": 0.9,
  "rewritten_query": "操作系统 期末 复习 资料",
  "slots": {"school": "清华", "post_type": "resource"},
  "hyde_doc": null
}
```

## 五、改写质量评估

- 黄金集每条标 `expected_rewritten_query` + `expected_slots`。
- 指标：改写后检索 Recall@20 提升、槽位抽取 accuracy。
- A/B：开/关 HyDE 对比 Top-5 命中率。

## 六、决策记录 (ADR)

### ADR-011: 意图分类与查询改写合并为一次 LLM 调用
- **理由**：省 ~500ms 延迟和一次调用成本；两者输入相同、输出可结构化合并。
- **风险**：单 prompt 任务多可能降低单任务质量 → 用清晰 JSON schema + Few-shot 缓解。

### ADR-012: HyDE 条件触发
- **理由**：HyDE 对短模糊 query 增益大，但对长明确 query 无益且费时。
- **触发**：SEARCH 类 + query 长度 < 15 字 + 不含明确实体。
