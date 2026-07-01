# 路由分发策略

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、路由总览

意图确定后，分发到不同处理管线。路由层目标：**简单意图走快路径省成本，复杂意图走 Agent 主循环保质量**。

```
IntentRouter 输出
  │
  ├─ OUT_OF_SCOPE/*        ─► 拒绝模板（0 次 LLM）
  ├─ NAVIGATE/my_list      ─► 直接返回跳转卡（0 次 LLM）
  ├─ NAVIGATE/feature_loc  ─► 知识库检索 + 跳转卡（0~1 次 LLM）
  ├─ NAVIGATE/section_loc  ─► 分类检索 + 跳转卡（0~1 次 LLM）
  ├─ HOW_TO/*              ─► 知识库检索 → 生成（1 次 LLM）
  ├─ CLARIFY/*             ─► 上下文消解 → 重走原管线 or 直接答
  └─ SEARCH/*              ─► Agent ReAct 主循环（多次 LLM + 工具）
```

## 二、快路径（Fast Path）

### 2.1 OUT_OF_SCOPE
- 模板化拒绝 + 引导。不调 LLM。
- 例：「这个问题超出了我的服务范围，我是 CampusShare 平台助手，可以帮你找资源、教你怎么用功能哦～」

### 2.2 NAVIGATE/my_list
- 槽位映射到路由：
  - 「我点赞的」→ `/profile/liked`
  - 「我收藏的」→ `/profile/starred`
  - 「我浏览历史」→ `/profile/history`
  - 「我回复的」→ `/profile/comments`
  - 「我发的帖」→ `/profile/posts`
  - 「关注/粉丝/互关」→ `/profile/following|followers|mutual`
- 直接返回跳转卡片，不调 LLM 生成。

### 2.3 NAVIGATE/feature_loc
- 槽位映射：
  - 「设置」→ `/settings/account`
  - 「通知设置」→ `/settings/notifications`
  - 「创作者认证」→ `/creator-verification`
  - 「私信」→ `/messages`
- 直接跳转卡（可附一句说明，调 1 次轻 LLM 或用模板）。

## 三、知识库路径（HOW_TO）

```
rewritten_query
  → 知识库向量检索 Top-3
  → 拼接为 context
  → LLM 生成步骤 + 跳转入口（流式）
  → 附引用（知识库文档 id）
```

不走 ReAct，单轮检索+生成，省时省钱。

## 四、Agent 主路径（SEARCH）

走完整 ReAct 循环（见 `07-agent-design`），可多次调用工具（检索帖子、查用户信息、定位板块）。

## 五、CLARIFY 路由

```
CLARIFY
  ├─ coreference → 从上下文取实体，不检索，直接答 or 筛选
  ├─ refine → 修正后重走上一步意图对应管线
  └─ followup → 上下文 + 新 query 走原管线
```

## 六、路由决策表（实现参考）

| intent | sub_intent | 调 LLM 次数 | 调工具 | 流式 |
|--------|-----------|:---:|:---:|:---:|
| OUT_OF_SCOPE | * | 0 | 否 | 否 |
| NAVIGATE | my_list | 0 | 否 | 否 |
| NAVIGATE | feature_loc | 0~1 | 否 | 可 |
| NAVIGATE | section_loc | 0~1 | 是(分类检索) | 可 |
| HOW_TO | * | 1 | 是(知识库) | 是 |
| CLARIFY | coreference | 0~1 | 否 | 可 |
| CLARIFY | refine/followup | 同原管线 | 同原管线 | 是 |
| SEARCH | * | 2~4(ReAct) | 是 | 是 |

## 七、决策记录 (ADR)

### ADR-013: 简单意图走快路径
- **理由**：OUT_OF_SCOPE/NAVIGATE 占比预估 30%+，走快路径省 60%+ 成本与延迟。
- **风险**：快路径误判（把 SEARCH 误分到 NAVIGATE）会少检索 → 用 confidence 阈值 + 兜底 SEARCH 缓解。
