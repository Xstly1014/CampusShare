# 看板与告警

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[metrics-system.md](./metrics-system.md)

## 一、看板规划

Agent-service 需 5 个 Grafana 看板:

| 看板 | 受众 | 核心内容 |
|------|------|----------|
| Agent 总览 | 全员 | 关键指标一屏全览 |
| Agent 延迟分析 | 开发 | 各阶段延迟分解、瓶颈定位 |
| Agent 质量效果 | 开发+产品 | 意图准确率、工具成功率、采纳率 |
| Agent 成本 | 运维+产品 | LLM 成本、token 消耗、预算 |
| Agent 安全 | 安全 | 安全事件、拦截率、注入趋势 |

## 二、Agent 总览看板

```
┌──────────────────────────────────────────────────────────────┐
│                   Agent Service - 总览                         │
├──────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐│
│  │  QPS        │ │ 活跃会话     │ │ TTFB P95    │ │ E2E P95  ││
│  │   12.5/s    │ │    23       │ │   1.8s ✓    │ │  5.2s ✓  ││
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘│
│                                                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐│
│  │ 错误率      │ │ 工具成功率   │ │ 日成本      │ │ 采纳率   ││
│  │  0.8% ✓    │ │  96.2% ✓   │ │  ¥23.50    │ │  58%     ││
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘│
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐│
│  │              请求量 & 错误率 (近 24h)                      ││
│  │  ▁▂▃▅▆▇█▇▆▅▃▂▁▂▃▅▆▇█▇▆▅▃▂▁  QPS                          ││
│  │  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁  错误率                        ││
│  └──────────────────────────────────────────────────────────┘│
│                                                                │
│  ┌──────────────────────┐ ┌──────────────────────────────────┐│
│  │  意图分布 (饼图)       │ │  各模型调用占比 (饼图)            ││
│  │  SEARCH 45%          │ │  DeepSeek-V3 78%                ││
│  │  HOW_TO 28%          │ │  DeepSeek-R1 15%                ││
│  │  NAVIGATE 12%        │ │  豆包 Pro 7%                    ││
│  │  CLARIFY 8%          │ │                                  ││
│  │  OUT_OF_SCOPE 7%     │ │                                  ││
│  └──────────────────────┘ └──────────────────────────────────┘│
│                                                                │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 总览看板 PromQL

```promql
# QPS
rate(agent_llm_call_total[5m])

# 活跃会话
agent_sse_connections_active

# TTFB P95
histogram_quantile(0.95, rate(agent_ttfb_ms_bucket[5m]))

# E2E P95
histogram_quantile(0.95, rate(agent_e2e_ms_bucket[5m]))

# 错误率
rate(agent_sse_errors_total[5m]) / rate(agent_sse_connections_total[5m])

# 工具成功率
1 - (rate(agent_tool_call_total{status!="success"}[5m]) / rate(agent_tool_call_total[5m]))

# 日成本
agent_llm_cost_yuan_daily

# 采纳率
agent_adoption_rate

# 意图分布
sum by (predicted) (increase(agent_intent_total[1h]))
```

## 三、Agent 延迟分析看板

### 3.1 各阶段延迟分解

```
┌──────────────────────────────────────────────────────────┐
│              Agent 各阶段延迟 (P95, 近 1h)                 │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  input_guardrail     ██ 52ms                              │
│  intent_classify    ██████ 480ms                          │
│  query_rewrite      ████ 310ms                            │
│  vector_search       █ 75ms                               │
│  keyword_search      █ 50ms                               │
│  rrf_fusion          ▏ 8ms                                │
│  rerank             ██ 145ms                              │
│  tool_call          ██ 180ms                              │
│  llm_generate    ████████████████████ 2800ms  ← 瓶颈     │
│  output_guardrail   ██ 180ms                              │
│  citation_process    ▏ 30ms                               │
│  ──────────────────────────────                           │
│  TOTAL P95        ████████████████████████ 4323ms         │
│  SLO                        8000ms  ✓                     │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

```promql
# 各阶段 P95
histogram_quantile(0.95, rate(agent_phase_duration_ms_bucket{phase="llm_generate"}[5m]))

# 按意图分组的 E2E 延迟
histogram_quantile(0.95, sum by (intent) (rate(agent_e2e_ms_bucket[5m])))
```

### 3.2 LLM 调用延迟趋势

