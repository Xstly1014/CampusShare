# 降级与兜底策略

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、降级层级（从高到低）

```
L0 正常：DeepSeek-V3 主生成 + BGE-M3 embedding + 向量检索 + 重排
   │ 失败/超时/限流
   ▼
L1 模型降级：切豆包/Qwen 兜底生成，其余不变
   │ 兜底也失败
   ▼
L2 检索降级：向量库不可用 → 纯 MySQL FULLTEXT 关键词检索
   │ 检索也失败
   ▼
L3 Embedding 降级：BGE 不可用 → 关键词检索 + 无向量
   │ LLM 全不可用
   ▼
L4 只读模式：返回检索到的帖子卡/帮助片段，不生成自然语言答案
   │ 全失败
   ▼
L5 友好错误：提示「助手暂时繁忙，请稍后再试」+ 引导浏览首页
```

## 二、切换触发条件

| 触发 | 动作 | 恢复 |
|------|------|------|
| 主模型连续 3 次超时或 5xx | 切兜底模型 | 熔断器半开探测，60s 成功恢复主 |
| 主模型 429 限流 | 切兜底模型 | 限流恢复后切回 |
| 向量库连接失败 | 降级 FULLTEXT | 健康检查通过恢复 |
| Embedding 失败 | 跳过向量，只用关键词 | 自动重试恢复 |
| 所有 LLM 不可用(>30s) | 只读模式 | LLM 健康后恢复 |

## 三、熔断器（Resilience4j）

```java
CircuitBreaker deepseekCb = CircuitBreaker.of("deepseek", CircuitBreakerConfig.custom()
    .failureRateThreshold(50)           // 失败率50%打开
    .slowCallRateThreshold(60)          // 慢调用率60%打开
    .slowCallDurationThreshold(Duration.ofSeconds(10))
    .waitDurationInOpenState(Duration.ofSeconds(60))
    .permittedNumberOfCallsInHalfOpenState(3)
    .build());
```

## 四、降级时的用户体验

- L1：用户无感知（答案正常，可能略慢）。
- L2/L3：答案前加提示「部分检索能力暂时受限，结果可能不全」。
- L4：返回「我现在无法生成完整回答，但为您找到以下相关内容：」+ 帖子卡列表。
- L5：Toast 提示 + 引导。

## 五、健康检查

- `/actuator/health` 包含：
  - `llm-provider`：探测主模型 `/models` 端点（轻量）。
  - `vector-store`：PG-Vector `SELECT 1`。
  - `embedding`：可选，不阻断健康（避免 Embedding 抖动导致服务不可用）。
- LLM 不可用不直接让服务 health=DOWN（避免被网关摘除），而是 `degraded` 状态，仍能服务只读模式。
