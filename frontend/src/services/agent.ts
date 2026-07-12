import { api, API_BASE_URL } from './http'

export interface AgentSession {
  id: string
  userId: string
  title: string
  status: string
  messageCount: number
  lastMessageAt?: string
  categoryId?: string
  createdAt: string
  updatedAt: string
}

export interface AgentCategory {
  id: string
  userId: string
  name: string
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface AgentTurn {
  id: string
  sessionId: string
  turnNumber: number
  userMessage: string
  assistantMessage: string
  messageRole: string
  tokensUsed?: number
  modelName?: string
  responseTimeMs?: number
  status: string
  errorMessage?: string
  createdAt: string
  refs?: ChatRef[]
  navigate?: ChatNavigate
}

export interface ChatRef {
  index: number
  id: string
  type: 'POST' | 'KNOWLEDGE'
  title: string
  url: string | null
}

export interface ChatNavigate {
  route: string
  label: string
}

export interface ChatStreamCallbacks {
  onDelta: (content: string) => void
  onRefs: (refs: ChatRef[]) => void
  onNavigate?: (navigate: ChatNavigate) => void
  onDone: () => void
  onError: (message: string) => void
}

function getAuthHeaders(): Record<string, string> {
  const token = sessionStorage.getItem('campusshare_token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

export async function chatStream(
  message: string,
  sessionId: string | null,
  callbacks: ChatStreamCallbacks,
): Promise<{ sessionId: string | null }> {
  const response = await fetch(`${API_BASE_URL}/agent/chat`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ message, sessionId: sessionId || undefined }),
  })

  if (!response.ok) {
    const errText = await response.text().catch(() => '请求失败')
    callbacks.onError(errText)
    return { sessionId: null }
  }

  const reader = response.body?.getReader()
  if (!reader) {
    callbacks.onError('无法获取响应流')
    return { sessionId: null }
  }

  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'delta'
  let dataBuffer = ''
  let extractedSessionId: string | null = sessionId

  const dispatchEvent = () => {
    const data = dataBuffer
    dataBuffer = ''
    if (!data && currentEvent !== 'done') return
    switch (currentEvent) {
      case 'delta':
        callbacks.onDelta(data)
        break
      case 'done':
        callbacks.onDone()
        break
      case 'error':
        callbacks.onError(data)
        break
      case 'session':
        try {
          const parsed = JSON.parse(data)
          if (parsed.sessionId) extractedSessionId = parsed.sessionId
        } catch {
          /* ignore */
        }
        break
      case 'refs':
        try {
          const parsed = JSON.parse(data)
          if (Array.isArray(parsed)) callbacks.onRefs(parsed)
        } catch {
          /* ignore */
        }
        break
      case 'navigate':
        try {
          const parsed = JSON.parse(data)
          if (parsed.route) callbacks.onNavigate?.(parsed)
        } catch {
          /* ignore */
        }
        break
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const rawLine of lines) {
        const line = rawLine.replace(/\r$/, '')
        if (line === '') {
          dispatchEvent()
          continue
        }
        if (line.startsWith(':')) continue
        if (line.startsWith('event:')) {
          if (dataBuffer) dispatchEvent()
          currentEvent = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          if (dataBuffer) dataBuffer += '\n'
          dataBuffer += line.slice(5).replace(/^ /, '')
          continue
        }
      }
    }
    if (dataBuffer) dispatchEvent()
  } catch (e) {
    callbacks.onError((e as Error).message || '流读取异常')
  } finally {
    reader.releaseLock()
  }

  return { sessionId: extractedSessionId }
}

export const agentApi = {
  createSession: (title?: string) =>
    api.post<AgentSession>('/agent/sessions', title ? { title } : {}),

  getSessions: () => api.get<AgentSession[]>('/agent/sessions'),

  getSession: (sessionId: string) => api.get<AgentSession>(`/agent/sessions/${sessionId}`),

  getSessionTurns: (sessionId: string) =>
    api.get<AgentTurn[]>(`/agent/sessions/${sessionId}/turns`),

  archiveSession: (sessionId: string) => api.put<void>(`/agent/sessions/${sessionId}/archive`),

  deleteSession: (sessionId: string) => api.delete<void>(`/agent/sessions/${sessionId}`),

  getCategories: () => api.get<AgentCategory[]>('/agent/categories'),

  createCategory: (name: string) => api.post<AgentCategory>('/agent/categories', { name }),

  renameCategory: (categoryId: string, name: string) =>
    api.put<AgentCategory>(`/agent/categories/${categoryId}`, { name }),

  deleteCategory: (categoryId: string) => api.delete<void>(`/agent/categories/${categoryId}`),

  moveSessionCategory: (sessionId: string, categoryId: string | null) =>
    api.put<AgentSession>(`/agent/sessions/${sessionId}/category`, { categoryId }),
}
