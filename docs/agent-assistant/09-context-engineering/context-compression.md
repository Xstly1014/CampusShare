# 上下文压缩与摘要

> 状态: 核心已实现
> 最后更新: 2026-07-10

## 一、目标

当对话轮次累积导致 L4 历史 token 超预算时，把旧历史压缩为摘要，让出 token 给 L3 检索结果。压缩必须**保真**：不能丢失用户已确认的关键约束、已引用的帖子 ID、已澄清的意图。

## 二、触发条件

三种触发器，任一满足即压缩：

| 触发器 | 阈值 | 动作 |
|--------|------|------|
| Token 触发 | L4 历史 > 2500 token | 压缩最早 50% |
| 轮次触发 | 单会话轮次 > 10 | 压缩最早 5 轮 |
| 主题切换触发 | 意图分类检测到主题切换（见 [multi-turn-context.md](../04-intent-understanding/multi-turn-context.md)） | 压缩切换前的全部历史 |

## 三、压缩层级

压缩不是一步到位，而是渐进式三级：

### 3.1 L1 滚动摘要（Rolling Summary）

- 维护一个 `rolling_summary` 字段，始终是"截至上一轮的对话摘要"。
- 每轮新对话结束后，把 `rolling_summary + 本轮 user+assistant` 喂给 LLM 生成新摘要。
- Prompt 模板：

  ```
  你是对话摘要器。请把以下摘要 + 新对话合并为一份新摘要，要求：
  1. 保留用户提到的所有具体约束（帖子ID、分类、学校、时间）。
  2. 保留用户已澄清的意图与已否决的选项。
  3. 丢弃寒暄、重复确认、无效试探。
  4. 摘要 ≤300 字。

  旧摘要: {rolling_summary}
  新对话: {new_turn}
  ```

- L4 历史装入时：`[rolling_summary 占位] + 最近 N 轮原文`。
- 摘要 token 控制在 300 以内，节省 1500-2000 token。

### 3.2 L2 槽位冻结（Slot Freezing）

- 对话中产生的"结构化事实"不进摘要文本，而是抽取为槽位 JSON：

  ```json
  {
    "confirmed_intent": "SEARCH",
    "target_category": "软件",
    "target_school": null,
    "mentioned_post_ids": ["uuid1", "uuid2"],
    "rejected_post_ids": ["uuid3"],
    "user_constraints": {"format": "PDF", "year": ">=2024"}
  }
  ```

- 槽位 JSON 单独存 Redis，不参与压缩。
- L4 历史装入时，槽位 JSON 作为前缀注入：`[已确认约束: {slots}]`。

### 3.3 L3 关键消息标记（Pin Message）

- 某些消息（如用户明确说"记住我偏好 PDF"）标记为 `pinned=true`，永不压缩。
- Pin 标记由 LLM 在摘要时顺手输出：`{"pin": true, "reason": "用户偏好声明"}`。
- Pinned 消息最多保留 5 条，超出按时间淘汰最早的。

## 四、压缩流水线

```
新轮次结束
   │
   ├─► 槽位更新器（LLM 抽取 slots 增量）──► Redis slots 字段
   │
   ├─► Pin 检测器（LLM 判断是否 pinned）──► Redis pinned 列表
   │
   └─► 摘要生成器（LLM 合并 rolling_summary）──► Redis rolling_summary
                                                    │
                                              若 token 仍超 ──► 触发 L4 截断
```

三个 LLM 调用合并为一次（同一 prompt 输出三段 JSON），节省成本。

## 五、压缩质量保障

### 5.1 摘要保真测试

- 黄金集：50 组多轮对话，每组人工标注"必须保留的关键事实"。
- 每次摘要 prompt 改版，跑黄金集，要求关键事实召回率 ≥95%。
- 召回率 = 摘要保留的关键事实数 / 人工标注的关键事实数。

### 5.2 压缩后行为一致性测试

- 同一组多轮对话，分别用"全历史"和"压缩后历史"跑 Agent，对比最终回答的语义相似度。
- 目标：cosine 相似度 ≥0.85（用 BGE-M3 算 embedding）。

### 5.3 在线监控

- `compression_ratio`: 压缩前 token / 压缩后 token，目标 3-5×。
- `post_compression_clarify_rate`: 压缩后用户需要重新澄清的比例，目标 <5%。若上升说明压缩丢了关键信息。

## 六、压缩与反思阶段的协作