```
┌──────────────────────────────────────────────────────────┐
│         LLM 调用延迟 (P50/P95/P99, 近 24h)                │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  P50  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 2.1s               │
│  P95  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 3.2s               │
│  P99  ╳━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 5.8s               │
│                                                            │
│  ※ P99 在 14:00 突增 → DeepSeek API 限流                  │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

### 3.3 依赖服务延迟

```
┌──────────────────────────────────────────────────────────┐
│         依赖服务延迟 (P95, 近 1h)                          │
├──────────────────────────────────────────────────────────┤
│  MySQL                35ms  ✓  (SLO 50ms)                │
│  PostgreSQL(向量)     72ms  ✓  (SLO 80ms)                │
│  Redis                 3ms  ✓  (SLO 5ms)                 │
│  BGE embedding        85ms  ✓  (SLO 100ms)               │
│  BGE reranker        140ms  ✓  (SLO 150ms)               │
│  DeepSeek-V3        2800ms  ✓  (SLO 3000ms)               │
│  Feign post-service  165ms  ✓  (SLO 200ms)               │
│  Feign user-service  120ms  ✓  (SLO 200ms)               │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## 四、Agent 质量效果看板

```
┌──────────────────────────────────────────────────────────┐
│              Agent 质量效果 (近 7 天)                      │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐            │
│  │ 意图准确率  │ │ 工具成功率  │ │ 引用准确率  │            │
│  │  91.2% ✓  │ │  96.2% ✓  │ │  90.5% ✓  │            │
│  └────────────┘ └────────────┘ └────────────┘            │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │           意图混淆矩阵 (近 7 天)                       ││
│  │                                                        ││
│  │          pred_HOW  pred_SEA  pred_NAV  pre_CLA  pre_OUT││
│  │  HOW_TO    532       12        3        2       1    ││
│  │  SEARCH     15      645        8        3       2    ││
│  │  NAVIGATE    2        5      178        1       0    ││
│  │  CLARIFY     3        2        1       89       0    ││
│  │  OUT_OF_S    1        1        0        0      67    ││
│  │                                                        ││
│  │  ※ SEARCH→HOW_TO 混淆 15 次, 需优化意图分类 prompt     ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │           用户满意度趋势 (近 30 天)                    ││
│  │                                                        ││
│  │  采纳率: ▁▂▃▂▃▄▅▄▅▆▅▆▇▆▇█▇█  58% (目标 60%)         ││
│  │  👍率:  ▃▄▃▄▅▄▅▆▅▆▇▆▇█▇█  76% (目标 75%) ✓          ││
│  │  重试率: █▇▆▇▆▅▆▅▄▅▄▃▄▃▂▃  12% (目标 ≤12%) ✓        ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## 五、Agent 成本看板

```
┌──────────────────────────────────────────────────────────┐
│                Agent 成本看板 (实时)                        │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐            │
│  │ 今日成本    │ │ 本月成本    │ │ 单次对话成本 │            │
│  │ ¥23.50     │ │ ¥456.20    │ │ ¥0.047     │            │
│  │ /¥100 (24%)│ │ /¥2000(23%)│ │ (目标≤0.05)│            │
│  └────────────┘ └────────────┘ └────────────┘            │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │         日成本趋势 (近 30 天)                          ││
│  │  ▁▂▁▂▃▂▃▄▃▄▅▄▅▆▅▆▇▆▇█▇¥23                             ││
│  │  预算线: ¥100 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─              ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
│  ┌──────────────────┐ ┌──────────────────────────────────┐│
│  │ 按模型成本占比    │ │ 按意图成本占比                     ││
│  │ V3  ¥18.20 (77%)│ │ SEARCH  ¥12.30 (52%)            ││
│  │ R1  ¥3.80  (16%)│ │ HOW_TO  ¥6.50  (28%)            ││
│  │ 豆包 ¥1.50  (6%) │ │ 其他    ¥4.70  (20%)            ││
│  └──────────────────┘ └──────────────────────────────────┘│
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │         Top 5 高成本用户 (今日)                         ││
│  │  user_8842:  ¥2.30 (123 次调用)                         ││
│  │  user_1024:  ¥1.80 (98 次)                             ││
│  │  user_3022:  ¥1.20 (65 次)                             ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## 六、Agent 安全看板

