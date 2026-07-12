# 成本与性能监控

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[metrics-system.md](./metrics-system.md)、[dashboards-and-alerts.md](./dashboards-and-alerts.md)

## 一、为什么成本监控独立成文

Agent 与传统服务最大的区别:**每次请求都有真金白银的 LLM 成本**。传统服务扩容只需加机器,Agent 扩容还需考虑 LLM API 成本线性增长。

成本失控 = 直接经济损失。因此成本监控与性能监控同等重要,甚至优先级更高。

## 二、成本模型

### 2.1 各模型定价(2026 年参考价)

| 模型 | 用途 | 输入价格(元/百万token) | 输出价格 | 备注 |
|------|------|------------------------|----------|------|
| DeepSeek-V3 | 主生成 | ¥1 | ¥2 | 性价比最高 |
| DeepSeek-R1 | 离线反思 | ¥4 | ¥16 | 推理强但贵 |
| 豆包 Pro | 降级/评估 | ¥0.8 | ¥2 | 国内可访问 |
| 豆包 Lite | 安全分类 | ¥0.3 | ¥0.6 | 低成本分类 |
| BGE-M3 (自部署) | embedding | ¥0(硬件成本) | - | TEI 部署 |
| bge-reranker-v2-m3 (自部署) | 重排 | ¥0(硬件成本) | - | TEI 部署 |

### 2.2 单次对话成本估算

一次典型的 SEARCH 意图对话:

```
阶段                模型          输入token  输出token  成本(元)
─────────────────────────────────────────────────────────
意图分类            豆包Lite        300        20        0.0001
查询改写            DeepSeek-V3     500        100       0.0007
embedding           BGE-M3(自部署)  100        -         0(硬件)
检索(PG)           -               -          -         0(硬件)
重排               bge-reranker    -          -         0(硬件)
LLM生成            DeepSeek-V3     2000       500       0.0030
输出护栏(安全)     豆包Lite        800        30        0.0003
─────────────────────────────────────────────────────────
合计                                                   ¥0.0041
```

- **单次对话成本 ≈ ¥0.004**(SEARCH 意图,无反思)。
- **含反思(R1)的成本 ≈ ¥0.02**(反思用 R1,贵 4-8 倍)。
- **目标:单次对话 ≤ ¥0.05**,当前 ¥0.004 远低于目标。

### 2.3 月度成本预估

```
日活用户:    500
人均对话:    5 轮
日总对话:    2500
单次成本:    ¥0.005 (含偶尔反思)
日成本:      ¥12.5
月成本:      ¥375

预算:        ¥2000/月
利用率:      18.75%  ✓ 充足
```

## 三、成本采集

### 3.1 实时成本追踪

```java
@Component
@RequiredArgsConstructor
public class CostTracker {

    private final MeterRegistry registry;
    private final StringRedisTemplate redis;

    // 模型定价表 (元/百万 token)
    private static final Map<String, Pricing> PRICING = Map.of(
        "deepseek-v3", new Pricing(1.0, 2.0),
        "deepseek-r1", new Pricing(4.0, 16.0),
        "doubao-pro", new Pricing(0.8, 2.0),
        "doubao-lite", new Pricing(0.3, 0.6)
    );

    public void record(String userId, String sessionId, Long turnId,
                       String model, String phase,
                       int inputTokens, int outputTokens) {
        Pricing p = PRICING.getOrDefault(model, new Pricing(1.0, 2.0));
        double cost = (inputTokens * p.input + outputTokens * p.output) / 1_000_000;

        // 1. Prometheus 指标
        registry.counter("agent_llm_cost_yuan_total", "model", model).increment(cost);
        registry.counter("agent_llm_tokens_total", "model", model, "direction", "input").increment(inputTokens);
        registry.counter("agent_llm_tokens_total", "model", model, "direction", "output").increment(outputTokens);

        // 2. Redis 实时累加 (日/月/用户维度)
        String today = LocalDate.now().toString();
        redis.opsForValue().increment("cost:daily:" + today, cost);
        redis.opsForValue().increment("cost:monthly:" + today.substring(0, 7), cost);
        redis.opsForValue().increment("cost:user:" + userId + ":" + today, cost);
        redis.opsForValue().increment("cost:session:" + sessionId, cost);

        // 3. 异步写入 MySQL (审计 + 分析)
        costQueue.offer(new CostRecord(userId, sessionId, turnId, model, phase,
            inputTokens, outputTokens, cost, Instant.now()));
    }

    // 获取今日成本
    public double getTodayCost() {
        String key = "cost:daily:" + LocalDate.now();
        String val = redis.opsForValue().get(key);
        return val != null ? Double.parseDouble(val) : 0;
    }

    @Data @AllArgsConstructor
    private static class Pricing {
        double input;  // 元/百万 token
        double output;
    }
}
```

