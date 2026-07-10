# SSE 客户端实现

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

封装一个 SSE 客户端，处理 POST 流式请求、事件解析、断线重连、中断、错误。供 AssistantPage 调用。

## 二、API 设计

```typescript
// frontend/src/services/agentStream.ts

export interface SSEEvent {
  type: 'status' | 'tool' | 'token' | 'refs' | 'clarify' | 'error' | 'done'
  data: any
  id?: number  // 事件序号，用于重连
}

export interface StreamOptions {
  sessionId: string
  content: string
  requestId: string
  onEvent: (event: SSEEvent) => void
  onError: (error: Error) => void
  onClose: () => void
  signal?: AbortSignal  // 用于中断
}

export async function streamMessage(opts: StreamOptions): Promise<void>
```

## 三、实现

```typescript
import { v4 as uuid } from 'uuid'

export async function streamMessage(opts: StreamOptions): Promise<void> {
  const { sessionId, content, requestId, onEvent, onError, onClose, signal } = opts

  let response: Response
  try {
    response = await fetch(`/api/agent/sessions/${sessionId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getToken()}`,
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({ request_id: requestId, content }),
      signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      onClose()
      return
    }
    onError(e as Error)
    return
  }

  if (!response.ok) {
    onError(new Error(`HTTP ${response.status}`))
    return
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let lastEventId = 0

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const rawEvent of events) {
        const parsed = parseEvent(rawEvent)
        if (parsed) {
          lastEventId = parsed.id || lastEventId
          onEvent(parsed)
        }
      }
    }
    onClose()
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      onClose()
    } else {
      onError(e as Error)
    }
  }
}

function parseEvent(raw: string): SSEEvent | null {
  const lines = raw.split('\n')
  let type: SSEEvent['type'] | null = null
  let data = ''
  let id: number | undefined

  for (const line of lines) {
    if (line.startsWith('event:')) {
      type = line.slice(6).trim() as SSEEvent['type']
    } else if (line.startsWith('data:')) {
      data += line.slice(5).trim()
    } else if (line.startsWith('id:')) {
      id = parseInt(line.slice(3).trim(), 10)
    }
  }

  if (!type) return null
  return { type, data: data ? JSON.parse(data) : {}, id }
}
```

## 四、中断

```typescript
const controller = new AbortController()
streamMessage({
  sessionId, content, requestId: uuid(),
  onEvent: handleEvent,
  onError: handleError,
  onClose: handleClose,
  signal: controller.signal,
})

// 用户点击"停止"
controller.abort()
// 同时调后端中断接口
await fetch(`/api/agent/sessions/${sessionId}/interrupt`, {
  method: 'POST',
  body: JSON.stringify({ request_id: requestId }),
})
```

## 五、断线重连

### 5.1 自动重连

```typescript
async function streamWithReconnect(opts: StreamOptions, maxRetries = 2) {
  let lastEventId = 0
  let retries = 0

  const wrappedOnEvent = (evt: SSEEvent) => {
    lastEventId = evt.id || lastEventId
    opts.onEvent(evt)
  }

  while (retries <= maxRetries) {
    try {
      await streamMessage({ ...opts, onEvent: wrappedOnEvent })
      return
    } catch (e) {
      retries++
      if (retries > maxRetries) {
        opts.onError(e as Error)
        return
      }
      await new Promise(r => setTimeout(r, 1000 * retries))
    }
  }
}
```

### 5.2 重连续传

- 重连时若 `lastEventId > 0`，在请求头加 `Last-Event-ID: {lastEventId}`。
- 后端从 lastEventId+1 回放 token 事件（见 [sse-streaming-api.md](../10-tools-and-apis/sse-streaming-api.md) ADR-093）。
- 仅在 30 秒内重连有效，超出后端返回 done。

## 六、React Hook 封装

