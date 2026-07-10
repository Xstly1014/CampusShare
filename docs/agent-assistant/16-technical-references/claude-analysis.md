# Claude 架构分析

> 状态: 草稿
> 最后更新: 2026-06-30
> 分析对象: Anthropic Claude (Claude 3.5 Sonnet / Claude 3 Opus)
> 参考: Anthropic 官方文档、公开技术博客、Constitutional AI 论文

## 一、产品概述

Claude 是 Anthropic 开发的 AI 助手,以「安全、有用、诚实」为核心价值观。与 ChatGPT 相比,Claude 在以下方面有独特设计:
- **超长上下文**:支持 200K token 上下文窗口。
- **Artifacts**:侧边面板渲染代码/文档/图表,实时可编辑。
- **Computer Use**:可操作浏览器和桌面应用(MVP)。
- **Constitutional AI**:通过「宪法」规则自我约束,减少有害输出。

## 二、架构拆解

### 2.1 整体架构(推测)

```
用户输入
    │
    ▼
┌─────────────────────┐
│  上下文组装          │  ← 200K 窗口, 动态分配
│  (Context Assembly) │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  工具路由            │  ← 决定是否调用工具
│  (Tool Routing)     │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  生成 + 流式输出     │
│  (Generation)       │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  输出后处理          │  ← Artifacts 渲染、引用校验
│  (Post-processing)  │
└─────────────────────┘
```

### 2.2 上下文工程(核心借鉴点)

Claude 的 200K 上下文管理是业界标杆:

**分层装载**:
- System Prompt(固定):角色定义、能力边界、安全规则。
- 工具定义(半固定):工具名称、参数、描述。
- 历史对话(动态):按时间倒序装载,超出窗口则摘要。
- 检索结果(动态):RAG 召回的文档。
- 用户输入(动态):当前提问。

**上下文压缩**:
- 长对话中,旧消息会被**渐进式摘要**(rolling summary)。
- 摘要保留关键信息(实体、决策、用户偏好),丢弃寒暄。
- 用户可「pin」重要消息,不被压缩。

**预填充(Prefill)**:
- Claude 支持 `assistant` 角色预填充,引导输出格式。
- 例如预填充 `{` 强制 JSON 输出。

### 2.3 工具使用(Tool Use)

Claude 的工具调用设计:

```json
// 工具定义
{
  "name": "search_web",
  "description": "搜索互联网获取最新信息",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "搜索关键词"}
    },
    "required": ["query"]
  }
}
```

**特点**:
- 工具调用是**结构化的**(JSON Schema),非自由文本。
- 支持并行工具调用(一次返回多个 tool_use)。
- 工具结果以 `tool_result` 角色返回,与用户消息区分。
- LLM 自主决定何时调用工具、调用几次。

### 2.4 Artifacts

Claude 的 Artifacts 是侧边面板渲染:

```
┌──────────────┬─────────────────────┐
│  对话区       │  Artifact 区         │
│              │                      │
│  User: ...   │  [代码/文档/图表]    │
│  Claude: ... │  可编辑、可下载      │
│              │  支持版本历史         │
│              │                      │
└──────────────┴─────────────────────┘
```

- 检测到代码/Markdown/SVG/HTML 时,自动提取到 Artifact 区。
- 用户可在 Artifact 区直接编辑,Claude 根据编辑继续优化。
- 支持版本历史,可回退。

### 2.5 Constitutional AI

Claude 的安全方法:

```
有害输入 → LLM 生成初始回答(可能有害)
         → 自我评估:这个回答违反宪法规则吗?
         → 如果违反,生成修订版
         → 输出修订版
```

**宪法规则示例**:
- 「请不要生成仇恨或歧视性内容」
- 「请不要帮助用户进行非法活动」
- 「当不确定时,请诚实说明」

## 三、可借鉴点

### 3.1 上下文分层装载(✅ 已借鉴)
→ 见 [09-context-engineering/context-window-management.md](../09-context-engineering/context-window-management.md)
- CampusShare 采用 L0-L5 六层分层,与 Claude 设计一致。
- 但窗口限制为 8K(DeepSeek-V3),而非 200K,需更激进的压缩。

### 3.2 渐进式摘要(✅ 已借鉴)
→ 见 [09-context-engineering/context-compression.md](../09-context-engineering/context-compression.md)
- rolling summary + slot freezing + pin message 三级压缩。
- 借鉴了 Claude 的「pin 不被压缩」设计。

### 3.3 结构化工具调用(✅ 已借鉴)
→ 见 [10-tools-and-apis/tool-specifications.md](../10-tools-and-apis/tool-specifications.md)
- JSON Schema 定义工具参数。
- tool_result 独立角色返回。

### 3.4 引用溯源(✅ 已借鉴)
→ 见 [10-tools-and-apis/sse-streaming-api.md](../10-tools-and-apis/sse-streaming-api.md)
- 回答中 `[n]` 引用 + 引用卡展示。
- 与 Claude 的 citation 功能一致。

### 3.5 Constitutional AI(部分借鉴)
→ 见 [14-safety-and-guardrails/output-guardrails.md](../14-safety-and-guardrails/output-guardrails.md)
- 借鉴「自我评估」思想:输出护栏中 LLM 自检有害内容。
- 但不做完整 Constitutional AI(需要 fine-tune,成本太高),改用规则+LLM 分类。

## 四、不适合借鉴点

### 4.1 200K 上下文窗口
- CampusShare 用 DeepSeek-V3(128K 理论上限,实际 8K 预算)。
- 无需处理 200K 上下文的复杂分页/检索问题。

### 4.2 Computer Use
- 操作浏览器/桌面应用对校园场景无需求。
- 且安全风险极高,MVP 不考虑。

### 4.3 Artifacts 侧边面板
- CampusShare 是移动端优先(底部 NavBar),无侧边面板空间。
- 代码渲染需求低(非编程助手)。
- 可在进阶阶段考虑「富内容卡片」替代。

### 4.4 完整 Constitutional AI
- 需要 RLHF + Constitutional AI 训练,成本极高。
- CampusShare 用 API 调用,无法改模型权重。
- 改用 prompt 约束 + 输出护栏替代。

## 五、关键启发

Claude 的设计哲学:**上下文是 Agent 的「工作记忆」,管理好上下文 = 管理好 Agent 能力**。

这对 CampusShare 的启示:
- 上下文工程(09)是核心,不是附属。
- Token 预算分配需要精心设计,而非简单截断。
- 压缩不是「删旧消息」,而是「保留信息密度」。

## 六、决策记录

### ADR-188: Claude 技术借鉴 - 上下文工程 + 结构化工具
- **背景**:Claude 在上下文管理和工具使用上业界领先,值得借鉴。
- **决策**:
  - 借鉴:分层上下文装载、渐进式摘要压缩、结构化工具调用、引用溯源、自我评估安全。
  - 不借鉴:200K 窗口(用 8K)、Computer Use、Artifacts 侧边面板、完整 Constitutional AI。
- **理由**:借鉴方法论而非实现规模;移动端场景决定了 UI 差异;API 模式决定了无法改模型。
- **状态**:采纳
