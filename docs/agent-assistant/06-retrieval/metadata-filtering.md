# 元数据过滤

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、可过滤维度

| 维度 | 来源 | 字段 | 示例 |
|------|------|------|------|
| 学校 | slots.school | school_id | 清华 |
| 主分类 | slots.category | category_id | 音乐 |
| 子分类 | slots.subcategory | sub_category_id | K-POP |
| 帖子类型 | slots.post_type | post_type | resource/discussion |
| 作者 | slots.author | author_id | 某用户 |
| 是否有文件 | slots.has_file | file_url IS NOT NULL | 求资料 |
| 时间范围 | slots.time_range | create_time | 最近一周 |
| 排序 | slots.sort | - | 最新/最热 |

## 二、过滤策略

### 2.1 硬过滤（必满足）
- school_id、category_id、post_type、has_file、author_id：作为 WHERE 硬条件。
- 这些是用户明确意图，不满足的帖不召回。

### 2.2 软过滤（偏好）
- time_range、sort：作为排序加权或时间窗口偏好，可放宽。
- 例：「最近最热的 OS 讨论」→ 时间窗口可从「最近一周」放宽到「最近一月」若召回不足。

### 2.3 渐进放宽
当硬过滤后召回 < 5 条，按优先级放宽：
1. 先去掉 sub_category_id（保留 category）。
2. 再去掉 category_id（保留 school）。
3. 再去掉 school_id（全库语义）。
4. 仍不足 → 触发澄清/推荐相近板块。

每步放宽后向用户说明「未在 [清华] 找到，已为你扩大到全平台」。

## 三、过滤与向量检索的联合

PG-Vector 支持 HNSW + WHERE 联合查询（先过滤再 HNSW 搜索，或 HNSW 搜索中过滤）。注意：
- 过滤选择性高（过滤掉 >90% 数据）时，HNSW 可能召回不足 → 需调大 `ef_search` 或退化到精确扫描。
- PG-Vector 0.7+ 支持迭代过滤，性能更好。

## 四、隐私过滤

- 检索结果必须过滤掉用户隐私设置不可见的帖子（如作者关闭了「公开帖子」）。
- 通过 Feign 调 user-service 批量校验作者隐私设置，或冗余 `is_public` 字段到 post_vectors。
- MVP 简化：默认只检索 status=1 且 deleted=0 的帖，隐私过滤进阶做。

## 五、决策记录 (ADR)

### ADR-029: 渐进放宽策略
- **理由**：硬过滤召空时直接说「没找到」体验差；渐进放宽 + 说明能持续给价值。
- **风险**：放宽过多偏离用户意图 → 每步说明 + 用户可纠正。