```
┌──────────────────────────────────────────────────────────┐
│                Agent 安全看板 (近 7 天)                     │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐            │
│  │ 注入尝试    │ │ 有害拦截    │ │ PII 检测    │            │
│  │   18 次    │ │   3 次     │ │   45 次    │            │
│  └────────────┘ └────────────┘ └────────────┘            │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │         注入攻击趋势 (近 7 天)                          ││
│  │  Mon ██ 3                                             ││
│  │  Tue █ 1                                              ││
│  │  Wed ████ 5                                           ││
│  │  Thu ██ 2                                             ││
│  │  Fri ██████ 8 ← 异常激增,需调查                        ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
│  ┌──────────────────────────────────────────────────────┐│
│  │         拦截内容分类                                    ││
│  │  VIOLENCE:    0                                       ││
│  │  ILLEGAL:     1                                       ││
│  │  PII_LEAK:    2                                       ││
│  │  INJECTION:   18                                      ││
│  └──────────────────────────────────────────────────────┘│
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## 七、告警规则

### 7.1 告警分级

| 级别 | 通知方式 | 响应时间 | 示例 |
|------|----------|----------|------|
| Critical | 电话 + 短信 + 钉钉 | 15 分钟 | 服务宕机、成本超预算、P0 安全事件 |
| Warning | 钉钉 + 邮件 | 1 小时 | 延迟超 SLO、错误率上升 |
| Info | 钉钉(汇总) | 不要求 | 日成本达 50% 预算 |

### 7.2 Prometheus 告警规则

```yaml
# prometheus/rules/agent-alerts.yml
groups:
  - name: agent-service
    rules:
      # === Critical ===
      - alert: AgentServiceDown
        expr: up{job="agent-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Agent 服务不可用"
          description: "agent-service 已宕机超过 1 分钟"

      - alert: AgentCostBudgetExceeded
        expr: agent_llm_cost_yuan_daily > 100
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Agent 日成本超预算 (¥100)"
          description: "当前日成本 ¥{{ $value }}"

      - alert: AgentP0SecurityEvent
        expr: increase(agent_security_events_total{severity="P0"}[5m]) > 0
        labels:
          severity: critical
        annotations:
          summary: "Agent P0 安全事件"
          description: "检测到 P0 级安全事件,需立即处理"

      # === Warning ===
      - alert: AgentTtfbHigh
        expr: histogram_quantile(0.95, rate(agent_ttfb_ms_bucket[5m])) > 2500
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent TTFB P95 超过 SLO (2.5s)"
          description: "当前 P95: {{ $value }}ms"

      - alert: AgentE2eHigh
        expr: histogram_quantile(0.95, rate(agent_e2e_ms_bucket[5m])) > 8000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent E2E P95 超过 SLO (8s)"

      - alert: AgentErrorRateHigh
        expr: rate(agent_sse_errors_total[5m]) / rate(agent_sse_connections_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent SSE 错误率超过 5%"

      - alert: AgentToolSuccessLow
        expr: 1 - (rate(agent_tool_call_total{status!="success"}[5m]) / rate(agent_tool_call_total[5m])) < 0.90
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Agent 工具成功率低于 90%"

      - alert: AgentLlmErrorHigh
        expr: rate(agent_llm_call_total{status!="success"}[5m]) / rate(agent_llm_call_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent LLM 调用错误率超过 5%"

      - alert: AgentCostBudgetWarning
        expr: agent_llm_cost_yuan_daily > 90
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent 日成本接近预算 (¥90/¥100)"

      # === Info ===
      - alert: AgentCostBudgetInfo
        expr: agent_llm_cost_yuan_daily > 50
        for: 10m
        labels:
          severity: info
        annotations:
          summary: "Agent 日成本达 50% 预算"
```

### 7.3 告警通知配置

```yaml
# alertmanager.yml
route:
  group_by: ['alertname', 'service']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'dingtalk-default'
  routes:
    - match:
        severity: critical
      receiver: 'critical-notify'
      repeat_interval: 30m
    - match:
        severity: warning
      receiver: 'dingtalk-default'

receivers:
  - name: 'critical-notify'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=CRITICAL_TOKEN'
    # 电话通知 (进阶接入)

  - name: 'dingtalk-default'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=DEFAULT_TOKEN'
```

### 7.4 告警 SOP

每条告警需配套处理 SOP:

| 告警 | SOP |
|------|-----|
| AgentServiceDown | 1. 检查容器状态 2. 查日志 3. 重启 4. 若反复,回滚版本 |
| AgentCostBudgetExceeded | 1. 查成本看板定位高成本来源 2. 临时调低限流 3. 检查是否有滥用 |
| AgentTtfbHigh | 1. 查延迟分解看板 2. 定位瓶颈阶段 3. 查对应依赖延迟 |
| AgentErrorRateHigh | 1. 查错误类型分布 2. 查日志 traceId 3. 定位根因 |
| AgentLlmErrorHigh | 1. 查 LLM API 状态 2. 检查限流 3. 切换降级模型 |

## 八、决策记录

### ADR-185: 看板与告警 - 5 看板 + 3 级告警 + SOP
- **背景**:指标采集后需可视化展示和主动告警,否则等于没采集。
- **决策**:
  - 5 个看板:总览/延迟/质量/成本/安全。
  - 告警 3 级:Critical(电话)/Warning(钉钉)/Info(汇总)。
  - 每条告警配套 SOP,确保可行动。
  - Prometheus + AlertManager + 钉钉 Webhook。
- **理由**:看板按受众分;告警分级避免疲劳;SOP 确保告警可处理。
- **状态**:采纳
