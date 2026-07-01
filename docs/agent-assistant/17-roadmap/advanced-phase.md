# 进阶阶段详细规划

> 状态: 草稿
> 最后更新: 2026-06-30
> 阶段: Phase 2 进阶
> 周期: 4-6 周
> 前置: MVP 已上线

## 一、进阶目标

**一句话**:在 MVP 基础上提升质量(重排+反思+压缩)、建立评估体系(黄金集+LLM-Judge+A/B)、完善安全与可观测性。

**验收标准**:
1. Reranker 上线,NDCG@10 ≥ 0.75。
2. 反思自校验上线,困难查询采纳率提升 ≥3%。
3. 上下文压缩上线,50 轮对话不超 token 预算。
4. 长期记忆上线,记住用户偏好。
5. 黄金测试集 200 条,离线回归 CI 门禁。
6. LLM-as-Judge 评估流水线。
7. A/B 测试框架,首个实验上线。
8. 完整安全护栏(内容安全 LLM 分类 + 审计)。
9. 5 个 Grafana 看板 + 告警规则。
10. 用户满意度采集(👍👎 + CSAT)。

## 二、周计划

### Week 1: Reranker + 检索优化

**任务**:
- [ ] 部署 bge-reranker-v2-m3(TEI 镜像,复用 BGE 服务或独立)
- [ ] 编写 RerankerService:
  - 输入:RRF 融合后的 top-10 文档
  - 调用 reranker 对 query-doc pair 打分
  - 按分数重排,输出 top-5
- [ ] 查询扩展(见 06-retrieval/query-expansion.md):
  - 同义词扩展(词表)
  - HyDE(用 LLM 生成假设性文档)
- [ ] 元数据过滤(见 06-retrieval/metadata-filtering.md):
  - 按学校/分类/帖子类型过滤
- [ ] 检索质量评估脚本(见 13-retrieval-quality-metrics.md):
  - Recall@10 / NDCG@10 / MRR / Hit Rate@3
  - 对比基线生成报告

**交付物**:检索质量提升,NDCG@10 ≥ 0.75。

### Week 2: 反思自校验

**任务**:
- [ ] 编写 ReflectionService(见 07-reflection-self-validation.md):
  - 触发条件(SEARCH 意图 + 检索结果少 + 上轮👎 + 复杂查询)
  - 调用 DeepSeek-R1 反思
  - R1 思考过程解析(`<think>` 标签)
- [ ] 反思结果处理:
  - R1 认为正确 → 直接输出 V3 回答
  - R1 发现问题 → V3 重新生成(带 R1 反馈)
  - R1 确认无法回答 → 返回「无法确认」
- [ ] 反思成本控制:
  - 触发率 ≤ 15%
  - 单会话 R1 调用 ≤ 1 次
- [ ] 幻觉检测(见 14-output-guardrails.md):
  - NLI 方法,逐论断判断
  - 幻觉率 > 20% 拦截

**交付物**:困难查询质量提升,采纳率提升 ≥3%。

### Week 3: 上下文压缩 + 长期记忆

**任务**:
- [ ] 上下文压缩(见 09-context-compression.md):
  - Rolling Summary:旧消息摘要
  - Slot Freezing:实体槽位冻结
  - Pin Message:用户可固定重要消息
  - 三合一 LLM 调用(单次调用完成三种压缩)
- [ ] 上下文窗口管理(见 09-context-window-management.md):
  - 5 层分层装载(L0-L5)
  - 8K token 预算动态分配
  - ContextAssembler 组件
- [ ] 长期记忆(见 09-long-term-memory.md):
  - user_memory + user_memory_evidence 表
  - 显式抽取(LLM 从对话中提取偏好)
  - 隐式证据(用户行为累积)
  - 装载 Top-K=5,周衰减 0.1
  - 用户可查看/删除(透明性)

**交付物**:50 轮对话不超 token 预算,用户偏好被记忆。

### Week 4: 评估体系

**任务**:
- [ ] 黄金测试集构建(见 13-golden-test-set.md):
  - 从日志采集 + 人工构造,200 条
  - 双人标注 + 仲裁,Cohen's Kappa ≥ 0.6
  - 意图分布:HOW_TO 30% / SEARCH 35% / NAVIGATE 15% / CLARIFY 10% / OUT_OF_SCOPE 10%
- [ ] LLM-as-Judge 评估(见 13-llm-as-judge.md):
  - 6 维度 Rubric(事实/相关/完整/引用/语气/安全)
  - 评估者用豆包 Pro(日常)/ GPT-4o(发版前)
  - 与人工标注校准,Pearson ≥ 0.7
- [ ] CI/CD 门禁(见 13-e2e-quality-evaluation.md):
  - PR 触发检索回归(~3min)
  - 发版触发生成回归(~15min)
  - 退化 >5% 阻断合并
