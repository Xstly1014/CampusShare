# DeepSeek 技术分析

> 状态: 草稿
> 最后更新: 2026-06-30
> 分析对象: DeepSeek-V3 / DeepSeek-R1 (深度求索)
> 参考: DeepSeek 官方技术报告、API 文档、开源代码

## 一、产品概述

DeepSeek 是深度求索(杭州)开发的 LLM 系列,以「高性价比、开源、推理能力强」著称:

- **DeepSeek-V3**:MoE 架构,671B 总参数/37B 激活,通用对话模型。
- **DeepSeek-R1**:推理模型,RL 训练,擅长数学/代码/复杂推理。
- **开源**:模型权重开源(V3/R1),可本地部署。
- **低价**:API 价格约为 GPT-4 的 1/10。

## 二、架构拆解

### 2.1 MoE 架构

DeepSeek-V3 采用 Mixture of Experts:

```
输入 token
    │
    ▼
┌──────────────┐
│  路由器       │  决定激活哪些专家
│  (Router)    │
└──────┬───────┘
       │
    ┌──┴──┬───────┬───────┐
    ▼     ▼       ▼       ▼
  专家1  专家2   专家3  ... 专家256
  (37B 激活, 总 671B)
       │
       ▼
   输出 token
```

- 总参数 671B,但每个 token 只激活 37B,推理成本接近 37B 稠密模型。
- **对 CampusShare 的意义**:我们用 API,不关心架构细节,但低价正是 MoE 的结果。

### 2.2 Prefix Cache(核心借鉴点)

DeepSeek API 支持**前缀缓存**:

```
请求 1: [System Prompt 1000 token] + [用户问题 A]
         ← 缓存 1000 token

请求 2: [System Prompt 1000 token] + [用户问题 B]
         ← 命中缓存!1000 token 按 0.1 折计费
```

- 固定前缀(如 system prompt)命中缓存时,输入 token 按 **10%** 计费。
- 缓存有效期:数小时(具体未公开)。
- **对成本的影响**:System Prompt 1500 token,缓存后按 150 token 计费,单次省 ¥0.00135。

### 2.3 Multi-Token Prediction

DeepSeek-V3 训练时支持多 token 预测,推理时:
- 传统模型:一次预测 1 个 token。
- DeepSeek-V3:可一次预测多个 token(speculative decoding)。
- **效果**:推理速度提升,首 token 延迟更低。

### 2.4 R1 的推理能力

DeepSeek-R1 通过 RL(强化学习)训练推理:

```
问题 → [思考过程 <think>...</think>] → [答案]
```

- R1 会显式输出思考过程(Chain of Thought)。
- 思考过程对用户可见(或可隐藏)。
- 适合:数学证明、逻辑推理、代码调试。
- 不适合:简单对话(思考过程增加延迟和成本)。

### 2.5 评估方法(借鉴点)

DeepSeek 技术报告中的评估方法:

**自动评估**:
- MMLU(知识)、GSM8K(数学)、HumanEval(代码)等标准 benchmark。
- 使用 LLM-as-Judge 评估开放域生成质量。

**人工评估**:
- 对比 GPT-4/Claude,人类偏好投票。
- 分维度评估:准确性、有用性、安全性。

**关键指标**:
- Pass@1:一次生成正确率。
- Win Rate:对比其他模型的胜率。
- Safety Rate:安全通过率。

## 三、可借鉴点

### 3.1 模型选型(✅ 已借鉴)
→ 见 [03-llm-strategy/model-selection.md](../03-llm-strategy/model-selection.md)
- V3 主生成(性价比高)、R1 离线反思(推理强)、豆包兜底。
- 与 DeepSeek 定位一致:V3 日常、R1 复杂。

### 3.2 Prefix Cache(✅ 已借鉴)
→ 见 [08-prompt-engineering/system-prompt-design.md](../08-prompt-engineering/system-prompt-design.md)
- System Prompt 设计为固定前缀,确保命中缓存。
- L1 平台级 prompt 固定不变,L2 任务级按意图选择(每意图固定)。
- 成本节省 ~15%。

### 3.3 R1 用于反思(✅ 已借鉴)
→ 见 [07-agent-design/reflection-self-validation.md](../07-agent-design/reflection-self-validation.md)
- 困难场景触发 R1 反思(思考过程)。
- 反思结果用于校验 V3 的回答。
- 限制触发率 ≤15% 控制成本。