### 3.2 成本数据表

```sql
CREATE TABLE agent_cost_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_id BIGINT,
    model VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL COMMENT 'intent/rewrite/generate/reflect/judge/safety',
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    cost_yuan DECIMAL(10,6) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,

    INDEX idx_user_time (user_id, occurred_at),
    INDEX idx_model_time (model, occurred_at),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent LLM 成本明细';

-- 日汇总表 (定时任务每小时生成)
CREATE TABLE agent_cost_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL,
    total_cost DECIMAL(10,2) NOT NULL,
    total_calls INT NOT NULL,
    total_input_tokens BIGINT NOT NULL,
    total_output_tokens BIGINT NOT NULL,
    cost_by_model JSON COMMENT '{"deepseek-v3":18.2, "deepseek-r1":3.8}',
    cost_by_phase JSON COMMENT '{"generate":15.0, "reflect":3.8, ...}',
    cost_by_intent JSON,
    unique_users INT NOT NULL,
    avg_cost_per_turn DECIMAL(10,4) NOT NULL,

    UNIQUE KEY uk_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 成本日汇总';
```

## 四、成本优化策略

### 4.1 Prompt 缓存(Prefix Cache)

DeepSeek-V3 支持前缀缓存:固定前缀(如 system prompt)命中缓存时,输入 token 按 0.1 折计费。

```java
// System Prompt 设计为固定前缀 (见 08-prompt-engineering)
// L1 平台级 prompt 固定不变 → 命中缓存
// L2 任务级 prompt 按意图选择 → 每意图固定
// L3 Few-shot 固定
// L4 用户输入变化 → 不命中缓存

// 节省:system prompt 约 1000 token,缓存后按 100 token 计费
// 单次节省: (1000-100) * 1 / 1M = ¥0.0009
```

### 4.2 分级模型路由

不是所有请求都需要最强模型:

```java
public String selectModel(Intent intent, Difficulty difficulty) {
    return switch (intent) {
        case HOW_TO -> difficulty == Difficulty.HARD ? "deepseek-v3" : "doubao-pro";
        case SEARCH -> "deepseek-v3";  // 搜索需要理解力
        case NAVIGATE -> "doubao-pro";  // 导航简单
        case CLARIFY -> "doubao-lite";  // 澄清用小模型
        case OUT_OF_SCOPE -> "doubao-lite";  // 拒答用小模型
    };
}
```

- 简单意图用豆包(¥0.8/M),复杂意图用 DeepSeek-V3(¥1/M)。
- 反思只在困难场景触发(默认不反思)。

### 4.3 Token 预算控制

```java
public class TokenBudgetManager {

    // 单轮 token 预算
    private static final int MAX_TOKENS_PER_TURN = 8000;

    public BudgetAllocation allocate(ContextLayers layers) {
        int remaining = MAX_TOKENS_PER_TURN;

        // L0 System Prompt (固定)
        int l0 = 1500;
        remaining -= l0;

        // L1 用户画像 (动态)
        int l1 = Math.min(500, remaining / 4);
        remaining -= l1;

        // L2 工具描述
        int l2 = Math.min(500, remaining / 4);
        remaining -= l2;

        // L3 检索结果
        int l3 = Math.min(3000, remaining * 3 / 4);
        remaining -= l3;

        // L4 历史对话
        int l4 = Math.min(2000, remaining);
        remaining -= l4;

        // L5 用户输入
        int l5 = remaining;  // 剩余给用户输入

        // 生成预算
        int generation = 1500;  // max_tokens

        return new BudgetAllocation(l0, l1, l2, l3, l4, l5, generation);
    }
}
```

