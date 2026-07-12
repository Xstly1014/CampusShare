# 组件拆分与职责

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、组件树

```
AssistantPage
├── AssistantHeader              # 顶栏：返回/标题/历史/清空
├── AssistantMessageList         # 消息流容器
│   ├── WelcomeCard              # 首轮欢迎卡
│   ├── MessageBubble            # 单条消息气泡
│   │   ├── UserBubble           # 用户消息
│   │   ├── AssistantBubble      # 助手消息
│   │   │   ├── CitationRenderer # 引用角标渲染
│   │   │   ├── ReferenceList    # 引用卡列表
│   │   │   ├── ToolCallStatus   # 工具调用状态条
│   │   │   └── FeedbackButtons  # 👍/👎/复制
│   │   └── ErrorBubble          # 错误消息
│   ├── ClarifyCard              # 澄清问题卡
│   └── TypingIndicator          # 生成中动画
├── AssistantInput               # 底部输入区
│   ├── TextArea                 # 多行输入框
│   ├── SendButton               # 发送/停止按钮
│   └── AttachButton             # 附件（预留，禁用）
└── HistoryDrawer                # 历史会话抽屉
    ├── HistoryItem              # 单条历史会话
    └── EmptyHistory             # 空状态
```

## 二、组件职责

### 2.1 AssistantPage

- 路由：`/assistant`
- 职责：页面容器，挂载时创建/恢复会话，卸载时不关闭会话。
- 状态：从 `useAssistantStore` 读 sessionId/messages/isStreaming。
- 布局：`flex flex-col h-screen`，隐藏 NavBar。

### 2.2 AssistantHeader

- 职责：返回按钮、标题、历史按钮、清空按钮。
- 交互：
  - 返回：`navigate(-1)`。
  - 历史：打开 HistoryDrawer。
  - 清空：弹确认弹窗 → `assistantStore.clearSession()` → 重新 createSession。

### 2.3 AssistantMessageList

- 职责：渲染消息列表，自动滚动到底部。
- 自动滚动：新消息到达或 token 流更新时，平滑滚动到底部。
- 用户上滚检测：若用户主动上滚查看历史，暂停自动滚动（避免打断阅读）；用户滚回底部后恢复。

### 2.4 WelcomeCard

- 职责：首轮欢迎语 + 快捷提问按钮。
- 个性化：从后端 welcome_message 取（含个性化内容）。
- 快捷提问：点击直接 `sendMessage(预设问题)`。

### 2.5 MessageBubble

- 职责：根据 role 分发到 UserBubble / AssistantBubble / ErrorBubble。
- 通用属性：avatar、timestamp、role 标识。

### 2.6 UserBubble

- 样式：右对齐，蓝色背景白字，`rounded-2xl rounded-tr-sm`。
- 内容：纯文本，支持换行。
- 无头像（节省空间）。

### 2.7 AssistantBubble

- 样式：左对齐，白底灰边，`rounded-2xl rounded-tl-sm`。
- 头像：Bot 图标圆形背景。
- 内容：Markdown 渲染（标题/列表/代码块/链接）。
- 子组件：CitationRenderer / ReferenceList / ToolCallStatus / FeedbackButtons。

### 2.8 CitationRenderer

- 职责：把正文中的 `[n]` 渲染为可点击角标。
- 实现：正则替换 `[n]` 为 `<sup>` 元素。
- 交互：点击角标滚动到 ReferenceList 中对应卡片并高亮 1 秒。

### 2.9 ReferenceList

- 职责：回答下方列出引用卡片。
- 卡片样式：浅灰背景，`rounded-lg`，含图标 + 标题。
- 点击行为：
  - post → `navigate(/post/{id})`
  - knowledge → `navigate(/help/{id})`
  - user → `navigate(/user/{id})`
  - category → `navigate(/category/{id})`

### 2.10 ToolCallStatus

- 职责：显示 Agent 的工具调用过程。
- 状态：
  - start：`🔍 正在搜索帖子...`
  - end：`✓ search_posts (320ms) 找到 3 篇`
- 折叠：多个工具调用时默认显示最近 1 条，点击展开全部。
- 样式：浅灰小字，工具图标 + 文本。

### 2.11 FeedbackButtons

- 职责：👍/👎/复制 三个按钮。
- 状态：feedback 已提交后高亮对应按钮，禁用另一个。
- 👎 交互：弹出预设原因弹窗（答非所问/信息过时/太啰嗦/其它），选择后提交。
- 复制：`navigator.clipboard.writeText`，Toast"已复制"。

### 2.12 ClarifyCard

- 职责：展示澄清问题 + 选项按钮。
- 样式：浅蓝背景，圆角卡片，置于消息流底部。
- 选项按钮：点击即 `sendMessage(选项文本)`。
- 自由输入：用户仍可用 AssistantInput 输入其它回答。

### 2.13 TypingIndicator

- 职责：流式生成中的动画。
- 三点跳动动画（CSS `@keyframes`）。
- 仅在 isStreaming=true 且未收到首个 token 时显示。
- 收到首个 token 后由实际内容替代。

