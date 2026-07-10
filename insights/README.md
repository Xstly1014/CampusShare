# Insights 索引

> 本文件夹遵循 [PROJECT_INSIGHTS.md](../../PROJECT_INSIGHTS.md) v2.0 STAR 版规范。
> 每个记录文件覆盖 STAR 四环节（Situation/Task/Action/Result），强制附 Mermaid 图表。
> 这些记录为简历和面试积累不可替代的亮点素材。

---

## 目录结构

```
insights/
├── architecture/       ← 架构决策与技术选型
├── performance/        ← 性能测试与优化记录
├── reliability/        ← 稳定性、容错与故障复盘
├── engineering/        ← 工程化质量记录
├── resume-bullets.md   ← 简历条目提炼
└── README.md           ← 本索引
```

---

## architecture/（架构决策与技术选型）

| 文件 | 类型 | 简介 |
|------|------|------|
| [2026-06-27_system-overview_整体架构.md](architecture/2026-06-27_system-overview_整体架构.md) | 系统全景图 | 整体组件图 + 核心业务数据流图 + 关键约束 + 被排除方案 + 已知风险 |
| [2026-06-29_ADR-001_微服务拆分post-service.md](architecture/2026-06-29_ADR-001_微服务拆分post-service.md) | ADR | post-service 从 user-service 拆分的决策，含三方案对比 + 决策流程图 |
| [2026-06-27_data-model_核心实体.md](architecture/2026-06-27_data-model_核心实体.md) | 数据模型 | 17 张表 ER 图 + 核心查询模式 + 5 个设计决策 + 反模式检查 + Schema 变更史 |
| [2026-06-27_api-design_网关路由与内部API.md](architecture/2026-06-27_api-design_网关路由与内部API.md) | 接口设计 | 网关认证序列图 + 外部/内部 API 列表 + 6 个设计决策 |
| [2026-06-29_component-interaction_评论点赞通知.md](architecture/2026-06-29_component-interaction_评论点赞通知.md) | 组件交互 | 跨服务通知序列图 + 通知状态机 + 同步/异步/超时/降级决策 |

---

## performance/（性能测试与优化）

> **新增接口优化记录**请写入 [`optimization-logs/records/`](../optimization-logs/records/)（接口延迟优化专项工作目录）。
> 本目录保留 3 条历史优化记录作为参考，不再新增。两个目录采用相同 STAR 格式，详见 [optimization-logs/README.md](../optimization-logs/README.md)。

### 历史优化记录（已完成）

| 文件 | 类型 | 简介 |
|------|------|------|
| [2026-06-29_optimization_接口性能SQL聚合缓存N+1.md](performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md) | 优化记录 | P95 从 16.3s 降至 239ms，SQL GROUP BY + Redis 缓存 + 修复 N+1（61次/页→4次/页） |
| [2026-06-29_optimization_JVM参数与Dockerfile修复.md](performance/2026-06-29_optimization_JVM参数与Dockerfile修复.md) | 优化记录 | QPS 从 4 提升至 204（51倍），ENTRYPOINT exec→shell 修复 + JwtParser 缓存 + 硬件适配 |
| [2026-06-29_optimization_数据库复合索引设计.md](performance/2026-06-29_optimization_数据库复合索引设计.md) | 优化记录 | P95 从 167ms 降至 70ms，EXPLAIN 分析 + 复合索引设计 + 严格 A/B 压测对照 |

### 新增接口优化记录（写入 optimization-logs/records/）

> 接口延迟优化专项的所有新记录存放在 [`optimization-logs/records/`](../optimization-logs/records/)，采用相同 STAR 格式。
> 索引见 [optimization-logs/records/README.md](../optimization-logs/records/README.md)。
> 总计划与接口清单见 [optimization-logs/plans/00-master-plan.md](../optimization-logs/plans/00-master-plan.md)。

---

## reliability/（稳定性与容错）

| 文件 | 类型 | 简介 |
|------|------|------|
| [2026-06-28_postmortem_监控体系搭建故障链.md](reliability/2026-06-28_postmortem_监控体系搭建故障链.md) | 故障复盘 | 可观测性栈搭建三连故障：Tempo 版本不兼容 + Actuator 端点未暴露 + Prometheus PromQL 语法崩溃 |
| [2026-07-02_postmortem_WebFlux阻塞调用导致RAG索引全失败.md](reliability/2026-07-02_postmortem_WebFlux阻塞调用导致RAG索引全失败.md) | 故障复盘 | WebFlux Controller 返回同步类型致 reactor 线程 .block() 抛 IllegalStateException，RAG 18 篇文档索引全失败；用 boundedElastic 包裹阻塞调用修复，含线程模型对比图 + 诊断弯路（.env 误判） |

---

## engineering/（工程化质量）

| 文件 | 类型 | 简介 |
|------|------|------|
| [observability.md](engineering/observability.md) | 可观测性设计 | 监控架构图 + 关键指标 + Grafana 面板设计 + 链路追踪 + 日志规范 |
| [testing-strategy.md](engineering/testing-strategy.md) | 测试策略 | 测试金字塔 + JMeter 压测配置 + A/B 实验设计 + 性能基线指标 |

---

## 关键数字速查

| 指标 | 优化前 | 优化后 | 提升幅度 | 来源 |
|------|--------|--------|----------|------|
| 帖子列表 P95 延迟 | 16.3s | 70ms | 降低 99.6% | 索引+缓存+JVM 三轮叠加 |
| QPS | 4 req/s | 1151 req/s | 提升 287 倍 | A/B 压测 B 组 |
| 单页 DB/网络请求数 | 61 次/页 | 4 次/页 | 减少 93%+ | N+1 修复 |
| CPU 峰值 | 持续 100% | 79% | 健康水位 | JVM 参数生效 |
| post major GC | 频繁 | 0.188 ops/s | 约 5s/次 | JVM 堆扩大 |

---

## 写作规范

- **文件命名**：`YYYY-MM-DD_类型_简短描述.md`
- **STAR 强制**：每个文件覆盖 Situation/Task/Action/Result 四环节
- **Mermaid 强制**：ADR 需决策流程图、数据模型需 ER 图、接口需序列图、系统架构需组件图
- **图表质量**：节点标签清晰（非缩写）、标注判断条件、标明超时/重试、画项目特定图非通用教科书图
