# 输出护栏 (Output Guardrails)

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[input-guardrails.md](./input-guardrails.md)、[content-safety.md](./content-safety.md)

## 一、输出护栏的职责

LLM 生成回答后、返回给用户前,必须经过输出护栏(L5)检查。输出护栏负责:
1. **有害内容过滤**:拦截暴力/色情/违法/歧视性内容。
2. **幻觉检测**:检测回答中无引用支撑的论断。
3. **引用验证**:验证回答中的 `[n]` 引用是否对应正确的检索文档。
4. **PII 泄露检测**:防止 agent 在回答中泄露其他用户的 PII。
5. **格式校验**:确保输出符合前端可渲染的格式。

## 二、输出护栏执行流程

```
LLM 生成回答 (streaming)
         │
         ▼
┌────────────────────────────┐
│  L5a 有害内容分类 (~200ms)  │  小模型分类是否含有害内容
└──────────┬─────────────────┘
           │ 安全
           ▼
┌────────────────────────────┐
│  L5b 幻觉检测 (~500ms)     │  对比回答与检索文档
└──────────┬─────────────────┘
           │ 通过
           ▼
┌────────────────────────────┐
│  L5c 引用验证 (~100ms)     │  校验 [n] 引用映射
└──────────┬─────────────────┘
           │ 通过
           ▼
┌────────────────────────────┐
│  L5d PII 泄露检测 (~50ms)  │  扫描回答中的 PII
└──────────┬─────────────────┘
           │ 通过
           ▼
┌────────────────────────────┐
│  L5e 格式校验 (~10ms)      │  markdown 格式检查
└──────────┬─────────────────┘
           │
           ▼
      返回用户
```

**流式输出特殊处理**:L5a-L5d 无法对完整回答流式检查(需完整文本)。策略:
- **方案 A(安全优先)**:LLM 先完整生成,护栏检查通过后再流式输出给用户(增加首 token 延迟)。
- **方案 B(体验优先)**:边生成边输出,护栏在生成完成后异步检查,若有害则中断流并发送替代文本。
- **决策**:**MVP 用方案 A**(安全优先),进阶阶段对低风险意图(HOW_TO/NAVIGATE)用方案 B,高风险意图(SEARCH)用方案 A。

## 三、有害内容过滤

### 3.1 有害内容分类

| 类别 | 代码 | 示例 |
|------|------|------|
| 暴力/伤害 | VIOLENCE | 教唆自残、暴力方法 |
| 色情 | SEXUAL | 露骨性描写 |
| 违法 | ILLEGAL | 诈骗方法、毒品制作 |
| 仇恨/歧视 | HATE | 种族/性别歧视言论 |
| 自杀/自残 | SELF_HARM | 鼓励或方法指导 |
| 骚扰 | HARASSMENT | 针对个人的辱骂 |
| 个人信息 | PII_LEAK | 泄露他人隐私 |

### 3.2 检测方法

**层 1:关键词过滤(快)**
- 维护敏感词库(分品类),命中即标记。
- 用于明显违规内容,~1ms。

**层 2:LLM 分类(准)**
- 对每条回答用小模型(豆包 Lite)做安全分类:

```
你是内容安全审核助手。判断以下 AI 回答是否包含有害内容。

有害内容包括:暴力、色情、违法、仇恨歧视、自残、骚扰、隐私泄露。

AI 回答: {response}

输出 JSON:
{
  "safe": true/false,
  "categories": ["VIOLENCE", "ILLEGAL"],  // 若 unsafe
  "confidence": 0.0-1.0,
  "reason": "..."
}
```

- 置信度 ≥ 0.8 且 unsafe → 拦截。
- 0.5 ≤ 置信度 < 0.8 → 人工审核队列。
- < 0.5 → 放行。

### 3.3 拦截后处理

```java
if (!safetyResult.isSafe()) {
    // 1. 不返回原回答
    // 2. 返回标准安全提示
    String safeResponse = "抱歉,我生成的回答可能包含不适当的内容,已为你拦截。"
                       + "请换个方式提问,或联系客服。";

    // 3. 记录安全事件
    securityEventLogger.log(userId, "UNSAFE_OUTPUT",
        originalResponse, safetyResult);

    // 4. 流式场景:发送 error 事件 + 替代文本
    sseEmitter.send(SseEvent.error("CONTENT_FILTERED"));
    sseEmitter.send(SseEvent.token(safeResponse));
    sseEmitter.send(SseEvent.done());

    return;
}
```