### 2.14 AssistantInput

- 职责：多行输入框 + 发送/停止按钮。
- 自动增高：单行 40px，最多 4 行（160px）。
- Enter 发送，Shift+Enter 换行。
- 流式中禁用输入（或允许输入但禁用发送，待当前轮完成）。
- 发送按钮：isStreaming 时变为停止按钮（Square 图标）。

### 2.15 HistoryDrawer

- 职责：从右侧滑出的历史会话列表。
- 分页：下拉加载更多。
- 点击：新建会话并预填"继续之前的对话：{摘要}"。
- 空状态：EmptyHistory 组件。

## 三、文件组织

```
frontend/src/
├── pages/
│   └── AssistantPage.tsx           # 页面入口
├── components/
│   └── assistant/
│       ├── AssistantHeader.tsx
│       ├── AssistantMessageList.tsx
│       ├── AssistantInput.tsx
│       ├── WelcomeCard.tsx
│       ├── MessageBubble.tsx
│       ├── UserBubble.tsx
│       ├── AssistantBubble.tsx
│       ├── CitationRenderer.tsx
│       ├── ReferenceList.tsx
│       ├── ToolCallStatus.tsx
│       ├── FeedbackButtons.tsx
│       ├── ClarifyCard.tsx
│       ├── TypingIndicator.tsx
│       └── HistoryDrawer.tsx
├── stores/
│   └── assistantStore.ts
├── services/
│   └── agentApi.ts                 # API 封装
└── hooks/
    └── useAgentStream.ts           # SSE hook
```

## 四、复用现有组件

| 现有组件 | 复用场景 |
|---------|---------|
| Toast | 反馈失败/网络错误提示 |
| ConfirmDialog（若有） | 清空会话确认 |
| Avatar | 助手头像用 Bot 图标代替 |
| Spinner | 历史加载 |

不强行复用 Bubble 组件（与私信 MessageBubble 差异大），独立实现。

## 五、Markdown 渲染

助手回答含 Markdown（列表/代码块/链接），需引入 `react-markdown` + `remark-gfm`：

```typescript
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

<ReactMarkdown remarkPlugins={[remarkGfm]} components={{
  a: ({href, children}) => <a href={href} className="text-blue-600 underline" target="_blank" rel="noopener">{children}</a>,
  code: ({inline, children}) => inline
    ? <code className="bg-gray-100 px-1 rounded">{children}</code>
    : <pre className="bg-gray-800 text-white p-3 rounded-lg overflow-x-auto"><code>{children}</code></pre>
}}>
  {content}
</ReactMarkdown>
```

- 不引入 `rehype-raw`（避免 XSS，禁止 HTML 标签注入）。
- 引用 `[n]` 在 Markdown 渲染后用正则后处理替换为 `<sup>`。

## 六、无障碍

- 所有按钮含 `aria-label`。
- 输入框含 `placeholder` 和 `aria-label`。
- 消息流区域 `role="log" aria-live="polite"`，让屏幕阅读器播报新消息。
- 颜色对比度满足 WCAG AA（蓝色 #2563EB 对白字对比度 4.5:1）。

## 七、决策记录 (ADR)

### ADR-124: 组件按功能拆分而非按层级
- **理由**：功能拆分（CitationRenderer/ReferenceList）便于单测与复用。层级拆分（Bubble/Content/Footer）耦合度高。
- **代价**：组件数量多（15+），但每个职责单一，维护成本低。

### ADR-125: 不复用私信 MessageBubble
- **理由**：私信气泡无引用/工具状态/Markdown/反馈，复用会引入大量条件分支。独立实现更清晰。
- **未来**：若私信也升级为富文本，再考虑抽象共享 BubbleShell。

### ADR-126: 引用渲染用正则后处理而非自写 Markdown 扩展
- **理由**：自写 remark 插件复杂度高。正则后处理 `[n]` → `<sup>` 简单可控。
- **风险**：若 Markdown 代码块内含 `[n]` 会被误替换。通过先渲染 Markdown 再在纯文本节点替换规避。

### ADR-127: 流式中允许输入但禁用发送
- **理由**：用户可能在 Agent 回答时就想好下一个问题，允许输入提升流畅度。禁用发送避免并发请求。
- **替代**：流式中清空输入框——用户需重新输入，体验差。

### ADR-128: 自动滚动 + 用户上滚暂停
- **理由**：默认自动滚动到底部便于看流式输出；用户上滚查看历史时若强制滚回底部会打断阅读。
- **检测**：监听 scroll 事件，距底部 >100px 视为用户上滚，暂停自动滚动；滚回底部后恢复。

### ADR-129: 历史会话不复活而是新建预填
- **理由**：见 [assistant-page-design.md](./assistant-page-design.md) ADR-111。
- **前端实现**：点击历史项 → createSession → sendMessage("继续之前的对话：{摘要}")。

### ADR-130: react-markdown 禁止 HTML 注入
- **理由**：助手回答可能含用户输入的反射内容，禁止 HTML 防 XSS。
- **实现**：不引入 rehype-raw，默认仅渲染 Markdown 语法。