### 3.4 LLM-as-Judge 评估(✅ 已借鉴)
→ 见 [13-evaluation/llm-as-judge.md](../13-evaluation/llm-as-judge.md)
- 6 维度 Rubric 评估,与 DeepSeek 评估思路一致。
- 评估者 ≠ 被评估者(用 GPT-4o/Claude 评估 DeepSeek)。

### 3.5 R1 思考过程透明化(部分借鉴)
- R1 的 `<think>` 标签可对用户展示「思考过程」。
- CampusShare 可在进阶阶段展示 R1 反思的思考过程,增加透明度。
- MVP 不展示(增加延迟感)。

## 四、不适合借鉴点

### 4.1 本地部署
- DeepSeek 开源可本地部署,但 671B 模型需多卡 H100,CampusShare 无此资源。
- 统一用 API 调用。

### 4.2 MoE 架构细节
- 架构对 API 用户不可见,无需关心。

### 4.3 RL 训练
- R1 的 RL 训练需要大量算力和数据,CampusShare 不做模型训练。
- 直接用训练好的 R1 API。

### 4.4 Speculative Decoding
- 这是推理引擎优化,API 用户无法控制。
- 但受益于 DeepSeek 服务端的优化(更快响应)。

## 五、API 使用实践

### 5.1 API 调用

```java
@Component
public class DeepSeekClient {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public DeepSeekResponse chat(DeepSeekRequest request) {
        return webClient.post()
            .uri(baseUrl + "/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(DeepSeekResponse.class)
            .timeout(Duration.ofSeconds(30))
            .block();
    }

    // 流式调用 (SSE)
    public Flux<DeepSeekChunk> chatStream(DeepSeekRequest request) {
        request.setStream(true);
        return webClient.post()
            .uri(baseUrl + "/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(DeepSeekChunk.class)
            .timeout(Duration.ofSeconds(60));
    }
}
```

### 5.2 Prefix Cache 命中技巧

```java
public DeepSeekRequest buildRequest(String systemPrompt, String userMessage) {
    return DeepSeekRequest.builder()
        .model("deepseek-chat")  // V3
        .messages(List.of(
            Message.builder().role("system").content(systemPrompt).build(),  // 固定前缀
            Message.builder().role("user").content(userMessage).build()       // 变化部分
        ))
        .build();
}
// 注意: system prompt 必须完全一致(含空格/换行)才能命中缓存
// 因此 prompt 模板从文件加载,不动态拼接
```

### 5.3 R1 思考过程处理

```java
public String extractAnswer(String r1Response) {
    // R1 输出格式: <think>思考过程</think>实际答案
    int thinkEnd = r1Response.indexOf("</think>");
    if (thinkEnd != -1) {
        String thinking = r1Response.substring(0, thinkEnd + 8);
        String answer = r1Response.substring(thinkEnd + 8).trim();
        // 思考过程可用于反思日志,不返回给用户
        log.debug("R1 thinking", kv("thinking", thinking));
        return answer;
    }
    return r1Response;
}
```

## 六、成本对比

| 模型 | 输入(元/百万token) | 输出 | CampusShare 用途 |
|------|---------------------|------|-------------------|
| DeepSeek-V3 | ¥1 | ¥2 | 主生成(70%+ 调用) |
| DeepSeek-R1 | ¥4 | ¥16 | 反思(≤15% 调用) |
| GPT-4o | ¥18 | ¥54 | 评估者(发版前) |
| Claude 3.5 | ¥21 | ¥63 | 评估者(备选) |

- DeepSeek-V3 比 GPT-4o 便宜 **18-27 倍**。
- 这是 CampusShare 选择 DeepSeek 的核心理由。

## 七、决策记录

### ADR-189: DeepSeek 技术借鉴 - 模型选型 + Prefix Cache + R1 反思
- **背景**:DeepSeek 是性价比最高的开源 LLM,适合 CampusShare。
- **决策**:
  - 借鉴:V3 主生成 + R1 反思的分层模型策略;Prefix Cache 命中(prompt 固定前缀);LLM-as-Judge 评估方法。
  - 不借鉴:本地部署(无算力)、MoE 架构细节(API 不可见)、RL 训练(不做)。
- **理由**:DeepSeek 性价比是 GPT-4 的 18 倍;开源生态透明;API 稳定。
- **状态**:采纳
