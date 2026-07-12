# 意图分类法

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、意图体系

```
HOW_TO         —— 平台功能操作指引（怎么用某功能）
SEARCH         —— 资源/讨论帖检索（找东西）
NAVIGATE       —— 功能/板块定位（去哪儿）
CLARIFY        —— 多轮澄清/追问/指代（接着上文问）
OUT_OF_SCOPE   —— 超范围（闲聊/开放域/写操作/敏感）
```

## 二、子意图（二级标签，用于路由细化）

| 一级 | 二级 | 触发特征 | 处理管线 |
|------|------|----------|----------|
| HOW_TO | feature_help | 「怎么/如何/在哪设置」+ 平台功能词 | 知识库检索 → 生成步骤 |
| HOW_TO | rule_explain | 「为什么/什么意思」+ 平台规则词（单向消息、金色V） | 知识库检索 → 解释 |
| SEARCH | resource | 「求/找/有没有」+ 资源类词 + 学校/科目 | 帖子检索(resource 类型) |
| SEARCH | discussion | 「讨论/聊聊/怎么看」+ 话题 | 帖子检索(discussion 类型) |
| SEARCH | content_qa | 「那个帖子说了什么」+ 指代 | 定位帖 → 内容抽取回答 |
| NAVIGATE | feature_loc | 「在哪/入口」+ 功能名 | 返回跳转入口 |
| NAVIGATE | section_loc | 「板块/分类在哪」+ 分类名 | 返回分类卡 |
| NAVIGATE | my_list | 「我xxx的帖子」+ 个人列表词 | 返回 /profile/:type |
| CLARIFY | coreference | 「那个/上面那个/它」+ 指代 | 上下文消解 |
| CLARIFY | refine | 「我说的是xxx/不对」+ 修正 | 修正后重走原管线 |
| CLARIFY | followup | 「那xxx呢/接着问」+ 追问 | 上下文 + 新检索 |
| OUT_OF_SCOPE | chitchat | 闲聊问候 | 礼貌拒绝+引导 |
| OUT_OF_SCOPE | open_domain | 开放域知识 | 拒绝+引导 |
| OUT_OF_SCOPE | write_action | 「帮我发/帮我点赞/帮我改」 | 拒绝代操作+给指引 |
| OUT_OF_SCOPE | sensitive | 政治/医疗/法律 | 拒绝 |

## 三、分类方法

### 3.1 主方法：LLM 分类（MVP）
- 一次 LLM 调用，输出 JSON `{intent, sub_intent, confidence, rewritten_query}`。
- Prompt 含意图定义 + Few-shot（见 `08-prompt-engineering`）。
- 低延迟路径：用 DeepSeek-V3，温度 0，max_tokens 200。

### 3.2 辅助：规则预筛（短路，省 LLM 调用）
- 显式写操作词（「帮我发帖/帮我点赞/帮我关注/帮我改密码」）→ 直接 OUT_OF_SCOPE/write_action，不调 LLM。
- 显式闲聊词（「你好/你是谁/谢谢」）→ 直接 OUT_OF_SCOPE/chitchat。
- 显式个人列表词（「我点赞的/我收藏的/我回复的/我的浏览历史」）→ 直接 NAVIGATE/my_list。
- 这些规则命中后跳过 LLM，省钱省延迟。

### 3.3 阈值与兜底
- LLM confidence < 0.6 → 视为 SEARCH（最通用兜底，宁可检索一下）。
- 多轮上下文下若当前 query 含指代词（那个/它/上面）→ 强制 CLARIFY。

## 四、意图到管线路由

```
intent ──► pipeline
HOW_TO/feature_help     ─► 知识库检索 → 生成
HOW_TO/rule_explain     ─► 知识库检索 → 生成
SEARCH/*                ─► Agent ReAct(主流程)
NAVIGATE/feature_loc    ─► 直接返回跳转卡(可能不调 LLM 生成)
NAVIGATE/section_loc    ─► 分类检索 → 返回分类卡
NAVIGATE/my_list        ─► 直接返回 /profile/:type 跳转
CLARIFY/*               ─► 上下文消解 → 重走原管线 or 直接答
OUT_OF_SCOPE/*          ─► 拒绝模板
```

## 五、意图识别评估

- 黄金集：`13-evaluation/test-datasets.md` 中每条标 `expected_intent`。
- 指标：accuracy、per-intent F1、混淆矩阵。
- 目标：overall accuracy ≥ 92%，OUT_OF_SCOPE recall ≥ 95%（宁拒勿错答）。

## 六、决策记录 (ADR)

### ADR-009: 意图分类用 LLM 而非纯规则
- **理由**：用户表达多变（「咋」「咋整」「咋关」），规则维护成本高；LLM 泛化好。
- **优化**：高频确定性模式用规则短路，省 LLM 调用。

### ADR-010: confidence 低时兜底为 SEARCH
- **理由**：SEARCH 管线最通用（检索 + 生成），即使分错也能给有用结果；NAVIGATE/HOW_TO 分错会完全跑偏。
- **例外**：含指代词强制 CLARIFY，避免误检索。