超预算时降级(见 09-context-engineering):压缩历史 → 减少检索文档 → 跳过工具描述。

### 4.4 反思触发条件
反思用 R1(贵 8 倍),不能每次都触发:

```java
public boolean shouldReflect(TurnContext ctx) {
    // 1. 只有 SEARCH 意图才反思(用户最关心准确性)
    if (ctx.getIntent() != Intent.SEARCH) return false;

    // 2. 检索结果少(可能不准)
    if (ctx.getRetrievedDocs().size() < 3) return true;

    // 3. 用户上一轮 👎 过(当前轮需更谨慎)
    if (ctx.isPreviousTurnNegative()) return true;

    // 4. 查询含否定词或多实体(复杂查询)
    if (isComplexQuery(ctx.getQuery())) return true;

    // 5. 概率触发(10% 随机反思,用于离线评估)
    if (random.nextDouble() < 0.1) return true;

    return false;
}
```

- 默认不反思,只在上述条件触发。
- 反思率目标:≤ 15%(即 85% 的请求不触发 R1)。

### 4.5 成本优化效果

| 优化措施 | 节省比例 | 说明 |
|----------|----------|------|
| Prefix Cache | ~15% | System prompt 缓存 |
| 分级模型路由 | ~20% | 简单意图用便宜模型 |
| 反思限制触发 | ~30% | 仅 15% 请求触发 R1 |
| Token 预算控制 | ~10% | 避免超长上下文 |
| **合计** | **~50%** | 实际成本约为估算的 50% |

## 五、性能监控

### 5.1 性能 SLO 总表

| 指标 | SLO | 告警线 | 降级线 |
|------|-----|--------|--------|
| TTFB P50 | ≤ 1.5s | > 2.0s | > 3.0s |
| TTFB P95 | ≤ 2.5s | > 3.0s | > 4.0s |
| E2E P50 | ≤ 5.0s | > 6.0s | > 8.0s |
| E2E P95 | ≤ 8.0s | > 10.0s | > 12.0s |
| 向量检索 P95 | ≤ 80ms | > 100ms | > 150ms |
| Reranker P95 | ≤ 150ms | > 200ms | > 300ms(跳过) |
| LLM 生成 P95 | ≤ 3.0s | > 4.0s | > 5.0s |
| 工具调用 P95 | ≤ 200ms | > 300ms | > 500ms |

### 5.2 性能降级链

当性能不达标时,自动降级以保体验:

```
E2E 延迟 > 8s
    │
    ├─ 1. 跳过反思 (省 ~3s)
    │
    ├─ 2. 跳过 reranker (省 ~150ms, 质量略降)
    │
    ├─ 3. 减少检索文档数 (top10 → top5, 省 ~50ms)
    │
    ├─ 4. 压缩上下文 (历史 2k → 1k token, 省 ~200ms)
    │
    └─ 5. 切换更快模型 (DeepSeek-V3 → 豆包Pro, 省 ~500ms)
```

```java
@Component
public class PerformanceDegradation {

    public DegradationPlan evaluate(long elapsedMs, TurnContext ctx) {
        if (elapsedMs > 8000) {
            return DegradationPlan.builder()
                .skipReflect(true)
                .skipReranker(true)
                .reduceRetrieval(5)
                .build();
        }
        if (elapsedMs > 6000) {
            return DegradationPlan.builder()
                .skipReflect(true)
                .build();
        }
        return DegradationPlan.none();
    }
}
```

### 5.3 LLM 延迟专项监控

LLM 生成是最大延迟瓶颈(占 60%+),需专项监控:

```
┌──────────────────────────────────────────────────────────┐
│              LLM 延迟监控 (DeepSeek-V3)                    │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  延迟分布 (近 1h):                                         │
│  P50:  2.1s  ━━━━━━━━━━━━━━━━━━━━━                       │
│  P95:  3.2s  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━             │
│  P99:  5.8s  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     │
│  Max:  8.2s  (超时重试)                                    │
│                                                            │
│  按 phase 分布:                                            │
│  generate:   P95 2.8s  (主生成)                            │
│  reflect:    P95 4.5s  (反思, R1 更慢)                     │
│  intent:     P95 0.5s  (意图分类)                          │
│  rewrite:    P95 0.4s  (查询改写)                          │
│                                                            │
│  限流事件:                                                 │
│  rate_limit 触发: 3 次 (自动切换豆包)                      │
│  timeout 触发: 1 次 (重试成功)                             │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

### 5.4 容量规划

基于性能指标做容量规划:

```
当前配置:
- agent-service: 1 实例, 1.5G 内存
- 并发上限: 100 SSE 连接
- QPS: 50/s