- 反思阶段（见 [07-agent-design/reflection-and-verification.md](../07-agent-design/reflection-and-verification.md)）需要看"完整证据链"，不能用压缩后的历史。
- 解决方案：反思阶段绕过 ContextAssembler 的压缩，从 `agent_context_snapshots` 表取最近 N 轮的原始上下文快照拼装。
- 代价：反思阶段 input token 可能到 20K，但反思是低频动作（每会话 1-2 次），可接受。

## 七、边界情况

### 7.1 首轮压缩

- 第 11 轮触发首次压缩，此时 rolling_summary 为空。
- 直接对前 5 轮做摘要，无"旧摘要"合并。

### 7.2 跨会话恢复

- 用户 30 分钟内回到同一会话：恢复 rolling_summary + slots + pinned + 最近 3 轮原文。
- 超过 30 分钟：开启新会话，但继承 long-term memory（见 [long-term-memory.md](./long-term-memory.md)）。

### 7.3 摘要失败 fallback

- 摘要 LLM 调用失败/超时：不压缩，直接截断最早 2 轮，记录 `compression_fallback=truncate`。
- 摘要输出格式非法（JSON parse 失败）：重试 1 次，仍失败则截断。

## 八、决策记录 (ADR)

### ADR-050: 三级渐进压缩（摘要 + 槽位 + Pin）
- **理由**：单一摘要会丢结构化事实；单一槽位会丢叙述性上下文。三者结合既省 token 又保真。
- **替代方案**：仅摘要（丢约束）、仅槽位（丢语用信息）。

### ADR-051: 摘要/槽位/Pin 三合一 LLM 调用
- **理由**：三次独立调用成本 3×、延迟 3×。合并为一次 prompt 输出三段 JSON，成本降 60%。
- **风险**：单次输出较长可能触发输出截断，通过 `max_tokens=800` 限制 + 结构化输出校验兜底。

### ADR-052: 反思阶段绕过压缩
- **理由**：反思是为了发现错误，必须看到完整证据。压缩会引入"二次错误源"。
- **代价**：反思阶段 token 成本 ≈3×，但反思低频，整体可接受。

### ADR-053: 压缩质量用"行为一致性"而非"ROUGE"
- **理由**：ROUGE 衡量文本相似度，但摘要的目标是"让 Agent 行为不变"，行为一致性更直接。
- **实现**：BGE-M3 算回答 embedding 的 cosine 相似度，门槛 0.85。

---

## 九、实现详情（已上线）

### 9.1 三级压缩已实现

ContextCompressionService 实现了完整的三级压缩架构，三个压缩步骤合并为一次 LLM 调用输出三段 JSON：

| 压缩层级 | 实现状态 | 说明 |
|---------|---------|------|
| L1 滚动摘要（Rolling Summary） | ✅ 已实现 | 维护 rolling_summary 字段，每轮新对话后 LLM 生成新摘要 |
| L2 槽位冻结（Slot Freezing） | ✅ 已实现 | 抽取结构化事实为 JSON 槽位（confirmed_intent, target_category, mentioned_post_ids, user_constraints 等） |
| L3 关键消息标记（Pin Message） | ✅ 已实现 | LLM 标记 pinned=true 的关键消息，最多保留 5 条 |

压缩结果在 ConversationMemoryService 中读取并注入到 L4 历史：`[rolling_summary] + [slots JSON] + [pinned messages] + [最近 N 轮原文]`。

### 9.2 MySQL 持久化存储

压缩结果持久化到 MySQL 三张表，实现跨会话恢复：

| 表名 | Entity/Mapper | 存储内容 |
|------|--------------|---------|
| `context_summaries` | ContextSummary / ContextSummaryMapper | 滚动摘要文本、摘要版本、更新时间 |
| `context_slots` | ContextSlot / ContextSlotMapper | 结构化槽位 JSON、槽位键值对 |
| `pin_messages` | PinMessage / PinMessageMapper | Pin 标记的关键消息、Pin 原因 |

迁移脚本位置：`backend/docker/mysql/migrate_context_compression.sql`

### 9.3 Redis 热缓存 + MySQL 持久化双写

**存储分层策略**：
- **Redis（热缓存）**：当前活跃会话的压缩结果，TTL 7 天，提供低延迟访问（<5ms）
- **MySQL（持久化）**：压缩结果异步写入 MySQL，Redis miss 时从 MySQL 降级加载

**读写流程**：
1. 压缩成功后先写 Redis，再异步写入 MySQL
2. 读取时优先从 Redis 获取
3. Redis 未命中（如跨会话恢复、Redis 重启），从 MySQL 加载并回填 Redis
