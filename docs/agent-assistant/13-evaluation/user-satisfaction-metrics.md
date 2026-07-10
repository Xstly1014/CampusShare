# 用户满意度度量

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[ab-testing-framework.md](./ab-testing-framework.md)

## 一、为什么用户满意度是终极指标

前面所有评估(检索指标、LLM-Judge、E2E)都是**代理指标**——它们假设「检索好 → 回答好 → 用户满意」,但这个传导链可能断裂:
- 检索 Recall 高,但回答太长用户没耐心看。
- LLM-Judge 分高,但语气居高临下让用户不舒服。
- 延迟达标,但用户觉得「等了 3 秒还是太慢」。

用户满意度是**终极指标**,直接反映「用户到底觉不觉得 agent 有用」。参考 DeepSeek 的用户反馈采集和字节的体验度量体系。

## 二、满意度信号体系

```
┌──────────────────────────────────────────────────────────┐
│                  用户满意度信号体系                         │
├──────────────────────────────────────────────────────────┤
│                                                            │
│   显式信号 (用户主动表达)         隐式信号 (行为推断)        │
│   ┌──────────────────┐         ┌──────────────────┐      │
│   │ 👍 / 👎 反馈      │         │ 采纳行为           │      │
│   │ 文本反馈          │         │ 重试行为           │      │
│   │ CSAT 评分         │         │ 中断行为           │      │
│   │ NPS               │         │ 会话深度           │      │
│   │ 举报              │         │ 引用点击           │      │
│   └──────────────────┘         │ 回访频率           │      │
│                                 │ 复制行为           │      │
│                                 └──────────────────┘      │
│                                                            │
│   主动采集 (5-10% 回收率)        被动采集 (100% 覆盖)       │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

**核心原则**:显式信号准确但稀疏(回收率低),隐式信号覆盖广但需推断。两者结合使用。

## 三、显式信号采集

### 3.1 👍 / 👎 反馈(即时反馈)

**交互设计**(见 11-frontend-integration):
- 每条 assistant 回答下方显示 👍 👎 两个按钮。
- 点击 👍:按钮变蓝,可选填文本反馈(可跳过)。
- 点击 👎:弹出反馈理由选择(见 3.1.1),必选一个理由。

**3.1.1 👎 反馈理由选项**

| 理由 | 代码 | 含义 |
|------|------|------|
| 回答不准确 | INACCURATE | 事实错误或答非所问 |
| 信息不完整 | INCOMPLETE | 没有完全解决我的问题 |
| 找不到想要的 | NO_RESULT | 搜索/检索没有返回相关内容 |
| 回答太长 | TOO_LONG | 啰嗦,没抓重点 |
| 回答太短 | TOO_SHORT | 过于简略,不够详细 |
| 引用错误 | BAD_CITATION | 引用的文档和回答不匹配 |
| 格式难看 | BAD_FORMAT | 排版/格式影响阅读 |
| 其他 | OTHER | 文本输入 |

**3.1.2 数据模型**

```sql
CREATE TABLE agent_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id BIGINT NOT NULL COMMENT '关联 agent_turns.id',
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,

    feedback_type ENUM('POSITIVE','NEGATIVE') NOT NULL,
    reason_code VARCHAR(32) COMMENT 'NEGATIVE 时的理由代码',
    feedback_text TEXT COMMENT '用户文本反馈',
    response_snapshot TEXT COMMENT '被反馈的回答快照 (防止回答被修改后无法追溯)',

    -- 上下文 (用于归因分析)
    intent VARCHAR(32),
    agent_version VARCHAR(32),
    experiment_id VARCHAR(64),
    experiment_bucket VARCHAR(16),

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_turn (turn_id),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_type_reason (feedback_type, reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 用户反馈';
```

### 3.2 CSAT 评分(会话级)

**采集时机**:会话结束(用户主动关闭/超时 30 分钟无活动)后,弹出 CSAT 问卷。

**问题**:
> "你对这次与助手的对话满意吗?"
> 😡 😟 😐 🙂 😍 (1-5 分)

**采集策略**:
- 不每次都弹(打扰),**抽样 20%** 的结束会话弹出。
- 同一用户 24h 内最多弹一次。
- 回收率预期:5-10%。

### 3.3 NPS(净推荐值,季度)

**采集时机**:季度问卷,通过站内通知推送给过去 30 天使用过 agent 的用户。

**问题**:
> "你有多大可能向同学推荐 CampusShare 的智能助手?"
> 0-10 分

```
NPS = %推荐者(9-10分) - %贬损者(0-6分)
```

- 目标:NPS ≥ 30(良好),≥ 50(优秀)。
- 频率:季度,避免频繁打扰。

### 3.4 举报(负向极端信号)

用户可对 assistant 回答举报,理由:
- 有害内容
- 错误信息
- 侵犯隐私
- 其他

举报是**高优先级信号**,每条都需人工审核,严重的(有害内容)需立即下线相关回答并排查。

## 四、隐式信号采集

### 4.1 采纳行为(最强正向信号)

**定义**(与 A/B 测试的采纳率一致):
- 点击引用卡片 → 采纳
- 点 👍 → 采纳
- 60s 内无重试/无中断 → 默认采纳

```
采纳率 = adopted_turns / total_turns
```

- **目标**:≥ 0.60。
- **为何 60% 而非更高**:agent 不可避免有 CLARIFY(需澄清)和 OUT_OF_SCOPE(拒答)场景,这些不算「失败」但也不算「采纳」。

### 4.2 重试行为(强负向信号)

**定义**:用户在 60s 内对同一问题重新提问(可能是改写也可能是原样重发)。

```
重试率 = retried_turns / total_turns
```

- **目标**:≤ 0.12。
- **细分**:
  - 原样重发(复制粘贴):agent 完全没理解,最差。
  - 改写重发:agent 部分理解但不够好,有优化空间。
  - 追问(换角度问):正常对话,不算重试。

### 4.3 中断行为(强负向信号)

**定义**:用户在 agent 流式输出过程中主动点击「停止」。

```
中断率 = interrupted_turns / total_turns
```

- **目标**:≤ 0.10。
- **细分**:
  - 输出 < 20% 时中断:回答方向完全错误。
  - 输出 20-80% 时中断:回答太慢或太长。
  - 输出 > 80% 时中断:接近完成,影响小。

### 4.4 会话深度

```
平均会话轮数 = total_turns / total_sessions
```

- **解读需谨慎**:
  - 深度上升可能是回答好(用户想深入)。
  - 也可能是回答差(用户需反复追问才能得到答案)。
- **结合采纳率看**:深度上升 + 采纳率上升 = 好;深度上升 + 采纳率下降 = 差。

### 4.5 引用点击率

```
引用点击率 = turns_with_citation_click / turns_with_citations
```

- **目标**:≥ 0.25。
- **意义**:用户点击引用说明回答引起了兴趣,且引用看起来相关。
- **过低**:引用不吸引人或回答已足够(不需看原文)。

### 4.6 回访频率

```
周回访率 = 7日内再次使用 agent 的用户 / 本周使用过的用户
```

- **目标**:≥ 0.30。
- **意义**:回访是最真实的「有用」信号——用户用过还会再来。

### 4.7 复制行为

```
复制率 = turns_with_copy / total_turns
```

- 用户长按/选择回答文本复制,说明内容有引用价值。
- **目标**:≥ 0.15。

## 五、满意度归因分析

### 5.1 👎 归因

每条 👎 反馈需归因到具体问题层:

```
👎 reason → 可能的根因 → 优化方向

INACCURATE → 检索错误 / LLM 幻觉 → 优化检索 / 加强 grounding
INCOMPLETE → 检索不全 / 生成截断 → 扩大召回 / 增加 max_tokens
NO_RESULT → 检索失败 / 知识缺失 → 补充知识库
TOO_LONG → prompt 缺乏长度约束 → 调整 prompt
TOO_SHORT → 检索结果少 / prompt 过度简洁 → 扩大召回
BAD_CITATION → 引用映射错误 → 优化引用后处理
BAD_FORMAT → markdown 渲染问题 → 优化前端渲染
```

### 5.2 周报归因

每周生成满意度周报:

```
┌──────────────────────────────────────────────────────┐
│            Agent 满意度周报 (2026-W26)                 │
├──────────────────────────────────────────────────────┤
│                                                       │
│  显式信号:                                            │
│   👍 152 次 (👍率 78%)                                │
│   👎 43 次 (👎率 22%)                                 │
│   👎 Top 理由:                                        │
│     1. INACCURATE 15次 (35%) → 检索质量问题           │
│     2. INCOMPLETE 10次 (23%) → 知识库覆盖不足         │
│     3. TOO_LONG 8次 (19%) → prompt 长度约束           │
│   CSAT: 3.8/5 (n=42, 回收率 8%)                      │
│                                                       │
│  隐式信号:                                            │
│   采纳率: 58% ↓2% (目标 60%)                          │
│   重试率: 14% ↑2% (目标 ≤12%)                         │
│   中断率: 11% (持平)                                  │
│   会话深度: 3.2轮 (持平)                              │
│   引用点击率: 22% (持平)                              │
│   周回访率: 28% ↓3%                                   │
│                                                       │
│  归因分析:                                            │
│   本周采纳率下降主因: SEARCH 意图采纳率从 62% → 55%   │
│   对应 👎 INACCURATE 增多,根因:                      │
│   - 新增知识文档未及时 embedding 索引                  │
│   - 修复: KnowledgeReindexScheduler 提前执行          │
│                                                       │
│  行动项:                                              │
│   1. [P0] 修复知识索引延迟 (本周)                     │
│   2. [P1] 优化 SEARCH prompt 增加简洁性约束 (下周)    │
│   3. [P2] 补充知识库「创作者认证」相关文档 (下周)      │
│                                                       │
└──────────────────────────────────────────────────────┘
```

### 5.3 异常检测
- 采纳率/👍率突然下降 >5%(日环比)→ 告警。
- 中断率/重试率突然上升 >3%(日环比)→ 告警。
- 触发告警后自动拉取最近 1 小时的 👎 反馈,辅助定位。

## 六、满意度数据表

### 6.1 隐式信号采集(agent_turns 字段扩展)

```sql
-- agent_turns 表新增隐式信号字段 (见 12-database-schema.md)
ALTER TABLE agent_turns ADD COLUMN adopted BOOLEAN COMMENT '是否采纳';
ALTER TABLE agent_turns ADD COLUMN retried BOOLEAN COMMENT '是否被重试';
ALTER TABLE agent_turns ADD COLUMN interrupted BOOLEAN COMMENT '是否被中断';
ALTER TABLE agent_turns ADD COLUMN interrupt_pct INT COMMENT '中断时输出百分比';
ALTER TABLE agent_turns ADD COLUMN citation_clicked BOOLEAN COMMENT '是否点击引用';
ALTER TABLE agent_turns ADD COLUMN copied BOOLEAN COMMENT '是否复制回答';
ALTER TABLE agent_turns ADD COLUMN adopted_evaluated_at DATETIME COMMENT '采纳判定时间';
```

### 6.2 满意度汇总表(agent_satisfaction_daily)

```sql
CREATE TABLE agent_satisfaction_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL,

    -- 显式
    positive_count INT DEFAULT 0,
    negative_count INT DEFAULT 0,
    negative_reasons_json JSON COMMENT '{"INACCURATE":15, "INCOMPLETE":10}',
    csat_avg DECIMAL(4,2) COMMENT '平均 CSAT',
    csat_count INT COMMENT 'CSAT 回收数',
    report_count INT DEFAULT 0 COMMENT '举报数',

    -- 隐式
    adoption_rate DECIMAL(6,4),
    retry_rate DECIMAL(6,4),
    interrupt_rate DECIMAL(6,4),
    avg_session_depth DECIMAL(4,2),
    citation_click_rate DECIMAL(6,4),
    weekly_return_rate DECIMAL(6,4),
    copy_rate DECIMAL(6,4),

    -- 切片
    adoption_rate_by_intent JSON COMMENT '{"HOW_TO":0.65, "SEARCH":0.55}',

    -- 元信息
    total_turns INT NOT NULL,
    total_sessions INT NOT NULL,
    agent_version VARCHAR(32),

    UNIQUE KEY uk_date (stat_date),
    INDEX idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 满意度日汇总';
```

## 七、反馈闭环

### 7.1 闭环流程

```
用户 👎 → 反馈入库 → 周报归因 → 优化行动项
    ↑                                    ↓
    └──── 改进后采纳率提升 ←──── 发版 ←──┘
```

### 7.2 反馈可见性
- 用户点 👎 后,显示:"感谢反馈,我们会持续改进。"
- 如果该问题在后续版本修复,可选择性通知用户:"你反馈的问题已优化,试试看?"(需用户授权通知)。

### 7.3 反馈防滥用
- 同一用户对同一回答只能反馈一次。
- 短时间内大量 👎(如 1 分钟内 >10 条)触发风控,可能是恶意刷负。
- 👍 无此限制(刷正不影响系统,但需监控异常)。

## 八、满意度指标门禁

| 指标 | 目标 | 告警线 | 阻断线(发版) |
|------|------|--------|---------------|
| 采纳率 | ≥ 60% | < 58% | < 55% |
| 重试率 | ≤ 12% | > 14% | > 16% |
| 中断率 | ≤ 10% | > 12% | > 15% |
| 👍率 | ≥ 75% | < 72% | < 68% |
| CSAT | ≥ 3.8 | < 3.5 | < 3.2 |
| 引用点击率 | ≥ 25% | < 22% | - |
| 周回访率 | ≥ 30% | < 27% | - |

**注意**:满意度指标有滞后性(需积累 1-2 周数据),不用于 PR 门禁,用于**版本发版审批**和**季度回顾**。

## 九、决策记录

### ADR-175: 用户满意度度量体系
- **背景**:代理指标(检索/生成)不能完全反映用户体验,需要直接度量满意度。
- **决策**:
  - 显式:👍👎(即时,回收率高)+ CSAT(会话级,抽样 20%)+ NPS(季度)+ 举报。
  - 隐式:采纳率/重试率/中断率/会话深度/引用点击率/回访率/复制率。
  - 👎 必选理由(8 类),用于归因分析。
  - 周报归因:每周分析 👎 理由分布,定位根因,产出行动项。
  - 门禁:满意度指标用于发版审批(非 PR 门禁),因数据有滞后性。
- **理由**:显式+隐式结合,显式准确但稀疏,隐式覆盖广但需推断;参考字节体验度量体系。
- **权衡**:CSAT 抽样打扰用户,但全量弹窗更打扰;20% 抽样是平衡点。
- **状态**:采纳