## 四、幻觉检测

### 4.1 什么是幻觉

幻觉(Hallucination)指 LLM 生成的内容**在检索文档中找不到支撑**,包括:
- **事实幻觉**:陈述了不存在的事实(如「CampusShare 有 10 万用户」实际没有)。
- **引用幻觉**:引用了 `[3]` 但第 3 个文档并没有这个信息。
- **数字幻觉**:编造了具体的数字/日期/统计。

### 4.2 检测方法:NLI(自然语言推理)

将回答拆分为原子论断,逐条与检索文档做 NLI 判断:

```
论断: "创作者认证需要总获赞 10000"
文档: "创作者认证条件:总获赞≥10000 且发帖≥50"

NLI 判定: ENTAILMENT(文档支持论断) → 不是幻觉
```

```
论断: "CampusShare 已覆盖全国 200 所高校"
文档: (无相关信息)

NLI 判定: NEUTRAL/CONTRADICTION → 疑似幻觉
```

**实现**:用 LLM 做 NLI 判断(豆包 Lite,成本低):

```
你是事实核查助手。判断以下论断是否能被参考文档支持。

论断: {claim}

参考文档:
[1] {doc1}
[2] {doc2}
...

输出 JSON:
{
  "verdict": "ENTAILMENT" | "CONTRADICTION" | "NEUTRAL",
  "supported_by": [1],  // ENTAILMENT 时标注哪些文档支持
  "reason": "..."
}
```

### 4.3 幻觉处理策略

| NLI 判定 | 处理 |
|----------|------|
| ENTAILMENT | 保留论断 |
| CONTRADICTION | 删除该论断或整条回答重新生成 |
| NEUTRAL | 标记为「低置信」,保留但弱化语气(「据了解」「可能」) |

**批量幻觉率**:
```
幻觉率 = NEUTRAL+CONTRADICTION 论断数 / 总论断数
```
- 幻觉率 > 20% → 整条回答不可信,返回「抱歉,我无法确认这个问题的答案」。
- 幻觉率 10-20% → 删除可疑论断后返回(附带「以上信息基于检索结果,仅供参考」提示)。

### 4.4 与 Groundedness 指标的关系
- 幻觉检测是**实时护栏**(生成后立即检查)。
- Groundedness 是**离线评估指标**(黄金集回归时统计)。
- 二者用相同的 NLI 方法,但实时护栏只做粗粒度检查(避免延迟过高)。

## 五、引用验证

### 5.1 引用映射校验

Agent 回答中的 `[n]` 引用必须对应检索结果中第 n 个文档。验证:

```python
def verify_citations(response, retrieved_docs):
    # 1. 提取回答中所有 [n] 引用
    citations = re.findall(r'\[(\d+)\]', response)

    errors = []
    for n in citations:
        n = int(n)
        # 检查引用编号是否越界
        if n < 1 or n > len(retrieved_docs):
            errors.append(f"引用 [{n}] 越界,最多 {len(retrieved_docs)}")
            continue

        # 检查引用的文档是否真的支持该论断 (NLI)
        # 提取 [n] 前的论断文本
        claim = extract_claim_before_citation(response, n)
        doc = retrieved_docs[n-1]
        nli_result = nli_check(claim, doc)

        if nli_result.verdict != "ENTAILMENT":
            errors.append(f"引用 [{n}] 与文档内容不匹配: {nli_result.reason}")

    return {
        "valid": len(errors) == 0,
        "errors": errors,
        "citation_accuracy": 1 - len(errors) / max(len(citations), 1)
    }
```

### 5.2 引用错误处理
- **引用越界**:`[5]` 但只有 3 个文档 → 删除该引用。
- **引用不匹配**:论断与文档内容矛盾 → 删除论断或修正引用。
- **无引用论断**:重要事实论断没有引用 → 添加「(未找到确切来源)」标注。

### 5.3 引用编号连续性
- 引用编号必须连续(1,2,3...),不能跳号(1,3,5)。
- 跳号时重新编号,确保 `[n]` 对应第 n 个引用卡。

## 六、PII 泄露检测

