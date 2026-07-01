# A/B 测试框架

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[user-satisfaction-metrics.md](./user-satisfaction-metrics.md)

## 一、为什么 Agent 需要 A/B 测试

离线黄金集只能证明「没退化」,不能证明「更好」。真正的优化验证必须在线上进行:

- 离线集覆盖不了真实长尾 query。
- 离线指标(Recall/NDCG)与用户体验的相关性不完美。
- 有些改动(如 prompt 语气调整)无法用离线指标衡量。

A/B 测试是**验证优化是否真实有效**的唯一可靠方法。参考字节跳动 A/B 测试文化:任何产品决策都需 A/B 数据支撑。

## 二、实验设计

### 2.1 实验生命周期

```
┌──────────────────────────────────────────────────────────┐
│                    A/B 实验生命周期                        │
└──────────────────────────────────────────────────────────┘

 1. 假设形成
    "改用 HyDE 查询改写后,SEARCH 意图的采纳率提升 ≥3%"
    ↓
 2. 样本量计算
    基于基线采纳率、MDE、显著性水平计算所需样本量
    ↓
 3. 流量分桶
    按用户 ID 哈希分桶,控制组 vs 实验组
    ↓
 4. 灰度上线
    实验组 10% 流量起步,观察 24h 无异常
    ↓
 5. 正式实验
    扩至目标流量比例,运行至样本量达标
    ↓
 6. 统计检验
    t-test / Mann-Whitney U / 卡方检验
    ↓
 7. 决策
    显著正向 → 全量
    显著负向 → 回滚
    不显著 → 延长 or 放弃
    ↓
 8. 归档
    实验结果写入 agent_experiments 表
```

### 2.2 假设规范
每个实验必须有明确的、可证伪的假设:

**好的假设**:
> "将系统提示词中的 Few-shot 从 2 个增加到 4 个后,CLARIFY 意图的澄清准确率提升 ≥5%,且不影响其他意图的响应延迟。"

**差的假设**:
> "优化 prompt 让 agent 更好。" (不可证伪、无指标)

假设模板:
```
[改动描述] 后,[核心指标] [变化方向] ≥[MDE],
且 [护栏指标] 不退化。
```

## 三、流量分桶

### 3.1 分桶策略

```java
// 分桶逻辑:基于用户 ID 的一致性哈希
public String getExperimentBucket(String userId, String experimentId) {
    String key = experimentId + ":" + userId;
    int hash = Math.abs(key.hashCode()) % 100;  // 0-99
    if (hash < config.getTreatmentPct()) {
        return "treatment";
    }
    return "control";
}
```

### 3.2 分桶原则

| 原则 | 说明 |
|------|------|
| 一致性 | 同一用户在实验期间始终在同一桶,不可漂移 |
| 互斥 | 同一用户同时只能参与一个实验(避免干扰) |
| 可重启 | 实验暂停后重启,用户分桶不变 |
| 白名单 | 内部账号可强制分到 treatment 提前体验 |

### 3.3 流量分配

```
总流量 100%
├── 实验互斥层 (80%)
│   ├── 当前实验: treatment 10% / control 70%
│   └── 剩余可分配: 0%
├── 保留区 (15%) — 不参与任何实验,作为长期基线
└── 白名单区 (5%) — 内部测试
```

- **保留区**:始终有 15% 用户不参与实验,用于观察「无实验状态」的基线,检测实验污染。
- **单实验限制**:MVP 阶段同时只跑一个实验,避免多实验交互干扰。

## 四、指标体系

### 4.1 指标分层

| 层级 | 指标 | 用途 |
|------|------|------|
| 核心指标(OEC) | 采纳率 | 判断实验成败的主指标 |
| 护栏指标 | 重试率、首 token 延迟、错误率 | 防止核心指标提升但体验退化 |
| 辅助指标 | 引用点击率、会话长度、澄清率 | 解释核心指标变化的原因 |

### 4.2 核心指标:采纳率 (Adoption Rate)

```
采纳率 = 被采纳的 assistant 回答数 / 总 assistant 回答数
```

**「采纳」定义**(任一即可):
- 用户点击了回答中的引用卡片
- 用户对回答点了 👍
- 用户在该会话中未重试(未在 60s 内发下一条消息且未中断)

**为何选采纳率**:综合反映回答的相关性、准确性、实用性,是用户体验的代理指标。

### 4.3 护栏指标

