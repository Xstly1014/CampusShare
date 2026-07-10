# 11 前端集成与 UI 设计

> 状态: 草稿
> 最后更新: 2026-06-30

本目录规划 Agent 助手在前端的集成方案：底部导航栏改动、助手页面 UI、SSE 客户端、状态管理、组件拆分、移动端体验。

## 文档清单

| 文档 | 主题 |
|------|------|
| [navbar-integration.md](./navbar-integration.md) | 底部 NavBar 插入助手入口 |
| [assistant-page-design.md](./assistant-page-design.md) | 助手主页面 UI 设计 |
| [sse-client-implementation.md](./sse-client-implementation.md) | SSE 客户端实现 |
| [state-management.md](./state-management.md) | Zustand 状态管理 |
| [component-breakdown.md](./component-breakdown.md) | 组件拆分与职责 |
| [mobile-ux.md](./mobile-ux.md) | 移动端 UX 考量 |

## 设计原则

1. **移动端优先**: 校园场景以手机为主，5 tab 底部导航是基准。
2. **流式感知**: 用户必须实时看到 Agent 在做什么（思考/检索/生成），不能空白等待。
3. **引用可溯源**: 每条回答的引用 [n] 可点击查看原帖/文档。
4. **中断可控**: 用户可随时停止生成，已生成内容保留。
5. **不打扰**: 助手不主动弹窗，只在用户进入页面时显示。
6. **与现有设计语言一致**: 蓝色主题 #2563EB、Tailwind、Lucide 图标、Toast 反馈。
