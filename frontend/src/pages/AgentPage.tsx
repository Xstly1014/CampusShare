import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Send, Bot, User, Sparkles, Plus, Trash2, Menu, X, MessageSquare } from 'lucide-react'
import { agentApi, chatStream } from '../services/agent'
import type { AgentSession, AgentTurn } from '../services/agent'
import { toast } from '../stores/toastStore'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/common/NavBar'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp?: string
}

const WELCOME_MESSAGE = `你好！我是 CampusShare 智能助手 👋

我可以帮助你：
• 📚 **使用教程** — 解答平台操作问题（发帖、收藏、认证等）
• 🔍 **资源检索** — 帮你查找学习资料、帖子和讨论
• 💡 **帮助中心** — 解决账号、通知、消息等常见问题
• 💬 **智能问答** — 回答校园生活和学习相关问题

有什么可以帮你的吗？`

export default function AgentPage() {
  const navigate = useNavigate()
  const { user } = useAuth()

  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE },
  ])
  const [inputValue, setInputValue] = useState('')
  const [sending, setSending] = useState(false)
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null)
  const [sessions, setSessions] = useState<AgentSession[]>([])
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [streaming, setStreaming] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<boolean>(false)
  const streamContentRef = useRef<string>('')

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streaming])

  const fetchSessions = useCallback(async () => {
    try {
      const res = await agentApi.getSessions()
      setSessions(res.data || [])
    } catch { /* ignore */ }
  }, [])

  useEffect(() => {
    fetchSessions()
  }, [fetchSessions])

  const loadSession = async (sessionId: string) => {
    setLoadingHistory(true)
    setSidebarOpen(false)
    try {
      const turnsRes = await agentApi.getSessionTurns(sessionId)
      const turns = turnsRes.data || []

      const loadedMessages: ChatMessage[] = []
      for (const turn of turns) {
        if (turn.userMessage) {
          loadedMessages.push({
            id: `u-${turn.id}`,
            role: 'user',
            content: turn.userMessage,
            timestamp: turn.createdAt,
          })
        }
        if (turn.assistantMessage) {
          loadedMessages.push({
            id: `a-${turn.id}`,
            role: 'assistant',
            content: turn.assistantMessage,
            timestamp: turn.createdAt,
          })
        }
      }

      if (loadedMessages.length > 0) {
        setMessages(loadedMessages)
      } else {
        setMessages([{ id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE }])
      }
      setCurrentSessionId(sessionId)
    } catch {
      toast.error('加载会话失败')
    } finally {
      setLoadingHistory(false)
    }
  }

  const startNewChat = () => {
    setMessages([{ id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE }])
    setCurrentSessionId(null)
    setSidebarOpen(false)
  }

  const deleteSession = async (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation()
    try {
      await agentApi.deleteSession(sessionId)
      setSessions(prev => prev.filter(s => s.id !== sessionId))
      if (currentSessionId === sessionId) {
        startNewChat()
      }
      toast.success('会话已删除')
    } catch {
      toast.error('删除失败')
    }
  }

  const handleSend = async () => {
    const content = inputValue.trim()
    if (!content || sending) return

    const userMsgId = `u-${Date.now()}`
    const assistantMsgId = `a-${Date.now()}`

    setMessages(prev => [
      ...prev,
      { id: userMsgId, role: 'user', content },
    ])
    setInputValue('')
    setSending(true)
    setStreaming(true)
    abortRef.current = false
    streamContentRef.current = ''

    setMessages(prev => [
      ...prev,
      { id: assistantMsgId, role: 'assistant', content: '' },
    ])

    try {
      await chatStream(content, currentSessionId, {
        onDelta: (delta: string) => {
          if (abortRef.current) return
          streamContentRef.current += delta
          setMessages(prev => prev.map(m =>
            m.id === assistantMsgId
              ? { ...m, content: streamContentRef.current }
              : m
          ))
        },
        onDone: () => {
          setStreaming(false)
          fetchSessions()
        },
        onError: (message: string) => {
          setStreaming(false)
          if (!streamContentRef.current) {
            setMessages(prev => prev.map(m =>
              m.id === assistantMsgId
                ? { ...m, content: `抱歉，出错了：${message}` }
                : m
            ))
          } else {
            toast.error(message)
          }
          fetchSessions()
        },
      }).then(({ sessionId: newSessionId }) => {
        if (newSessionId && !currentSessionId) {
          setCurrentSessionId(newSessionId)
        }
      })
    } catch (e) {
      setStreaming(false)
      toast.error((e as Error).message || '发送失败')
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
          >
            <Menu className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
              <Sparkles className="w-4 h-4 text-white" />
            </div>
            <span className="text-sm font-semibold text-gray-900">CampusShare AI</span>
          </div>
          <button
            onClick={startNewChat}
            className="p-1.5 -mr-1.5 hover:bg-gray-100 rounded-full transition-colors"
            title="新对话"
          >
            <Plus className="w-5 h-5 text-gray-600" />
          </button>
        </div>
      </div>

      {/* Sidebar Overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/30 z-40" onClick={() => setSidebarOpen(false)}>
          <div
            className="absolute left-0 top-0 bottom-0 w-72 bg-white shadow-xl flex flex-col"
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
              <span className="text-sm font-semibold text-gray-900">历史会话</span>
              <button onClick={() => setSidebarOpen(false)} className="p-1 hover:bg-gray-100 rounded-full">
                <X className="w-5 h-5 text-gray-500" />
              </button>
            </div>
            <div className="p-3">
              <button
                onClick={startNewChat}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-blue-50 text-blue-600 rounded-xl text-sm font-medium hover:bg-blue-100 transition-colors"
              >
                <Plus className="w-4 h-4" />
                新建对话
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1">
              {sessions.length > 0 ? sessions.map(s => (
                <div
                  key={s.id}
                  onClick={() => loadSession(s.id)}
                  className={`group flex items-center gap-2 px-3 py-2.5 rounded-xl cursor-pointer transition-colors ${
                    currentSessionId === s.id ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'
                  }`}
                >
                  <MessageSquare className="w-4 h-4 flex-shrink-0 text-gray-400" />
                  <span className="text-sm truncate flex-1">{s.title || '新对话'}</span>
                  <button
                    onClick={(e) => deleteSession(e, s.id)}
                    className="p-1 text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )) : (
                <div className="text-center py-8">
                  <MessageSquare className="w-8 h-8 text-gray-200 mx-auto mb-2" />
                  <p className="text-xs text-gray-400">暂无历史会话</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Messages */}
      <div className="flex-1 max-w-3xl mx-auto w-full px-4 py-4 overflow-y-auto">
        {loadingHistory ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
          </div>
        ) : (
          <div className="space-y-4">
            {messages.map((msg) => {
              const isUser = msg.role === 'user'
              return (
                <div key={msg.id} className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                    isUser
                      ? 'bg-gradient-to-br from-blue-500 to-blue-600'
                      : 'bg-gradient-to-br from-purple-500 to-blue-600'
                  }`}>
                    {isUser ? (
                      user?.avatarUrl ? (
                        <img
                          src={user.avatarUrl.startsWith('/files/') ? `/api${user.avatarUrl}` : user.avatarUrl}
                          alt={user.username}
                          className="w-full h-full object-cover rounded-full"
                        />
                      ) : (
                        <User className="w-4 h-4 text-white" />
                      )
                    ) : (
                      <Bot className="w-4 h-4 text-white" />
                    )}
                  </div>
                  <div className={`max-w-[80%] ${isUser ? 'items-end' : 'items-start'} flex flex-col`}>
                    <div className={`px-4 py-2.5 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap ${
                      isUser
                        ? 'bg-blue-600 text-white rounded-tr-sm'
                        : 'bg-white border border-gray-100 text-gray-700 rounded-tl-sm shadow-sm'
                    }`}>
                      {msg.content}
                      {!isUser && msg.id === messages[messages.length - 1]?.id && streaming && msg.content && (
                        <span className="inline-block w-1.5 h-4 bg-blue-500 ml-0.5 align-middle animate-pulse" />
                      )}
                      {!isUser && msg.id === messages[messages.length - 1]?.id && streaming && !msg.content && (
                        <span className="inline-flex gap-1">
                          <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                          <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                          <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input */}
      <div className="bg-white border-t border-gray-100">
        <div className="max-w-3xl mx-auto px-4 py-3 flex items-end gap-3">
          <div className="flex-1 relative">
            <textarea
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入你的问题..."
              disabled={sending}
              rows={1}
              className="w-full px-4 py-2.5 bg-gray-100 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all resize-none disabled:opacity-60 disabled:cursor-not-allowed max-h-32"
              style={{ minHeight: '42px' }}
            />
          </div>
          <button
            onClick={handleSend}
            disabled={!inputValue.trim() || sending}
            className="p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
        <p className="text-xs text-gray-400 text-center pb-2">AI 回答仅供参考，请核实重要信息</p>
      </div>

      <NavBar />
    </div>
  )
}
