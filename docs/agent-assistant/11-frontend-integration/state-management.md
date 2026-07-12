# Zustand 状态管理

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

用 Zustand 管理 Agent 前端状态：当前会话、消息列表、流式状态、历史会话、引用详情缓存。状态独立于现有 AuthContext/ToastStore，避免耦合。

## 二、Store 设计

### 2.1 assistantStore（主 store）

```typescript
// frontend/src/stores/assistantStore.ts

import { create } from 'zustand'

interface Message {
  id: string              // turnId 或临时 ID
  role: 'user' | 'assistant' | 'tool'
  content: string
  refs?: Ref[]
  toolCalls?: ToolCallStatus[]
  feedback?: 'UP' | 'DOWN'
  interrupted?: boolean
  error?: { code: string; message: string; retryable: boolean }
  createdAt: number
}

interface AssistantState {
  // 会话状态
  sessionId: string | null
  sessionStatus: 'INIT' | 'ACTIVE' | 'WAITING_CLARIFY' | 'ARCHIVED' | 'ERROR'

  // 消息列表
  messages: Message[]

  // 流式状态
  isStreaming: boolean
  currentPhase: string | null   // intent_classifying / retrieving / generating...
  pendingClarify: { question: string; options: string[] } | null

  // 历史会话
  historySessions: SessionSummary[]
  historyLoading: boolean

  // Actions
  createSession: () => Promise<void>
  sendMessage: (content: string) => Promise<void>
  stopGeneration: () => void
  clearSession: () => void
  loadHistory: (page: number) => Promise<void>
  sendFeedback: (turnId: string, feedback: 'UP' | 'DOWN', reason?: string) => Promise<void>
  retryLast: () => void
}
```

### 2.2 选择器

为避免不必要的重渲染，提供细粒度选择器：

```typescript
export const useSessionId = () => useAssistantStore(s => s.sessionId)
export const useMessages = () => useAssistantStore(s => s.messages)
export const useIsStreaming = () => useAssistantStore(s => s.isStreaming)
export const useCurrentPhase = () => useAssistantStore(s => s.currentPhase)
export const usePendingClarify = () => useAssistantStore(s => s.pendingClarify)
export const useHistorySessions = () => useAssistantStore(s => s.historySessions)
export const useHasActiveSession = () => useAssistantStore(
  s => s.sessionId !== null && s.sessionStatus === 'ACTIVE'
)
```

## 三、Action 实现

### 3.1 createSession

```typescript
createSession: async () => {
  const res = await agentApi.createSession()
  set({
    sessionId: res.session_id,
    sessionStatus: 'ACTIVE',
    messages: [{
      id: 'welcome',
      role: 'assistant',
      content: res.welcome_message,
      createdAt: Date.now()
    }],
    isStreaming: false,
    pendingClarify: null,
  })
}
```

### 3.2 sendMessage

```typescript
sendMessage: async (content) => {
  const { sessionId } = get()
  if (!sessionId) return

  // 1. 立即追加用户消息（乐观更新）
  const userMsg: Message = {
    id: `user-${Date.now()}`,
    role: 'user', content,
    createdAt: Date.now()
  }
  set(state => ({
    messages: [...state.messages, userMsg],
    isStreaming: true,
    currentPhase: 'intent_classifying'
  }))

  // 2. 追加占位助手消息（流式填充）
  const assistantMsgId = `assistant-${Date.now()}`
  set(state => ({
    messages: [...state.messages, {
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      toolCalls: [],
      createdAt: Date.now()
    }]
  }))

  // 3. 启动 SSE
  const controller = new AbortController()
  get()._controller = controller

  await streamMessage({
    sessionId, content, requestId: uuid(),
    signal: controller.signal,
    onEvent: (evt) => get()._handleSSEEvent(evt, assistantMsgId),
    onError: (err) => get()._handleStreamError(err, assistantMsgId),
    onClose: () => set({ isStreaming: false, currentPhase: null }),
  })
}
```

### 3.3 _handleSSEEvent

```typescript
_handleSSEEvent: (evt, assistantMsgId) => {
  switch (evt.type) {
    case 'status':
      set({ currentPhase: evt.data.phase })
      break

    case 'tool':
      set(state => ({
        messages: state.messages.map(m =>
          m.id === assistantMsgId
            ? { ...m, toolCalls: [...(m.toolCalls||[]), evt.data] }
            : m
        )
      }))
      break

    case 'token':
      set(state => ({
        messages: state.messages.map(m =>
          m.id === assistantMsgId
            ? { ...m, content: m.content + evt.data.content }
            : m
        )
      }))
      break

    case 'refs':
      set(state => ({
        messages: state.messages.map(m =>
          m.id === assistantMsgId ? { ...m, refs: evt.data.refs } : m
        )
      }))
      break

    case 'clarify':
      set({ pendingClarify: evt.data })
      break

    case 'error':
      set(state => ({
        messages: state.messages.map(m =>
          m.id === assistantMsgId
            ? { ...m, error: evt.data }
            : m
        ),
        isStreaming: false
      }))
      break

    case 'done':
      set({ isStreaming: false, currentPhase: null })
      break
  }
}
```

