# 内容安全策略

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[input-guardrails.md](./input-guardrails.md)、[output-guardrails.md](./output-guardrails.md)

## 一、内容安全的范围

本文件聚焦「内容本身是否合规」,与 input-guardrails(防注入)和 output-guardrails(防幻觉)互补:
- input/output-guardrails 关注**技术攻击**(注入/幻觉)。
- 本文件关注**内容合规**(敏感话题/未成年人保护/意识形态/法律法规)。

CampusShare 面向高校学生群体,内容安全标准需符合中国大陆法律法规。

## 二、内容安全分类体系

### 2.1 禁止内容(必须拦截)

| 类别 | 说明 | 示例 |
|------|------|------|
| 政治敏感 | 违反国家政策、颠覆国家政权 | (不举例) |
| 暴力恐怖 | 宣扬恐怖主义、极端主义 | 爆炸物制作方法 |
| 色情低俗 | 露骨性描写、未成年人色情 | (不举例) |
| 毒品相关 | 毒品制作、贩卖、使用指导 | 冰毒合成步骤 |
| 诈骗犯罪 | 诈骗方法、洗钱指导 | 信用卡盗刷教程 |
| 自残自杀 | 鼓励或方法指导 | (不举例) |
| 仇恨歧视 | 种族/性别/地域歧视 | 针对特定群体的侮辱 |

### 2.2 限制内容(需谨慎处理)

| 类别 | 处理方式 |
|------|----------|
| 学术作弊 | 拒绝代写作业,但可提供学习指导 |
| 游戏外挂 | 拒绝外挂制作,但可讨论游戏攻略 |
| 网络安全 | 拒绝攻击教程,但可讨论防御知识 |
| 情感咨询 | 提供倾听,但建议寻求专业帮助 |
| 投资理财 | 不给具体投资建议,可提供基础知识 |

### 2.3 允许内容

- 平台功能使用帮助
- 学习资料搜索与推荐
- 学术知识讨论(非代写)
- 校园生活话题

## 三、检测技术栈

```
┌──────────────────────────────────────────────────────────┐
│                  内容安全检测技术栈                        │
└──────────────────────────────────────────────────────────┘

 输入/输出文本
      │
      ▼
 ┌────────────┐
 │ 层1: 词库   │  敏感词库匹配 (~1ms)
 │ 匹配       │  覆盖:政治/色情/暴力/毒品 明确关键词
 └─────┬──────┘
       │ 未命中
       ▼
 ┌────────────┐
 │ 层2: 规则   │  上下文模式匹配 (~5ms)
 │ 引擎       │  覆盖:变体词/拆字/谐音 (如"裸"→"果")
 └─────┬──────┘
       │ 未命中
       ▼
 ┌────────────┐
 │ 层3: LLM    │  语义级分类 (~300ms)
 │ 分类器     │  覆盖:隐含/暗示性有害内容
 └─────┬──────┘
       │
       ▼
   拦截 / 放行
```

### 3.1 敏感词库

**词库来源**:
- 开源词库基础(如 `sensitive-words` GitHub 项目)。
- 平台运营积累(用户举报触发的新词)。
- 法律法规更新(新出台的禁词)。

**词库结构**:
```sql
CREATE TABLE agent_sensitive_words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(128) NOT NULL,
    category ENUM('POLITICAL','PORNOGRAPHY','VIOLENCE','DRUG','FRAUD','SELF_HARM','HATE','OTHER') NOT NULL,
    severity ENUM('BLOCK','REVIEW') NOT NULL COMMENT 'BLOCK=直接拦截, REVIEW=人工审核',
    variants_json JSON COMMENT '变体: ["变体1", "变体2"]',
    enabled BOOLEAN DEFAULT TRUE,
    added_by VARCHAR(64),
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_word (word),
    INDEX idx_category (category, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';
```

**变体处理**:
- 拆字:「裸」→「衤果」
- 谐音:「黄」→「簧」
- 符号插入:「赌 博」→「赌.博」
- 英文替换:「drug」→「dr ug」

```java
public class SensitiveWordMatcher {
    private final AhoCorasickAutomaton automaton;  // AC 自动机高效多模式匹配

    @PostConstruct
    public void init() {
        List<String> words = sensitiveWordMapper.findAllEnabled();
        // 加入变体
        for (String w : words) {
            automaton.add(w);
            for (String variant : getVariants(w)) {
                automaton.add(variant);
            }
        }
        automaton.build();
    }

    public MatchResult match(String text) {
        // 预处理:去除空格/特殊符号 (防符号绕过)
        String normalized = text.replaceAll("[\\s\\p{Punct}]", "");
        List<Match> matches = automaton.search(normalized);
        return new MatchResult(matches);
    }
}
```

### 3.2 LLM 语义分类

对未命中词库但语义可疑的内容,用 LLM 做最终判断:

```
你是内容安全审核助手。判断以下内容是否属于禁止类别。

禁止类别:政治敏感、暴力恐怖、色情低俗、毒品相关、诈骗犯罪、自残自杀、仇恨歧视。

内容: {text}

输出 JSON:
{
  "safe": true/false,
  "category": "VIOLENCE",  // 若 unsafe
  "confidence": 0.0-1.0,
  "reason": "..."
}
```

