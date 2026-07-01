# 15 - 可观测性

> 状态: 草稿
> 最后更新: 2026-06-30
> 适用范围: agent-service 全链路可观测

## 一、本目录定位

Agent 系统的复杂度远超传统 CRUD 服务:一次用户请求可能经过意图识别 → 查询改写 → 向量检索 → 关键词检索 → RRF 融合 → 重排 → 工具调用 → LLM 生成 → 引用验证 → 输出护栏等 10+ 个阶段,涉及 MySQL/PostgreSQL/Redis/BGE/DeepSeek 等多个外部依赖。

没有完善的可观测性,任何一环出问题都是黑盒。本目录定义 agent-service 的 Metrics + Logging + Tracing + Dashboard 四位一体可观测体系。

## 二、可观测性三支柱

```
┌──────────────────────────────────────────────────────────┐
│                可观测性三支柱 (Metrics/Logs/Traces)        │
├──────────────────────────────────────────────────────────┤
│                                                            │
│   Metrics (指标)           Logs (日志)       Traces (追踪) │
│   ┌──────────┐            ┌──────────┐     ┌──────────┐  │
│   │ 聚合数值  │            │ 事件记录  │     │ 链路详情  │  │
│   │ 延迟/QPS  │            │ 错误/调试  │     │ 跨服务   │  │
│   │ 错误率    │            │ 安全事件  │     │ 耗时分解  │  │
│   └─────┬────┘            └─────┬────┘     └─────┬────┘  │
│         │                       │                │        │
│         └───────────┬───────────┘                │        │
│                     │                            │        │
│              ┌──────▼──────┐              ┌──────▼──────┐ │
│              │ Prometheus  │              │   Tempo     │ │
│              │ (指标存储)   │              │ (追踪存储)  │ │
│              └──────┬──────┘              └──────┬──────┘ │
│                     │                            │        │
│                     └────────────┬───────────────┘        │
│                          ┌───────▼───────┐                │
│                          │    Grafana    │                │
│                          │  (统一看板)    │                │
│                          └───────────────┘                │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## 三、文档索引

| 文档 | 主题 | 核心问题 | ADR |
|------|------|----------|-----|
| [metrics-system.md](./metrics-system.md) | 指标体系 | 采集什么指标,如何命名 | ADR-182 |
| [logging-and-tracing.md](./logging-and-tracing.md) | 日志与链路追踪 | 出了问题如何定位 | ADR-183 |
| [dashboards-and-alerts.md](./dashboards-and-alerts.md) | 看板与告警 | 如何看到、如何被告知 | ADR-184 |
| [cost-and-performance-monitoring.md](./cost-and-performance-monitoring.md) | 成本与性能监控 | LLM 成本和延迟如何监控 | ADR-185 |

## 四、可观测性设计原则

### 原则 1: RED 方法论 + USE 方法论
- **RED**(服务视角):Rate(请求率)、Errors(错误率)、Duration(延迟)。
- **USE**(资源视角):Utilization(利用率)、Saturation(饱和度)、Errors(错误)。
- Agent 服务以 RED 为主(关注用户体验),USE 为辅(关注资源健康)。

### 原则 2: 黄金信号
Google SRE 黄金信号,每个核心组件都需采集:
1. **延迟**(Latency):P50/P95/P99。
2. **流量**(Traffic):QPS/并发数。
3. **错误**(Errors):错误率/错误类型分布。
4. **饱和度**(Saturation):连接池/线程池/队列长度。

### 原则 3: traceId 贯穿全链路
- 网关生成 traceId,透传到 agent-service → Feign → 下游服务。
- 日志、指标、追踪通过 traceId 关联,实现「一点定位」。
- 使用 OpenTelemetry 自动注入(已在 docker-compose 配置 OTel agent)。

### 原则 4: 告警可行动
- 每条告警必须有明确的处理动作(SOP)。
- 不告警 = 健康;告警 = 需人工介入。
- 避免「狼来了」:告警阈值合理,分级(Warning/Critical)。

### 原则 5: 成本可见
- Agent 的特殊性:LLM 调用有真金白银成本。
- 成本指标(¥/天、¥/用户、¥/意图)与性能指标同等重要。
- 成本异常告警等同错误率告警。

## 五、与上游文档的关系

- **13-evaluation/**:评估指标(Recall/NDCG/满意度)部分来自在线采集,本目录定义采集方式。
- **14-safety-and-guardrails/**:安全事件告警在 dashboards-and-alerts 中配置。
- **12-backend-microservice/**:OTel agent 和 Prometheus 配置在 12-docker-integration 中定义。
- 项目已有的 Prometheus + Grafana + Tempo 监控栈(见 README.md 技术栈),agent-service 复用并扩展。

## 六、与项目现有监控的关系

CampusShare 已部署 Prometheus + Grafana + Tempo(见 docker-compose.yml),含 JVM/CPU/P95 延迟/Top10 慢接口等看板。agent-service 的可观测性在此基础上扩展:

| 已有监控 | agent-service 扩展 |
|----------|---------------------|
| JVM 内存/GC | + LLM 调用延迟/成本 |
| HTTP P95 延迟 | + SSE 首 token 延迟 |
| MySQL 慢查询 | + PostgreSQL 向量检索延迟 |
| Redis 命中率 | + BGE embedding 延迟 |
| 服务健康检查 | + Agent 各阶段延迟分解 |
| 通用错误率 | + 意图识别准确率/工具成功率 |

## 七、决策记录

### ADR-182: 可观测性体系 - 复用现有栈 + Agent 专属扩展
- **背景**:项目已有 Prometheus + Grafana + Tempo,agent-service 需在此基础上增加 LLM 特有指标。
- **决策**:
  - 复用现有监控栈,不引入新组件。
  - Agent 专属指标:LLM 延迟/成本、SSE 首 token、意图准确率、工具成功率、各阶段延迟分解。
  - traceId 全链路贯穿,OTel 自动注入。
  - 告警分级:Warning(钉钉) + Critical(电话/短信)。
- **理由**:复用降低运维成本;Agent 特有指标反映 LLM 应用独特性。
- **状态**:采纳
