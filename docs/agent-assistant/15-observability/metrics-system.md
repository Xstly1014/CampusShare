# 指标体系

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)

## 一、指标分层

Agent 指标分四层,从上到下反映「用户感受 → 业务效果 → 系统健康 → 资源消耗」:

```
┌──────────────────────────────────────────────────────────┐
│  L1 用户体验层  用户感受如何                                │
│  (首 token 延迟、端到端延迟、中断率、采纳率)                │
├──────────────────────────────────────────────────────────┤
│  L2 业务效果层  Agent 做得好不好                            │
│  (意图准确率、工具成功率、引用准确率、幻觉率)                │
├──────────────────────────────────────────────────────────┤
│  L3 系统健康层  各组件正常吗                                │
│  (SSE 错误率、LLM 可用性、PG/Redis/BGE 延迟)              │
├──────────────────────────────────────────────────────────┤
│  L4 资源消耗层  成本和容量                                  │
│  (LLM 成本、token 消耗、连接池、内存)                      │
└──────────────────────────────────────────────────────────┘
```

## 二、L1 用户体验指标

### 2.1 首 Token 延迟 (TTFB - Time To First Byte)

```
agent_ttfb_ms{intent, model}  -- Histogram
```

用户从发送消息到收到第一个 SSE token 的延迟。

- **SLO**:P50 ≤ 1.5s,P95 ≤ 2.5s。
- **分解**:
  - 输入护栏:~50ms
  - 意图识别:~500ms(若用 LLM 分类)
  - 查询改写:~300ms
  - 检索(向量+关键词+RRF):~150ms
  - 重排:~150ms
  - LLM 首 token:~300ms

