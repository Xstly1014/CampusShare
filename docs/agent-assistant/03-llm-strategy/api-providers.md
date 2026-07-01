# API Provider 接入

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、统一抽象层

agent-service 内建 `LlmClient` 统一接口，屏蔽 Provider 差异：

```java
public interface LlmClient {
    Flux<String> streamChat(LlmRequest req);     // 流式
    LlmResponse chat(LlmRequest req);            // 非流式
    List<Float> embed(String text);              // 向量化
}
```

实现类：
- `DeepSeekLlmClient`（主）
- `DoubaoLlmClient`（兜底）
- `QwenLlmClient`（兜底2）
- `BgeEmbeddingClient`（embedding，独立 Provider 可不同）

## 二、DeepSeek 接入

- **Base URL**：`https://api.deepseek.com`
- **API 兼容 OpenAI 格式**：`/v1/chat/completions`、`/v1/embeddings`（DeepSeek 暂无 embedding，用 BGE 替代）
- **模型名**：
  - `deepseek-chat`（V3）
  - `deepseek-reasoner`（R1，仅离线）
- **认证**：`Authorization: Bearer ${LLM_API_KEY}`
- **Tool Use**：支持 OpenAI 风格 `tools` + `tool_choice`，返回 `tool_calls`。
- **流式**：`stream: true`，SSE 格式 `data: {...}`，结束 `data: [DONE]`。
- **上下文缓存**：DeepSeek 自动对前缀命中缓存，价格更低 → 把系统提示词、Few-shot 固定前缀放最前。

## 三、豆包(Doubao)接入

- **Base URL**：`https://ark.cn-beijing.volces.com/api/v3`
- **兼容 OpenAI 格式**。
- **模型名**：用 Endpoint ID（如 `ep-xxxxxxxx`），在火山引擎控制台创建。
- **优势**：国内延迟低（<300ms 首 token），适合兜底。

## 四、BGE Embedding 接入

- **Provider**：硅基流动 `https://api.siliconflow.cn/v1/embeddings`（BGE-M3 托管）
- **请求**：`{model:"BAAI/bge-m3", input:[...], encoding_format:"float"}`
- **维度**：1024
- **批量**：单次最多 64 条文本，控制单条 ≤ 512 token。
- **备选**：智源官方、自部署（`text-embeddings-inference` 服务）。

## 五、重排模型接入（进阶）

- **Provider**：硅基流动 `/v1/rerank`
- **模型**：`BAAI/bge-reranker-v2-m3`
- **请求**：`{model, query, documents:[...], top_n:5}`
- **返回**：`[{index, relevance_score}]`

## 六、密钥管理

- 所有 API Key 通过环境变量注入（`.env`，不提交）。
- `.env.example` 提供占位模板。
- 容器内通过 `LLM_API_KEY` 等环境变量读取，禁止硬编码。
- 日志中 Key 必须脱敏（`sk-***xxxx`）。

## 七、超时与重试

| 调用 | 超时 | 重试 | 退避 |
|------|------|------|------|
| 流式生成 | 首 token 10s / 总 60s | 1 次 | 不重试流式，直接降级 |
| 非流式生成 | 30s | 2 次 | 指数 1s/2s |
| Embedding | 10s | 3 次 | 指数 0.5s/1s/2s |
| 重排 | 5s | 2 次 | 指数 0.5s/1s |

## 八、限流与配额

- DeepSeek：按账户 TPM/RPM 限流，429 时切兜底。
- 本地用 Redis 做令牌桶限流，防止单用户刷接口（每用户每分钟 ≤ 20 次）。