| 指标 | 定义 | 退化阈值 |
|------|------|----------|
| 重试率 | 用户 60s 内重发的比例 | 上升 >2% 则回滚 |
| 首 token 延迟 P95 | 从发送到首个 token 的时间 | 上升 >500ms 则回滚 |
| 错误率 | SSE error 事件比例 | 上升 >1% 则回滚 |
| 中断率 | 用户主动停止生成的比例 | 上升 >3% 则回滚 |

**护栏规则**:核心指标提升但任一护栏指标超阈值,仍判定实验失败。

### 4.4 辅助指标(诊断用)
- **引用点击率**:回答中引用被点击的比例。上升说明引用更精准。
- **会话长度**:平均轮数。上升可能说明回答质量好(深入对话),也可能说明单次回答不完整(需追问)。
- **澄清率**:触发澄清的比例。上升不一定坏(该澄清的更敢澄清了),需结合采纳率看。

## 五、统计显著性检验

### 5.1 样本量计算

实验前必须计算所需样本量,避免「跑不够就下结论」。

```
# 二项指标(如采纳率)样本量公式
n = (Z_α/2 + Z_β)² × [p1(1-p1) + p2(1-p2)] / (p1 - p2)²

参数:
- α = 0.05 (显著性水平, 双侧 Z=1.96)
- β = 0.20 (1-power=0.2, power=0.8, Z=0.84)
- p1 = 基线采纳率 (如 0.45)
- p2 = 期望采纳率 (如 0.48, MDE=3%)
```

**示例**:基线 45%,期望 48%,所需单组样本量 ≈ 5300。按日活 2000 估算,需运行约 6 天。

### 5.2 检验方法选择

| 指标类型 | 分布 | 检验方法 |
|----------|------|----------|
| 二项(采纳/未采纳) | 二项分布 | 卡方检验 或 Z 检验 |
| 连续(延迟、会话长度) | 近似正态 | Welch's t-test(不等方差) |
| 连续(偏态分布) | 非正态 | Mann-Whitney U 检验 |
| 计数(引用点击次数) | 泊松 | 泊松检验或置换检验 |

### 5.3 显著性判定

```
p-value < 0.05  →  显著
p-value < 0.01  →  强显著
p-value ≥ 0.05  →  不显著
```

**注意事项**:
- **多重比较校正**:同时检验多个指标时,用 Bonferroni 校正(α/m)防止假阳性。
- **效果量**:p-value 显著但效果量极小(如采纳率提升 0.1%)无实际意义,需同时看 MDE。
- **Peeking 问题**:不要每天看 p-value 提前下结论,必须跑到预定样本量。

### 5.4 置信区间
报告时必须给置信区间,不只给点估计:
> "采纳率提升 3.2% (95% CI: 1.1% - 5.3%, p=0.003)"

## 六、实验配置与执行

### 6.1 实验配置表(agent_experiments)

```sql
CREATE TABLE agent_experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id VARCHAR(64) NOT NULL UNIQUE COMMENT '如 exp-2026-007',
    name VARCHAR(128) NOT NULL,
    hypothesis TEXT NOT NULL COMMENT '可证伪假设',

    -- 变更内容
    change_type ENUM('PROMPT','RETRIEVAL','RERANK','LLM_MODEL','TOOL','OTHER') NOT NULL,
    change_detail_json JSON NOT NULL COMMENT '具体改了什么',

    -- 流量
    treatment_pct INT NOT NULL COMMENT '实验组流量百分比',
    control_pct INT NOT NULL,
    started_at DATETIME NOT NULL,
    planned_end_at DATETIME NOT NULL,

    -- 指标
    primary_metric VARCHAR(64) NOT NULL,
    guardrail_metrics_json JSON,
    mde DECIMAL(6,4) COMMENT '最小可检测效应',

    -- 状态
    status ENUM('DRAFT','RUNNING','PAUSED','COMPLETED','ROLLED_BACK') DEFAULT 'DRAFT',
    decision ENUM('SHIP','ROLLBACK','INCONCLUSIVE') COMMENT '最终决策',
    decision_reason TEXT,

    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_status (status),
    INDEX idx_dates (started_at, planned_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A/B 实验记录';
```

### 6.2 分桶记录表(agent_experiment_assignments)

```sql
CREATE TABLE agent_experiment_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    bucket ENUM('treatment','control') NOT NULL,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_exp_user (experiment_id, user_id),
    INDEX idx_exp_bucket (experiment_id, bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验分桶记录';
```