```java
@Component
public class TtfbMetrics {
    private final Timer ttfbTimer = Timer.builder("agent_ttfb")
        .tag("service", "agent-service")
        .register(meterRegistry);

    public void record(long startNanos, String intent, String model) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        ttfbTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

### 2.2 端到端延迟

```
agent_e2e_ms{intent, model, has_tool_call}  -- Histogram
```

从用户发送到 SSE done 事件的总延迟。

- **SLO**:P50 ≤ 5s,P95 ≤ 8s。
- **标签**:是否触发工具调用(工具调用显著增加延迟)。

### 2.3 中断率

```
agent_interrupt_rate  -- Gauge
agent_interrupt_count{intent, interrupt_stage}  -- Counter
```

用户主动停止生成的比例。

- **SLO**:≤ 10%。
- **interrupt_stage**:在生成进度 <20% / 20-80% / >80% 时中断,用于判断中断原因。

### 2.4 采纳率

```
agent_adoption_rate  -- Gauge (每日计算)
agent_adoption_total{intent}  -- Counter
```

(定义见 13-evaluation/user-satisfaction-metrics.md)

## 三、L2 业务效果指标

### 3.1 意图识别准确率

```
agent_intent_accuracy  -- Gauge
agent_intent_total{predicted_intent, actual_intent}  -- Counter
```

- **采集方式**:离线黄金集回归 + 在线抽样人工标注(每日 100 条)。
- **SLO**:≥ 90%。
- **混淆矩阵**:通过 `predicted_intent` × `actual_intent` 标签构建。

### 3.2 工具调用成功率

```
agent_tool_call_total{tool_name, status}  -- Counter
agent_tool_call_duration_ms{tool_name}  -- Histogram
```

- **status**:success / timeout / error / fallback。
- **SLO**:成功率 ≥ 95%。
- **per-tool SLO**:
  - search_posts:成功率 ≥ 95%,P95 ≤ 200ms
  - search_knowledge:成功率 ≥ 95%,P95 ≤ 150ms
  - get_post_detail:成功率 ≥ 98%,P95 ≤ 100ms

### 3.3 引用准确率

```
agent_citation_accuracy  -- Gauge (离线评估)
agent_citation_count  -- Counter
agent_citation_error_count{error_type}  -- Counter
```

- **error_type**:OUT_OF_RANGE(越界)、MISMATCH(不匹配)、MISSING(应有却无)。
- **SLO**:≥ 90%(离线)。

### 3.4 幻觉率

```
agent_hallucination_rate  -- Gauge (离线评估 + 在线抽样)
agent_hallucination_claims_total{verdict}  -- Counter
```

- **verdict**:ENTAILMENT / NEUTRAL / CONTRADICTION。
- **SLO**:幻觉率(NEUTRAL + CONTRADICTION) ≤ 15%。

## 四、L3 系统健康指标

### 4.1 SSE 连接指标

```
agent_sse_connections_active  -- Gauge (当前活跃 SSE 连接数)
agent_sse_connections_total  -- Counter (累计创建)
agent_sse_errors_total{error_type}  -- Counter
agent_sse_duration_ms  -- Histogram (连接持续时间)
```

- **error_type**:timeout / client_disconnect / server_error / upstream_error。
- **SLO**:错误率 ≤ 2%。

### 4.2 LLM 可用性

```
agent_llm_call_total{model, status}  -- Counter
agent_llm_call_duration_ms{model, phase}  -- Histogram
agent_llm_retry_total{model}  -- Counter
```

- **status**:success / rate_limit / timeout / error。
- **phase**:intent / rewrite / generate / reflect / judge。
- **SLO**:
  - 成功率 ≥ 99%(含重试后)。
  - rate_limit 触发时自动切换降级模型。

### 4.3 依赖服务延迟

```
agent_dependency_duration_ms{dependency, operation}  -- Histogram
agent_dependency_errors_total{dependency, error_type}  -- Counter
```

- **dependency**:mysql / postgresql / redis / bge_embedding / bge_reranker / deepseek / doubao / feign_post / feign_user。
- **operation**:如 postgresql=vector_search、redis=session_get、bge=embed。
- **SLO(各依赖 P95)**:
  - MySQL: ≤ 50ms
  - PostgreSQL 向量检索: ≤ 80ms
  - Redis: ≤ 5ms
  - BGE embedding: ≤ 100ms
  - BGE reranker: ≤ 150ms
  - DeepSeek API: ≤ 3000ms(生成)
  - Feign 调用: ≤ 200ms

### 4.4 各阶段延迟分解

```
agent_phase_duration_ms{phase}  -- Histogram
```

- **phase**:input_guardrail / intent_classify / query_rewrite / vector_search / keyword_search / rrf_fusion / rerank / tool_call / llm_generate / output_guardrail / citation_process。
- **用于**:瓶颈定位(见 dashboards-and-alerts 的延迟分解看板)。

```java
// 阶段计时器
public class PhaseTimer {
    private final Map<String, Long> starts = new HashMap<>();

    public void start(String phase) {
        starts.put(phase, System.nanoTime());
    }

