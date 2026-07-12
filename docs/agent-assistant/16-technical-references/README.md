# 16 - 技术参考

> 状态: 草稿
> 最后更新: 2026-06-30
> 适用范围: Agent 设计的外部技术参考与借鉴

## 一、本目录定位

用户明确要求:「参考 Claude、字节跳动、DeepSeek 等开源方案,不能介意工程庞大」。

本目录不是教程,而是**技术拆解**:分析业界领先 Agent 产品的架构设计、关键技术、可借鉴点,为 CampusShare agent 的实现提供参考依据。

每个分析文档遵循统一结构:
1. **产品概述**:是什么,解决什么问题。
2. **架构拆解**:核心组件、数据流、关键技术。
3. **可借鉴点**:哪些设计适合 CampusShare。
4. **不适合借鉴点**:哪些设计不适合(规模/场景差异)。
5. **ADR**:借鉴决策记录。

## 二、分析对象

| 对象 | 类型 | 分析文档 | 借鉴重点 |
|------|------|----------|----------|
| Claude | 闭源产品 | [claude-analysis.md](./claude-analysis.md) | 上下文工程、Artifacts、工具使用 |
| DeepSeek | 开源模型+技术 | [deepseek-analysis.md](./deepseek-analysis.md) | 模型选型、prefix cache、评估方法 |
| 豆包(字节) | 闭源产品 | [doubao-bytedance-analysis.md](./doubao-bytedance-analysis.md) | A/B 测试、体验度量、内容安全 |
| Dify/LangChain | 开源框架 | [dify-langchain-analysis.md](./dify-langchain-analysis.md) | Agent 编排、RAG 流水线、工具抽象 |
| AutoGen | 开源框架 | [autogen-multi-agent-analysis.md](./autogen-multi-agent-analysis.md) | 多 Agent 协作、对话模式 |

## 三、借鉴原则

### 原则 1: 不照搬,理解后取舍
- 大厂方案为亿级用户设计,CampusShare 是校园项目(千级用户)。
- 理解设计意图,而非复制实现细节。
- 例如:Claude 的 200K 上下文窗口我们用不到,但其上下文管理方法论可借鉴。

### 原则 2: 优先借鉴开源可复现的
- 闭源产品(Claude/豆包)借鉴**方法论**。
- 开源框架(Dify/LangChain/AutoGen)借鉴**实现细节**。
- 开源模型(DeepSeek)直接**使用**。

### 原则 3: 记录「为什么不借鉴」
- 不借鉴的设计同样重要,记录原因避免重复讨论。
- 例如:AutoGen 的多 Agent 对话对校园场景过重,MVP 不采用。

## 四、与主文档的关系

本目录是**参考性文档**,不直接产出 ADR 进入主设计。但主设计文档(00-15)中的很多决策受这些分析影响:

| 主设计 | 参考来源 |
|--------|----------|
| 03-llm-strategy | DeepSeek 模型选型 |
| 06-retrieval | Dify RAG 流水线 |
| 07-agent-design | Claude 工具使用、AutoGen 多 Agent |
| 08-prompt-engineering | Claude 上下文工程、DeepSeek prefix cache |
| 09-context-engineering | Claude 上下文压缩 |
| 13-evaluation | DeepSeek 评估方法、字节 A/B 测试 |
| 14-safety-and-guardrails | 字节内容安全、Claude Constitutional AI |

## 五、决策记录

### ADR-187: 技术参考分析 - 5 大对象拆解
- **背景**:用户要求参考业界方案,需系统化拆解避免零散借鉴。
- **决策**:分析 Claude/DeepSeek/豆包/Dify-LangChain/AutoGen 五大对象,每个按统一结构拆解。
- **原则**:理解意图而非照搬实现;开源借鉴细节,闭源借鉴方法论;记录不借鉴的原因。
- **状态**:采纳