### 6.3 指标采集
实验期间,每条 agent 回答记录中带实验信息:

```sql
-- agent_turns 表新增字段 (见 12-database-schema.md)
ALTER TABLE agent_turns ADD COLUMN experiment_id VARCHAR(64) COMMENT 'A/B 实验 ID';
ALTER TABLE agent_turns ADD COLUMN experiment_bucket ENUM('treatment','control') COMMENT '实验分桶';
ALTER TABLE agent_turns ADD COLUMN adopted BOOLEAN COMMENT '是否被采纳';
```

采纳判定定时任务(每分钟):
```java
// 采纳判定逻辑
@Scheduled(fixedRate = 60000)
public void evaluateAdoption() {
    // 1. 查询 60s 前的 assistant 回答(尚未判定采纳)
    // 2. 对每条回答:
    //    - 若用户点了 👍 → adopted = true
    //    - 若用户点了引用 → adopted = true
    //    - 若 60s 内用户重发消息 → adopted = false (重试)
    //    - 若 60s 内用户中断 → adopted = false
    //    - 若 60s 内用户无操作 → adopted = true (默认采纳)
    // 3. 批量更新 agent_turns.adopted
}
```

## 七、实验看板

Grafana 看板实时展示实验指标对比:

```
┌─────────────────────────────────────────────────────────┐
│              Experiment: exp-2026-007                    │
│         HyDE 查询改写对 SEARCH 采纳率的影响               │
├─────────────────────────────────────────────────────────┤
│ 状态: RUNNING | Day 3/6 | 流量: 10% treatment           │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  核心指标: 采纳率                                         │
│  ┌─────────────────────────────────────┐                │
│  │ control:  45.2% (n=480)  ━━━━━━━━━ │                │
│  │ treatment: 48.1% (n=52)  ━━━━━━━━━━│ +2.9%           │
│  │ p=0.34 (不显著, 样本不足)            │                │
│  └─────────────────────────────────────┘                │
│                                                          │
│  护栏指标:                                                │
│  ┌─────────────────────────────────────┐                │
│  │ 首 token P95:                       │                │
│  │   control: 1.2s  treatment: 1.3s  ✓ │ +83ms (可接受) │
│  │ 重试率:                              │                │
│  │   control: 12%  treatment: 11%   ✓  │ -1%            │
│  └─────────────────────────────────────┘                │
│                                                          │
│  预计达标: Day 6 (还需 ~4400 样本)                        │
└─────────────────────────────────────────────────────────┘
```

## 八、常见陷阱

### 8.1 Peeking(偷看)
- **问题**:每天看 p-value,显著就停,不显著就继续。
- **后果**:大幅增加假阳性率。
- **应对**:预先计算样本量,跑到样本量再判定;或用序贯检验(sequential testing)允许提前停止。

### 8.2 Novelty Effect(新奇效应)
- **问题**:新功能上线初期用户好奇导致指标虚高,随后回落。
- **应对**:实验至少跑 1 个完整周期(含工作日和周末),不只看前 2 天。

### 8.3 Simpson's Paradox(辛普森悖论)
- **问题**:整体看实验组更好,但分意图看每个切片实验组都更差(因流量分配不均)。
- **应对**:分桶时确保各切片均匀分配;分析时必须看分切片结果。

### 8.4 交互效应
- **问题**:同时跑多个实验,A 实验的效果受 B 实验影响。
- **应对**:MVP 阶段同时只跑一个实验;进阶阶段用正交分层(layering)支持多实验并行。

## 九、决策记录

### ADR-173: A/B 测试框架与统计规范
- **背景**:Agent 优化需要在线验证,但不能凭直觉判断效果。
- **决策**:
  - 核心指标:采纳率(综合反映回答质量)。
  - 护栏指标:重试率/延迟/错误率/中断率,任一超阈值即回滚。
  - 样本量:预先计算,α=0.05, power=0.8, MDE=3%。
  - 检验:二项用卡方,连续用 Welch's t-test,偏态用 Mann-Whitney U。
  - 流量:一致性哈希分桶,单实验互斥,15% 保留区作长期基线。
  - 周期:至少 6 天(含周末),防 novelty effect。
- **理由**:参考字节跳动 A/B 实践,统计严谨性是数据驱动决策的前提。
- **权衡**:单实验互斥导致实验吞吐量低,但 MVP 阶段可接受;进阶阶段引入正交分层。
- **状态**:采纳