### 3.4 stopGeneration

```typescript
stopGeneration: () => {
  get()._controller?.abort()
  set({ isStreaming: false, currentPhase: null })

  // 标记最后一条助手消息为 interrupted
  set(state => {
    const messages = [...state.messages]
    const last = messages[messages.length - 1]
    if (last && last.role === 'assistant') {
      messages[messages.length - 1] = { ...last, interrupted: true }
    }
    return { messages }
  })

  // 通知后端
  if (get().sessionId) {
    agentApi.interrupt(get().sessionId!, get()._lastRequestId)
  }
}
```

### 3.5 sendFeedback

```typescript
sendFeedback: async (turnId, feedback, reason) => {
  // 乐观更新
  set(state => ({
    messages: state.messages.map(m =>
      m.id === turnId ? { ...m, feedback } : m
    )
  }))

  try {
    await agentApi.sendFeedback(get().sessionId!, turnId, feedback, reason)
  } catch {
    // 回滚
    set(state => ({
      messages: state.messages.map(m =>
        m.id === turnId ? { ...m, feedback: undefined } : m
      )
    }))
    toast.error('反馈提交失败')
  }
}
```

## 四、持久化

### 4.1 会话 ID 持久化

用 `zustand/middleware persist` 持久化 sessionId 到 localStorage：

```typescript
export const useAssistantStore = create(
  persist(
    (set, get) => ({ ... }),
    {
      name: 'assistant-store',
      partialize: (state) => ({ sessionId: state.sessionId }),
      // 只持久化 sessionId，消息列表不持久化（每次从后端拉）
    }
  )
)
```

### 4.2 页面刷新恢复

页面刷新后：
1. 从 localStorage 读 sessionId。
2. 调 `GET /api/agent/sessions/{sessionId}/messages` 拉取历史消息。
3. 若会话已 ARCHIVED，提示"会话已过期，请新建"。

## 五、与现有 Store 的关系

| Store | 职责 | 与 assistantStore 关系 |
|-------|------|----------------------|
| AuthContext | 用户认证 | assistantStore 不直接读，通过 API 透传 token |
| ToastStore | Toast 通知 | assistantStore 调 `toast.error/success` |
| NavBar 的 unreadCount | 通知未读 | 独立，无交互 |

assistantStore 独立维护，不污染现有 store。

## 六、性能优化

### 6.1 消息列表分页渲染

- 仅渲染视口内消息 + 上下各 5 条缓冲。
- 用 `react-window` 或自实现虚拟滚动。
- MVP 阶段先全量渲染（<50 条无压力），>50 条再优化。

### 6.2 token 流节流

token 事件触发频率高（50-100/秒），直接 setState 会导致 50+ 次/秒重渲染。用 `requestAnimationFrame` 节流：

```typescript
let pendingTokens = ''
let rafId: number | null = null

function appendToken(token: string) {
  pendingTokens += token
  if (rafId === null) {
    rafId = requestAnimationFrame(() => {
      // 批量更新
      set(state => ({ ... }))  // 一次追加 pendingTokens
      pendingTokens = ''
      rafId = null
    })
  }
}
```

每帧（60fps）最多更新 1 次，渲染流畅。

### 6.3 选择器细粒度

避免 `useAssistantStore(s => s)` 订阅整个 store。每个组件只订阅需要的字段：

```typescript
// 差：整个 store 变化都重渲染
const store = useAssistantStore()

// 好：只订阅 messages
const messages = useAssistantStore(s => s.messages)
```

## 七、决策记录 (ADR)

### ADR-119: Zustand 而非 Redux/Context
- **理由**：项目已用 Zustand（见 README 技术栈），保持一致。Zustand 的细粒度选择器天然避免 Context 的全量重渲染问题。
- **替代**：Redux 过重；Context 在 token 流高频更新下性能差。

### ADR-120: 只持久化 sessionId 不持久化消息
- **理由**：消息可能很长（含引用/工具调用），localStorage 5MB 限制下易爆。sessionId 持久化足够，消息从后端拉取保证一致性。
- **代价**：刷新页面有短暂加载（骨架屏）。

### ADR-121: 乐观更新 + 失败回滚
- **理由**：用户消息立即显示（不等后端确认），反馈及时。后端失败时回滚，避免误导。
- **场景**：feedback 标记失败时回滚 👍/👎 状态。

### ADR-122: token 流用 requestAnimationFrame 节流
- **理由**：60fps 足够流畅，且与浏览器渲染周期对齐，避免抖动。
- **替代**：setTimeout 节流——与渲染周期不对齐，可能丢帧。

### ADR-123: assistantStore 独立不污染现有 store
- **理由**：Agent 是新功能模块，状态隔离便于维护与未来重构。现有 store 不感知 Agent 存在。
- **协作**：通过 toast 函数和 API 层交互，不直接 import 对方 store。