```typescript
// frontend/src/hooks/useAgentStream.ts

export function useAgentStream(sessionId: string | null) {
  const [isStreaming, setIsStreaming] = useState(false)
  const [events, setEvents] = useState<SSEEvent[]>([])
  const controllerRef = useRef<AbortController | null>(null)

  const send = useCallback(async (content: string) => {
    if (!sessionId || isStreaming) return
    setIsStreaming(true)
    setEvents([])
    const controller = new AbortController()
    controllerRef.current = controller

    await streamMessage({
      sessionId, content, requestId: uuid(),
      onEvent: (evt) => setEvents(prev => [...prev, evt]),
      onError: (err) => {
        toast.error('助手响应失败：' + err.message)
        setIsStreaming(false)
      },
      onClose: () => setIsStreaming(false),
      signal: controller.signal,
    })
  }, [sessionId, isStreaming])

  const stop = useCallback(() => {
    controllerRef.current?.abort()
    setIsStreaming(false)
  }, [])

  return { isStreaming, events, send, stop }
}
```

## 七、Token 拼接

events 数组中 token 事件按顺序拼接为完整回答：

```typescript
const answerText = useMemo(() => {
  return events
    .filter(e => e.type === 'token')
    .map(e => e.data.content)
    .join('')
}, [events])
```

引用列表从 refs 事件取：

```typescript
const refs = useMemo(() => {
  const refsEvent = events.find(e => e.type === 'refs')
  return refsEvent?.data.refs || []
}, [events])
```

## 八、错误处理

### 8.1 网络错误

- fetch 抛异常 → onError 触发 → Toast 提示"网络异常，请检查连接"。
- 不自动重试（避免重复扣费），用户手动点"重试"。

### 8.2 HTTP 4xx

- 401：token 过期，跳转登录页。
- 429：限流，Toast"提问太频繁，请稍后再试"。
- 404：会话不存在，提示"会话已失效，请新建"。
- 500：服务异常，Toast"服务异常，请稍后再试"。

### 8.3 SSE error 事件

后端通过 SSE error 事件主动报错（如 LLM 超时），前端处理：
- 显示错误气泡 + partial_answer。
- retryable=true 时显示"重试"按钮。

## 九、内存管理

- events 数组随轮次增长，单轮结束后保留在消息列表中，但清空 events 状态。
- 长会话（>50 轮）时，前端只渲染最近 20 轮，更早的折叠为"查看更多"。
- 避免一次性渲染上千条 DOM 节点导致卡顿。

## 十、决策记录 (ADR)

### ADR-113: fetch + ReadableStream 而非 EventSource
- **理由**：见 [sse-streaming-api.md](../10-tools-and-apis/sse-streaming-api.md) ADR-092。POST 请求不能用 EventSource。
- **代价**：手动解析 SSE 格式，但代码量 <100 行，可接受。

### ADR-114: AbortController 中断而非自定义标志位
- **理由**：AbortController 是浏览器原生 API，能真正中断 fetch，释放网络连接。自定义标志位只能忽略后续事件，连接仍占用。
- **配合**：同时调后端 interrupt 接口，让服务端也停止生成。

### ADR-115: 不自动重试网络错误
- **理由**：重试会重复扣费 + 重复生成。让用户主动重试更安全。
- **例外**：仅 SSE 连接中途断开（已开始流式）才自动重连续传，且仅续传 token 事件。

### ADR-116: events 数组单轮清空
- **理由**：events 是"当前轮的流式事件"，轮次结束后归档到 messages 列表。不清空会导致下一轮事件叠加。
- **保留**：messages 列表长期保留，支持滚动查看历史。

### ADR-117: 长会话只渲染最近 20 轮
- **理由**：DOM 节点过多（>1000）会导致滚动卡顿。虚拟滚动实现复杂，折叠历史更简单。
- **未来**：若用户反馈需要查看更早内容，引入 react-window 虚拟滚动。

### ADR-118: token 拼接用 useMemo
- **理由**：events 数组每次更新都触发重渲染，useMemo 缓存 answerText 避免不必要的字符串拼接。
- **优化**：events.filter + join 在 events 长度 >100 时有性能损耗，未来可改用 reducer 累积。