    public void end(String phase) {
        Long start = starts.get(phase);
        if (start != null) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            meterRegistry.timer("agent_phase_duration", "phase", phase)
                .record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
```

## 五、L4 资源消耗指标

### 5.1 LLM 成本

```
agent_llm_cost_yuan_total{model}  -- Counter (累计成本, 元)
agent_llm_cost_yuan_daily  -- Gauge (今日成本)
agent_llm_tokens_total{model, direction}  -- Counter
```

- **direction**:input / output。
- **SLO**:日均成本 ≤ ¥100,单次对话 ≤ ¥0.05。
- **告警**:日成本达预算 90% Warning,100% Critical。

### 5.2 Token 消耗

```
agent_token_usage_total{phase, model, direction}  -- Counter
agent_token_budget_usage_ratio  -- Gauge (单轮 token 预算使用率)
```

- **phase**:system_prompt / context / retrieved_docs / user_input / generation。
- **用于**:优化上下文工程(见 09-context-engineering),识别哪一层消耗 token 最多。

### 5.3 连接池

```
agent_hikaricp_active_connections{pool}  -- Gauge
agent_hikaricp_pending_threads{pool}  -- Gauge
agent_hikaricp_utilization{pool}  -- Gauge (利用率)
```

- **pool**:mysql-pool / pg-pool。
- **SLO**:利用率 < 80%,pending < 5。

### 5.4 JVM(复用项目已有)
- jvm_memory_used / jvm_gc_pause / jvm_threads_live,已有 Grafana 看板。

## 六、指标命名规范

遵循 Prometheus 命名约定:

```
<namespace>_<subsystem>_<name>_<unit>{<labels>}
```

- **namespace**:统一 `agent`(本服务)。
- **subsystem**:如 `llm`、`sse`、`tool`、`phase`。
- **unit**:如 `_ms`(毫秒)、`_total`(计数)、`_rate`(比率)、`_yuan`(元)。
- **labels**:用下划线分隔,如 `model`、`intent`、`phase`。

**示例**:
- `agent_llm_call_duration_ms{model="deepseek-v3",phase="generate"}`
- `agent_tool_call_total{tool_name="search_posts",status="success"}`
- `agent_sse_errors_total{error_type="timeout"}`

## 七、指标采集实现

### 7.1 Spring Boot Actuator + Micrometer

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config().commonTags(
            "service", "agent-service",
            "version", "@project.version@"
        );
    }
}
```

### 7.2 自定义业务指标

```java
@Component
@RequiredArgsConstructor
public class AgentBusinessMetrics {

    private final MeterRegistry registry;

    // 意图分类
    public void recordIntent(String predicted, String actual) {
        registry.counter("agent_intent_total",
            "predicted", predicted,
            "actual", actual
        ).increment();
    }

    // 工具调用
    public void recordToolCall(String toolName, String status, long durationMs) {
        registry.counter("agent_tool_call_total",
            "tool_name", toolName,
            "status", status
        ).increment();

        registry.timer("agent_tool_call_duration_ms",
            "tool_name", toolName
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    // LLM 调用
    public void recordLlmCall(String model, String phase, String status,
                              int inputTokens, int outputTokens, double cost) {
        registry.counter("agent_llm_call_total",
            "model", model, "phase", phase, "status", status
        ).increment();

        registry.counter("agent_llm_tokens_total",
            "model", model, "direction", "input"
        ).increment(inputTokens);

        registry.counter("agent_llm_tokens_total",
            "model", model, "direction", "output"
        ).increment(outputTokens);

        registry.counter("agent_llm_cost_yuan_total",
            "model", model
        ).increment(cost);
    }
}
```

### 7.3 Prometheus 抓取配置

```yaml
# prometheus.yml (增量)
scrape_configs:
  - job_name: 'agent-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['agent-service:8083']
        labels:
          service: 'agent-service'
    scrape_interval: 15s
```

## 八、指标保留策略

| 指标类型 | Prometheus 保留 | 长期存储 |
|----------|-----------------|----------|
| 原始指标(15s 粒度) | 15 天 | - |
| 5 分钟降采样 | - | 6 个月(可接入 VictoriaMetrics) |
| 1 小时降采样 | - | 2 年 |
| 成本指标(日粒度) | - | MySQL 永久 |

## 九、决策记录

### ADR-183: 指标体系 - 四层分层 + Micrometer 采集
- **背景**:Agent 指标需求复杂(含 LLM 成本、各阶段延迟),需系统化设计。
- **决策**:
  - 四层分层:L1 体验 / L2 效果 / L3 健康 / L4 资源。
  - 采集:Spring Boot Actuator + Micrometer,暴露 /actuator/prometheus。
  - 命名:遵循 Prometheus 约定,namespace=agent。
  - 核心 SLO:TTFB P95 ≤ 2.5s、E2E P95 ≤ 8s、工具成功率 ≥ 95%、日成本 ≤ ¥100。
- **理由**:分层便于定位问题层级;Micrometer 与 Spring Boot 集成成本低。
- **状态**:采纳
