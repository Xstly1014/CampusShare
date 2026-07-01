# 提示词版本管理

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、版本化

- 所有 prompt 模板存仓库 `docs/agent-assistant/prompt-assets/templates/`。
- 文件名含版本：`intent-router-v1.md`、`intent-router-v2.md`。
- 运行时通过配置指定当前版本，便于回滚。

## 二、A/B 测试

- 同一 prompt 的 v1/v2 各分流 50%，对比 KPI。
- 在 `agent_sessions` 表记录 `prompt_version`，便于归因。

## 三、变更流程

1. 新版 prompt 写入仓库（新版本号）。
2. 离线黄金集评估，对比 v1。
3. 通过 → 线上 10% 灰度。
4. KPI 不退化 → 全量。
5. 退化 → 回滚。

## 四、Prompt 即代码

- prompt 变更走 PR 评审，等同代码变更。
- PR 描述含：变更目的、黄金集评估结果、A/B 数据。

## 五、决策记录 (ADR)

### ADR-045: Prompt 版本化 + 灰度
- **理由**：prompt 改动影响大，需可回滚、可归因。
- **实现**：版本号写入会话记录。