## 四、特殊场景处理

### 4.1 未成年人保护

CampusShare 用户群体含大学生(多数已成年),但仍需保护:
- Agent 不讨论未成年人不适宜话题(酒精/烟草使用指导)。
- 检测到自残/自杀倾向时,**必须**提供心理援助热线:
  ```
  如果你正在经历困难,请记住你不是一个人。
  全国24小时心理援助热线:400-161-9995
  北京心理危机研究与干预中心:010-82951332
  ```

### 4.2 学术诚信

| 请求 | 处理 |
|------|------|
| "帮我写这篇论文" | 拒绝:"我不能代写作业,但可以帮你理清思路" |
| "这道题答案是什么" | 拒绝直接答案,提供解题思路 |
| "帮我检查代码 bug" | 允许(调试帮助≠代写) |
| "解释这个概念" | 允许(知识讲解) |

### 4.3 意识形态中立

Agent 在政治/宗教/意识形态话题上保持中立:
- 不表达政治立场。
- 不评价宗教优劣。
- 涉及争议话题时,提供多方观点或拒绝评论。

### 4.4 法律咨询边界

- Agent **不是**律师,不提供具体法律建议。
- 可提供法律基础知识(如「著作权法保护什么」)。
- 涉及具体纠纷时,建议咨询专业律师。

## 五、知识库内容安全

Agent 检索的知识库(knowledge_articles)和帖子(posts)本身也可能包含不安全内容。

### 5.1 知识库审核
- 知识文档由运营团队编写,发布前人工审核。
- 审核标准与本文件一致。
- 已发布文档定期复审(季度)。

### 5.2 帖子内容过滤
- 用户帖子可能含不当内容,agent 检索时需过滤。
- **方案**:检索结果在送入 LLM 前,跑层 1(词库)检测,命中的文档剔除。
- 已被平台删除/屏蔽的帖子,不进入 agent 检索范围。

### 5.3 检索结果安全标记
```sql
-- post_vectors 表新增安全标记字段
ALTER TABLE post_vectors ADD COLUMN safety_score DECIMAL(4,3) DEFAULT 1.0 COMMENT '安全分 0-1, <0.5 不参与 agent 检索';
ALTER TABLE post_vectors ADD COLUMN safety_checked_at DATETIME COMMENT '最近安全检查时间';
```

- 安全分 < 0.5 的帖子:不进入 agent 检索候选。
- 安全分 0.5-0.8:进入候选但 LLM 输出护栏加强检查。
- 安全分 > 0.8:正常。

## 六、内容安全监控

### 6.1 实时指标

| 指标 | Prometheus metric | 告警阈值 |
|------|-------------------|----------|
| 敏感词命中率 | agent_sensitive_word_hit_rate | >5% 日 |
| LLM 拦截率 | agent_content_block_rate | >2% 日 |
| 举报率 | agent_report_rate | >1% 周 |
| 自残/自杀检测次数 | agent_self_harm_detection_count | >0 即告警 |

### 6.2 人工审核队列

LLM 分类置信度 0.5-0.8 的内容进入人工审核队列:

```sql
CREATE TABLE agent_content_review_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) COMMENT '疑似类别',
    confidence DECIMAL(4,3),
    status ENUM('PENDING','APPROVED','REJECTED','ESCALATED') DEFAULT 'PENDING',
    reviewer VARCHAR(64),
    reviewed_at DATETIME,
    review_note TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容人工审核队列';
```

- 审核 SLA:24 小时内处理。
- 审核结果反馈到词库(确认违规 → 加入词库)。

## 七、词库热更新

```java
@RestController
@RequestMapping("/api/admin/agent/sensitive-words")
public class SensitiveWordController {

    @PostMapping
    public Result addWord(@RequestBody SensitiveWordDTO dto) {
        sensitiveWordMapper.insert(dto);
        // 通知所有实例重建 AC 自动机
        applicationEventPublisher.publishEvent(new SensitiveWordUpdatedEvent());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result removeWord(@PathVariable Long id) {
        sensitiveWordMapper.disable(id);
        applicationEventPublisher.publishEvent(new SensitiveWordUpdatedEvent());
        return Result.success();
    }
}
```

- 通过 Redis Pub/Sub 通知所有 agent-service 实例重建词库。
- 重建耗时 < 5 秒(词库约 5000 词)。

## 八、决策记录

### ADR-179: 内容安全三层检测 + 词库热更新
- **背景**:内容合规是面向中国市场的硬性要求,需多层防护。
- **决策**:
  - 三层检测:敏感词库(AC自动机)→ 规则引擎(变体)→ LLM 语义分类。
  - 词库热更新:管理后台增删,Redis Pub/Sub 通知全实例重建。
  - 知识库/帖子安全分:< 0.5 不参与检索。
  - 特殊场景:自残必提供援助热线;学术诚信拒绝代写;意识形态中立。
  - 灰度内容(置信度 0.5-0.8)进人工审核队列,24h SLA。
- **理由**:词库快但覆盖窄,LLM 准但慢,三层结合平衡速度与准确性。
- **状态**:采纳