容量预估:
- 单实例 E2E P95 = 8s, 平均处理时间 5s
- 单实例并发 = 100, 吞吐 = 100/5 = 20 QPS
- 当前峰值 QPS = 12.5 → 利用率 62%

扩容阈值:
- QPS > 16 (80%) → 扩容到 2 实例
- 并发 > 80 (80%) → 扩容
- P95 > SLO → 先降级, 再扩容

成本影响:
- 每增加 1 实例: +1.5G 内存, +¥0/月 (LLM 成本不变)
- LLM 成本与 QPS 线性, 不受实例数影响
```

## 六、周报与月报

### 6.1 成本周报

```
┌──────────────────────────────────────────────────────┐
│            Agent 成本周报 (2026-W26)                   │
├──────────────────────────────────────────────────────┤
│                                                        │
│  本周成本: ¥87.30 (预算 ¥700/周, 利用率 12.5%)         │
│  环比上周: +15% (用户增长 12%)                          │
│                                                        │
│  单次对话成本: ¥0.005 (目标 ≤0.05) ✓                    │
│                                                        │
│  成本分布:                                              │
│  按模型: V3 78% / R1 15% / 豆包 7%                      │
│  按意图: SEARCH 52% / HOW_TO 28% / 其他 20%             │
│  按阶段: generate 60% / reflect 25% / 其他 15%          │
│                                                        │
│  优化建议:                                              │
│  - 反思触发率 18% (目标 ≤15%), 可收紧条件               │
│  - OUT_OF_SCOPE 仍在用 V3, 建议降为豆包Lite             │
│                                                        │
└──────────────────────────────────────────────────────┘
```

### 6.2 性能月报

```
┌──────────────────────────────────────────────────────┐
│            Agent 性能月报 (2026-06)                    │
├──────────────────────────────────────────────────────┤
│                                                        │
│  SLO 达成率:                                           │
│  TTFB P95: 2.3s (SLO 2.5s) ✓ 达标率 96%               │
│  E2E P95:  6.8s (SLO 8.0s) ✓ 达标率 98%               │
│  错误率:   0.8% (SLO ≤2%)  ✓ 达标率 100%              │
│                                                        │
│  性能趋势:                                              │
│  TTFB: 2.5s → 2.3s (-8%, prompt 精简见效)             │
│  E2E:  7.2s → 6.8s (-6%, 反思触发率降低)              │
│                                                        │
│  降级触发:                                              │
│  跳过反思: 142 次 (12%)                                │
│  跳过重排: 23 次 (2%)                                  │
│  模型降级: 8 次 (0.7%)                                 │
│                                                        │
│  容量:                                                  │
│  峰值 QPS: 15.2 (容量 50, 利用率 30%)                   │
│  峰值并发: 38 (容量 100, 利用率 38%)                    │
│  无需扩容                                               │
│                                                        │
└──────────────────────────────────────────────────────┘
```

## 七、决策记录

### ADR-186: 成本与性能监控 - 实时追踪 + 降级链 + 容量规划
- **背景**:Agent 成本与性能直接关联,需持续监控和优化。
- **决策**:
  - 成本:实时 Redis 累加 + Prometheus 指标 + MySQL 明细,三层采集。
  - 优化:Prefix Cache + 分级模型路由 + 反思限制触发 + Token 预算,综合节省 ~50%。
  - 性能:SLO 8 项指标,超阈值自动降级(跳反思→跳重排→减检索→压上下文→换模型)。
  - 容量:基于 QPS/并发利用率扩容,LLM 成本与实例数无关。
  - 报告:成本周报 + 性能月报,定期回顾。
- **理由**:LLM 应用成本可控性是核心竞争力;性能降级链保障体验底线。
- **状态**:采纳