- [ ] E2E 评估流水线:
  - 全链路评估(意图/检索/工具/生成/引用/延迟)
  - 红队测试(50 条攻击样本)

**交付物**:评估体系就位,PR 有质量门禁。

### Week 5: A/B 测试 + 满意度采集

**任务**:
- [ ] A/B 测试框架(见 13-ab-testing-framework.md):
  - agent_experiments + agent_experiment_assignments 表
  - 一致性哈希分桶
  - 采纳率判定定时任务
  - 统计检验(卡方/t-test/Mann-Whitney U)
  - Grafana 实验看板
- [ ] 首个 A/B 实验:
  - 假设:HyDE 查询改写提升 SEARCH 采纳率 ≥3%
  - 10% 灰度,6 天周期
- [ ] 用户满意度采集(见 13-user-satisfaction-metrics.md):
  - 👍👎 按钮(每条回答)
  - 👎 必选理由(8 类)
  - CSAT 弹窗(会话结束,抽样 20%)
  - 隐式信号采集(采纳/重试/中断/引用点击)
  - 满意度周报

**交付物**:A/B 框架就位,首个实验上线,满意度信号采集。

### Week 6: 完整安全 + 可观测性

**任务**:
- [ ] 内容安全(见 14-content-safety.md):
  - 敏感词库(AC 自动机,5000+ 词)
  - LLM 语义分类(豆包 Lite)
  - 词库热更新(管理后台 + Redis Pub/Sub)
  - 人工审核队列
- [ ] 安全审计(见 14-audit-and-compliance.md):
  - agent_audit_logs 表(加密存储)
  - agent_security_events 表
  - P0-P3 分级告警
  - 事故响应流程
- [ ] 5 个 Grafana 看板(见 15-dashboards-and-alerts.md):
  - 总览 / 延迟分析 / 质量效果 / 成本 / 安全
- [ ] 告警规则(见 15-dashboards-and-alerts.md):
  - Critical:服务宕机 / 成本超预算 / P0 安全
  - Warning:TTFB/E2E 超 SLO / 错误率高 / 工具成功率低
  - AlertManager + 钉钉 Webhook
- [ ] 成本监控(见 15-cost-and-performance-monitoring.md):
  - 实时成本追踪(Redis + Prometheus)
  - 成本看板
  - 日预算熔断

**交付物**:完整安全 + 可观测性,进阶阶段可上线。

## 三、进阶验收清单

### 质量验收
- [ ] NDCG@10 ≥ 0.75
- [ ] Hit Rate@3 ≥ 0.90
- [ ] Citation Accuracy ≥ 0.90
- [ ] Groundedness ≥ 0.85
- [ ] LLM-Judge Overall ≥ 3.8/5
- [ ] 意图准确率 ≥ 90%
- [ ] 工具成功率 ≥ 95%

### 体验验收
- [ ] TTFB P95 ≤ 2.5s
- [ ] E2E P95 ≤ 8s
- [ ] 采纳率 ≥ 60%
- [ ] 重试率 ≤ 12%
- [ ] 中断率 ≤ 10%
- [ ] 👍率 ≥ 75%

### 安全验收
- [ ] Prompt 注入三层检测(规则+启发式+LLM)
- [ ] 内容安全三层检测(词库+规则+LLM)
- [ ] 幻觉检测(NLI)
- [ ] 安全事件分级告警
- [ ] 审计日志 90 天留存

### 可观测验收
- [ ] 5 个 Grafana 看板可访问
- [ ] 告警规则生效(Critical/Warning)
- [ ] traceId 全链路贯穿
- [ ] 成本看板实时更新
- [ ] 满意度周报自动生成

### 评估验收
- [ ] 黄金集 200 条
- [ ] CI 门禁生效(PR 退化 >5% 阻断)
- [ ] LLM-Judge 与人工 Pearson ≥ 0.7
- [ ] A/B 框架可用,首个实验上线
- [ ] 红队测试通过率 100%

## 四、进阶技术债务

| 债务 | 简化方式 | 偿还 |
|------|----------|------|
| 意图仍用 LLM | 延迟 +500ms | 远期(小模型/缓存) |
| 单实例 | 无高可用 | 远期(多实例+LB) |
| 无多 Agent | 单 Agent | 远期 |
| PG-Vector | 单机向量库 | 远期(Milvus) |
| 无 fine-tune | 通用模型 | 远期 |

## 五、决策记录

### ADR-195: 进阶阶段 - 质量+评估+安全+可观测
- **背景**:MVP 可用但质量一般,需系统提升。
- **决策**:进阶 6 周补齐 reranker/反思/压缩/记忆/评估/A/B/安全/监控。
- **理由**:这些是「高质量 Agent」的必要条件,缺一不可。
- **状态**:采纳