### 6.1 风险场景
- Agent 在搜索帖子时,可能返回包含其他用户 PII 的内容(如帖子中写了手机号)。
- LLM 可能「幻觉」编造用户信息。

### 6.2 检测
对 LLM 输出跑与 input-guardrails 相同的 PII 正则检测:
- 检测到手机号/身份证/邮箱/银行卡 → 脱敏后返回。
- 检测到完整姓名 + 手机号组合 → 整条回答标记高危,人工审核。

```java
RedactionResult outputRedaction = piiRedactor.redact(llmResponse);
if (outputRedaction.hasHighRiskPii()) {
    // 姓名+手机号 / 身份证完整出现
    securityEventLogger.log(userId, "PII_LEAK_OUTPUT", llmResponse, outputRedaction);
    return GuardrailResult.blocked("PII_LEAK",
        "抱歉,回答中可能包含敏感信息,已为你拦截。");
}
String safeResponse = outputRedaction.getRedactedText();
```

## 七、格式校验

### 7.1 必须满足的格式
- Markdown 合法(无未闭合的代码块/标题)。
- 引用编号格式统一 `[n]`(非 `[n]、(n)、【n】`)。
- 不含未支持的 HTML 标签(防 XSS)。
- 不含超长无换行段落(>500 字符无换行 → 强制插入换行)。

### 7.2 实现
```python
def validate_format(response):
    issues = []

    # 1. 代码块闭合检查
    if response.count("```") % 2 != 0:
        issues.append("UNCLOSED_CODE_BLOCK")

    # 2. HTML 标签检查 (前端用 react-markdown 禁 rehype-raw, 但后端也防御)
    if re.search(r'<(script|iframe|img|a)\b', response, re.IGNORECASE):
        issues.append("UNSAFE_HTML_TAG")

    # 3. 引用格式统一
    non_standard = re.findall(r'[（(【]\d+[)）】]', response)
    if non_standard:
        issues.append(f"NON_STANDARD_CITATION: {non_standard}")

    # 4. 超长无换行段落
    for para in response.split('\n'):
        if len(para) > 500:
            issues.append(f"LONG_PARAGRAPH: {len(para)} chars")

    return {"valid": len(issues) == 0, "issues": issues}
```

### 7.3 格式自动修复
- 非标准引用 `（3）` → 自动替换为 `[3]`。
- 超长段落 → 每 300 字符智能断句插入换行。
- 未闭合代码块 → 末尾补 ` ``` `。

## 八、护栏失败降级

当输出护栏检测到问题时,按严重程度降级:

| 严重度 | 问题 | 降级动作 |
|--------|------|----------|
| CRITICAL | 有害内容(VIOLENCE/ILLEGAL) | 拦截,返回安全提示 |
| CRITICAL | PII 泄露(完整) | 拦截,返回安全提示 |
| HIGH | 幻觉率 > 20% | 拦截,返回「无法确认」 |
| HIGH | 引用越界 | 修复引用后返回 |
| MEDIUM | 幻觉率 10-20% | 删除可疑论断 + 弱化语气 |
| MEDIUM | 引用不匹配 | 删除错误引用 |
| LOW | 格式问题 | 自动修复后返回 |

## 九、决策记录

### ADR-178: 输出护栏 - 有害过滤 + 幻觉检测 + 引用验证
- **背景**:LLM 可能生成有害、幻觉、错误引用的内容,必须在返回前拦截。
- **决策**:
  - 有害内容:关键词 + LLM 分类双层检测,unsafe 且置信度≥0.8 拦截。
  - 幻觉检测:NLI 方法,逐论断判断,幻觉率>20% 拦截,10-20% 删除可疑论断。
  - 引用验证:编号越界检查 + NLI 论断-文档匹配,错误引用删除或修正。
  - PII 泄露:输出跑 PII 正则,高危拦截,低危脱敏。
  - 格式校验:markdown 合法性 + 引用格式统一 + XSS 防御。
  - 流式场景:MVP 用「先检查后流式」(安全优先),进阶对低风险意图用「边流边检」。
- **理由**:输出是最后一道防线,必须严格;参考 Azure AI Content Safety 和 AWS Bedrock Guardrails。
- **权衡**:先检查后流式增加首 token 延迟 ~1s,但安全优先;进阶阶段可优化。
- **状态**:采纳
