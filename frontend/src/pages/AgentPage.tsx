import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import {
  Send,
  Bot,
  User,
  Sparkles,
  Plus,
  Trash2,
  Menu,
  X,
  MessageSquare,
  Folder,
  FolderOpen,
  ChevronDown,
  ChevronRight,
  FolderInput,
  MoreVertical,
  Pencil,
  ArrowRight,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { agentApi, chatStream } from '../services/agent'
import type { AgentSession, AgentCategory, ChatRef, ChatNavigate } from '../services/agent'
import { toast } from '../stores/toastStore'
import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import NavBar from '../components/common/NavBar'
import SwipeToDelete from '../components/common/SwipeToDelete'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp?: string
  refs?: ChatRef[]
  navigate?: ChatNavigate
}

const WELCOME_MESSAGE = `你好！我是 CampusShare 智能助手 👋

我可以帮助你：
• 📚 **使用教程** — 解答平台操作问题（发帖、收藏、认证等）
• 🔍 **资源检索** — 帮你查找学习资料、帖子和讨论
• 💡 **帮助中心** — 解决账号、通知、消息等常见问题
• 💬 **智能问答** — 回答校园生活和学习相关问题

有什么可以帮你的吗？`

const SESSION_STORAGE_KEY = 'agent_current_session_id'

export default function AgentPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE },
  ])
  const [inputValue, setInputValue] = useState('')
  const [sending, setSending] = useState(false)
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(() => {
    return localStorage.getItem(SESSION_STORAGE_KEY)
  })
  const [sessions, setSessions] = useState<AgentSession[]>([])
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [chatKey, setChatKey] = useState(0)

  const [categories, setCategories] = useState<AgentCategory[]>([])
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({})
  const [openSwipeId, setOpenSwipeId] = useState<string | null>(null)
  const [openMenuCategoryId, setOpenMenuCategoryId] = useState<string | null>(null)
  const [showCategoryModal, setShowCategoryModal] = useState(false)
  const [editingCategory, setEditingCategory] = useState<AgentCategory | null>(null)
  const [categoryName, setCategoryName] = useState('')
  const [showMovePicker, setShowMovePicker] = useState(false)
  const [moveTargetSessionId, setMoveTargetSessionId] = useState<string | null>(null)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<boolean>(false)
  const streamContentRef = useRef<string>('')
  // 初次进入页面或切换会话时使用瞬时滚动，避免"从顶部滑到底部"的动画；
  // 后续新消息追加/流式输出仍使用平滑滚动以跟随新内容
  const shouldInstantScrollRef = useRef(true)

  useEffect(() => {
    if (messages.length > 0) {
      messagesEndRef.current?.scrollIntoView({
        behavior: shouldInstantScrollRef.current ? 'auto' : 'smooth',
      })
      shouldInstantScrollRef.current = false
    }
  }, [messages, streaming])

  const fetchSessions = useCallback(async () => {
    try {
      const res = await agentApi.getSessions()
      setSessions(res.data || [])
    } catch {
      /* ignore */
    }
  }, [])

  const fetchCategories = useCallback(async () => {
    try {
      const res = await agentApi.getCategories()
      setCategories(res.data || [])
    } catch {
      /* ignore */
    }
  }, [])

  useEffect(() => {
    fetchSessions()
    fetchCategories()
  }, [fetchSessions, fetchCategories])

  const groupedSessions = useMemo(() => {
    const groups: Record<string, AgentSession[]> = { uncategorized: [] }
    for (const cat of categories) groups[cat.id] = []
    for (const s of sessions) {
      const key =
        s.categoryId && categories.some((c) => c.id === s.categoryId)
          ? s.categoryId!
          : 'uncategorized'
      groups[key]!.push(s)
    }
    return groups
  }, [sessions, categories])

  const toggleGroup = (key: string) => {
    setExpandedGroups((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  const closeSidebar = () => {
    setSidebarOpen(false)
    setOpenSwipeId(null)
    setOpenMenuCategoryId(null)
  }

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

      // 切换会话时使用瞬时滚动定位到底部，避免从顶部滑下的动画
      shouldInstantScrollRef.current = true
      if (loadedMessages.length > 0) {
        setMessages(loadedMessages)
      } else {
        setMessages([{ id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE }])
      }
      setCurrentSessionId(sessionId)
      localStorage.setItem(SESSION_STORAGE_KEY, sessionId)
    } catch {
      localStorage.removeItem(SESSION_STORAGE_KEY)
      setCurrentSessionId(null)
      shouldInstantScrollRef.current = true
      setMessages([{ id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE }])
      toast.error('加载会话失败')
    } finally {
      setLoadingHistory(false)
    }
  }

  useEffect(() => {
    const savedId = localStorage.getItem(SESSION_STORAGE_KEY)
    if (savedId) {
      loadSession(savedId)
    }
  }, [])

  const startNewChat = () => {
    localStorage.removeItem(SESSION_STORAGE_KEY)
    // 新对话使用瞬时滚动，避免从原会话位置滑回顶部的动画
    shouldInstantScrollRef.current = true
    setMessages([{ id: 'welcome', role: 'assistant', content: WELCOME_MESSAGE }])
    setCurrentSessionId(null)
    setSidebarOpen(false)
    setChatKey((k) => k + 1)
    toast.success('已开始新对话')
  }

  const handleSwipeDelete = async (sessionId: string) => {
    try {
      await agentApi.deleteSession(sessionId)
      setSessions((prev) => prev.filter((s) => s.id !== sessionId))
      setOpenSwipeId(null)
      if (currentSessionId === sessionId) {
        startNewChat()
      }
      toast.success('会话已删除')
    } catch {
      toast.error('删除失败')
    }
  }

  const handleDeleteCategory = async (categoryId: string) => {
    try {
      await agentApi.deleteCategory(categoryId)
      await fetchCategories()
      await fetchSessions()
      setOpenMenuCategoryId(null)
      toast.success('分类已删除')
    } catch {
      toast.error('删除分类失败')
    }
  }

  const handleSaveCategory = async () => {
    const name = categoryName.trim()
    if (!name) {
      toast.error('分类名不能为空')
      return
    }
    try {
      if (editingCategory) {
        await agentApi.renameCategory(editingCategory.id, name)
        toast.success('分类已更新')
      } else {
        await agentApi.createCategory(name)
        toast.success('分类已创建')
      }
      await fetchCategories()
      setShowCategoryModal(false)
      setEditingCategory(null)
      setCategoryName('')
    } catch {
      toast.error(editingCategory ? '更新失败' : '创建失败')
    }
  }

  const handleMoveToCategory = async (categoryId: string | null) => {
    if (!moveTargetSessionId) return
    try {
      await agentApi.moveSessionCategory(moveTargetSessionId, categoryId)
      await fetchSessions()
      setShowMovePicker(false)
      setMoveTargetSessionId(null)
      toast.success(categoryId ? '已移入分类' : '已移出分类')
    } catch {
      toast.error('移动失败')
    }
  }

  const openMovePicker = (sessionId: string) => {
    setMoveTargetSessionId(sessionId)
    setOpenSwipeId(null)
    setShowMovePicker(true)
  }

  const openCreateCategory = () => {
    setEditingCategory(null)
    setCategoryName('')
    setShowCategoryModal(true)
  }

  const openRenameCategory = (cat: AgentCategory) => {
    setEditingCategory(cat)
    setCategoryName(cat.name)
    setOpenMenuCategoryId(null)
    setShowCategoryModal(true)
  }

  const handleSend = async () => {
    const content = inputValue.trim()
    if (!content || sending) return

    const userMsgId = `u-${Date.now()}`
    const assistantMsgId = `a-${Date.now()}`

    setMessages((prev) => [...prev, { id: userMsgId, role: 'user', content }])
    setInputValue('')
    setSending(true)
    setStreaming(true)
    abortRef.current = false
    streamContentRef.current = ''

    setMessages((prev) => [...prev, { id: assistantMsgId, role: 'assistant', content: '' }])

    try {
      await chatStream(content, currentSessionId, {
        onDelta: (delta: string) => {
          if (abortRef.current) return
          streamContentRef.current += delta
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId ? { ...m, content: streamContentRef.current } : m,
            ),
          )
        },
        onRefs: (refs: ChatRef[]) => {
          setMessages((prev) => prev.map((m) => (m.id === assistantMsgId ? { ...m, refs } : m)))
        },
        onNavigate: (nav: ChatNavigate) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantMsgId ? { ...m, navigate: nav } : m)),
          )
        },
        onDone: () => {
          setStreaming(false)
          fetchSessions()
        },
        onError: (message: string) => {
          setStreaming(false)
          if (!streamContentRef.current) {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId ? { ...m, content: `抱歉，出错了：${message}` } : m,
              ),
            )
          } else {
            toast.error(message)
          }
          fetchSessions()
        },
      }).then(({ sessionId: newSessionId }) => {
        if (newSessionId && !currentSessionId) {
          setCurrentSessionId(newSessionId)
          localStorage.setItem(SESSION_STORAGE_KEY, newSessionId)
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

  // ========== 引用渲染 ==========

  const preprocessContentWithRefs = (content: string, refs?: ChatRef[]) => {
    if (!refs || refs.length === 0) return content
    return content.replace(/\[(\d+)\]/g, (match, numStr) => {
      const num = parseInt(numStr, 10)
      const ref = refs.find((r) => r.index === num)
      if (ref) {
        return `[\`${num}\`](#ref-${num})`
      }
      return match
    })
  }

  const markdownComponents = (refs?: ChatRef[]) => ({
    a: ({
      href,
      children,
      ...props
    }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { children?: React.ReactNode }) => {
      const refMatch = href?.match(/^#ref-(\d+)$/)
      if (refMatch && refs) {
        const refNum = parseInt(refMatch[1]!, 10)
        const ref = refs.find((r) => r.index === refNum)
        if (ref) {
          return (
            <span
              className="inline-flex items-center justify-center min-w-[18px] min-h-[18px] px-1 mx-0.5 text-[10px] font-semibold text-white bg-blue-500 rounded-full cursor-pointer hover:bg-blue-600 transition-colors align-super"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                if (ref.url) navigate(ref.url)
              }}
              {...props}
            >
              {refNum}
            </span>
          )
        }
      }
      return (
        <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
          {children}
        </a>
      )
    },
  })

  const RefCardList = ({ refs }: { refs: ChatRef[] }) => {
    if (!refs || refs.length === 0) return null
    return (
      <div className="mt-2 space-y-1.5">
        <div className="text-xs text-gray-400 font-medium">引用来源</div>
        {refs.map((ref) => {
          const clickable = !!ref.url
          return (
            <div
              key={ref.id}
              onClick={() => clickable && navigate(ref.url!)}
              className={`flex items-start gap-2 p-2 rounded-lg border transition-colors ${
                clickable
                  ? 'bg-blue-50 border-blue-100 cursor-pointer hover:bg-blue-100'
                  : 'bg-gray-50 border-gray-100 cursor-default'
              }`}
            >
              <span
                className={`flex-shrink-0 w-5 h-5 rounded-full text-white text-xs flex items-center justify-center font-semibold mt-0.5 ${
                  clickable ? 'bg-blue-500' : 'bg-gray-400'
                }`}
              >
                {ref.index}
              </span>
              <div className="flex-1 min-w-0">
                <div
                  className={`text-sm font-medium truncate ${
                    clickable ? 'text-blue-700' : 'text-gray-600'
                  }`}
                >
                  {ref.title}
                </div>
                <div className={`text-xs ${clickable ? 'text-blue-400' : 'text-gray-400'}`}>
                  {ref.type === 'POST' ? '帖子' : '知识库'}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    )
  }

  const NavigateCard = ({ nav }: { nav: ChatNavigate }) => (
    <div
      onClick={() => navigate(nav.route)}
      className="mt-2 flex items-center gap-3 p-3 bg-gradient-to-r from-blue-500 to-blue-600 rounded-xl cursor-pointer hover:from-blue-600 hover:to-blue-700 transition-all shadow-md active:scale-[0.98]"
    >
      <div className="flex-shrink-0 w-10 h-10 bg-white/20 rounded-full flex items-center justify-center">
        <ArrowRight className="w-5 h-5 text-white" />
      </div>
      <div className="flex-1">
        <div className="text-white font-medium text-sm">{nav.label}</div>
        <div className="text-blue-100 text-xs">点击前往</div>
      </div>
      <ChevronRight className="w-5 h-5 text-white/70" />
    </div>
  )

  const renderSessionItem = (s: AgentSession) => (
    <SwipeToDelete
      key={s.id}
      isOpen={openSwipeId === s.id}
      onOpenChange={(open) => setOpenSwipeId(open ? s.id : null)}
      onDelete={() => handleSwipeDelete(s.id)}
      onMove={() => openMovePicker(s.id)}
    >
      <div
        onClick={() => {
          if (openSwipeId === s.id) {
            setOpenSwipeId(null)
            return
          }
          loadSession(s.id)
        }}
        className={`flex items-center gap-2 px-3 py-2.5 cursor-pointer ${
          currentSessionId === s.id ? 'text-blue-700' : 'text-gray-700'
        }`}
      >
        <MessageSquare className="w-4 h-4 flex-shrink-0 text-gray-400" />
        <span className="text-sm truncate flex-1">{s.title || '新对话'}</span>
      </div>
    </SwipeToDelete>
  )

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col pb-16">
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
            className="p-1.5 -mr-1.5 hover:bg-blue-50 active:scale-90 rounded-full transition-all duration-150 group"
            title="新对话"
          >
            <Plus className="w-5 h-5 text-gray-600 group-hover:text-blue-600 transition-colors" />
          </button>
        </div>
      </div>

      {/* Sidebar Overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/30 z-40" onClick={closeSidebar}>
          <div
            className="absolute left-0 top-0 bottom-0 w-72 bg-white shadow-xl flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
              <span className="text-sm font-semibold text-gray-900">历史会话</span>
              <button onClick={closeSidebar} className="p-1 hover:bg-gray-100 rounded-full">
                <X className="w-5 h-5 text-gray-500" />
              </button>
            </div>
            <div className="p-3 space-y-2">
              <button
                onClick={startNewChat}
                className="w-full flex items-center gap-2 px-3 py-2.5 bg-blue-50 text-blue-600 rounded-xl text-sm font-medium hover:bg-blue-100 active:scale-95 transition-all duration-150"
              >
                <Plus className="w-4 h-4" />
                新建对话
              </button>
              <button
                onClick={openCreateCategory}
                className="w-full flex items-center gap-2 px-3 py-2 text-gray-500 text-sm hover:bg-gray-50 rounded-xl transition-colors"
              >
                <FolderInput className="w-4 h-4" />
                新建分类
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-2">
              {/* 分类折叠组 */}
              {categories.map((cat) => {
                const expanded = expandedGroups[cat.id] !== false
                const groupSessions = groupedSessions[cat.id] || []
                return (
                  <div key={cat.id}>
                    <div className="flex items-center gap-1 px-2 py-1.5 relative">
                      <button
                        onClick={() => toggleGroup(cat.id)}
                        className="p-0.5 hover:bg-gray-100 rounded"
                      >
                        {expanded ? (
                          <ChevronDown className="w-3.5 h-3.5 text-gray-400" />
                        ) : (
                          <ChevronRight className="w-3.5 h-3.5 text-gray-400" />
                        )}
                      </button>
                      {expanded ? (
                        <FolderOpen className="w-4 h-4 text-amber-500" />
                      ) : (
                        <Folder className="w-4 h-4 text-amber-500" />
                      )}
                      <span className="text-xs font-medium text-gray-600 flex-1 truncate">
                        {cat.name}
                      </span>
                      <span className="text-[10px] text-gray-400">{groupSessions.length}</span>
                      <button
                        onClick={() =>
                          setOpenMenuCategoryId(openMenuCategoryId === cat.id ? null : cat.id)
                        }
                        className="p-1 hover:bg-gray-100 rounded"
                      >
                        <MoreVertical className="w-3.5 h-3.5 text-gray-400" />
                      </button>
                      {openMenuCategoryId === cat.id && (
                        <div className="absolute right-0 top-full mt-1 bg-white shadow-lg rounded-lg border border-gray-100 z-10 w-28">
                          <button
                            onClick={() => openRenameCategory(cat)}
                            className="w-full flex items-center gap-2 px-3 py-2 text-left text-xs text-gray-600 hover:bg-gray-50"
                          >
                            <Pencil className="w-3 h-3" /> 重命名
                          </button>
                          <button
                            onClick={() => handleDeleteCategory(cat.id)}
                            className="w-full flex items-center gap-2 px-3 py-2 text-left text-xs text-red-500 hover:bg-red-50"
                          >
                            <Trash2 className="w-3 h-3" /> 删除分类
                          </button>
                        </div>
                      )}
                    </div>
                    {expanded && groupSessions.length > 0 && (
                      <div className="space-y-1 pl-2">{groupSessions.map(renderSessionItem)}</div>
                    )}
                  </div>
                )
              })}

              {/* 未分类组 */}
              <div>
                <div className="flex items-center gap-1 px-2 py-1.5">
                  <button
                    onClick={() => toggleGroup('uncategorized')}
                    className="p-0.5 hover:bg-gray-100 rounded"
                  >
                    {expandedGroups['uncategorized'] !== false ? (
                      <ChevronDown className="w-3.5 h-3.5 text-gray-400" />
                    ) : (
                      <ChevronRight className="w-3.5 h-3.5 text-gray-400" />
                    )}
                  </button>
                  <MessageSquare className="w-4 h-4 text-gray-400" />
                  <span className="text-xs font-medium text-gray-500 flex-1">未分类</span>
                  <span className="text-[10px] text-gray-400">
                    {groupedSessions['uncategorized']?.length || 0}
                  </span>
                </div>
                {expandedGroups['uncategorized'] !== false && (
                  <div className="space-y-1 pl-2">
                    {(groupedSessions['uncategorized'] || []).map(renderSessionItem)}
                    {sessions.length === 0 && (
                      <div className="text-center py-6">
                        <MessageSquare className="w-8 h-8 text-gray-200 mx-auto mb-2" />
                        <p className="text-xs text-gray-400">暂无历史会话</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 分类 CRUD Modal */}
      {showCategoryModal && (
        <div
          className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center"
          onClick={() => setShowCategoryModal(false)}
        >
          <div
            className="bg-white w-full sm:max-w-md sm:rounded-2xl rounded-t-3xl p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-lg font-bold text-gray-900 mb-5">
              {editingCategory ? '重命名分类' : '新建分类'}
            </h2>
            <input
              type="text"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSaveCategory()
              }}
              placeholder="输入分类名称"
              autoFocus
              maxLength={64}
              className="w-full px-4 py-3 bg-gray-100 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
            <div className="flex gap-3 mt-5">
              <button
                onClick={() => {
                  setShowCategoryModal(false)
                  setEditingCategory(null)
                  setCategoryName('')
                }}
                className="flex-1 py-3 bg-gray-100 text-gray-600 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSaveCategory}
                className="flex-1 py-3 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 分类选择器 Modal（移动会话） */}
      {showMovePicker && moveTargetSessionId && (
        <div
          className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center"
          onClick={() => setShowMovePicker(false)}
        >
          <div
            className="bg-white w-full sm:max-w-md sm:rounded-2xl rounded-t-3xl p-6 max-h-[80vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-lg font-bold text-gray-900 mb-5">选择分类</h2>
            <div className="space-y-1">
              {(() => {
                const targetSession = sessions.find((s) => s.id === moveTargetSessionId)
                const hasCategory = !!targetSession?.categoryId
                return (
                  <>
                    {hasCategory && (
                      <button
                        onClick={() => handleMoveToCategory(null)}
                        className="w-full flex items-center gap-3 px-3 py-3 text-left text-sm text-gray-600 hover:bg-gray-50 rounded-xl"
                      >
                        <X className="w-4 h-4 text-gray-400" />
                        移出分类
                      </button>
                    )}
                    {categories.map((cat) => (
                      <button
                        key={cat.id}
                        onClick={() => handleMoveToCategory(cat.id)}
                        className={`w-full flex items-center gap-3 px-3 py-3 text-left text-sm rounded-xl hover:bg-gray-50 ${
                          targetSession?.categoryId === cat.id
                            ? 'bg-blue-50 text-blue-600'
                            : 'text-gray-700'
                        }`}
                      >
                        <Folder className="w-4 h-4 text-amber-500" />
                        <span className="flex-1">{cat.name}</span>
                        {targetSession?.categoryId === cat.id && (
                          <span className="text-xs">当前</span>
                        )}
                      </button>
                    ))}
                    <button
                      onClick={() => {
                        setShowMovePicker(false)
                        openCreateCategory()
                      }}
                      className="w-full flex items-center gap-3 px-3 py-3 text-left text-sm text-blue-600 hover:bg-blue-50 rounded-xl"
                    >
                      <Plus className="w-4 h-4" />
                      新建分类
                    </button>
                  </>
                )
              })()}
            </div>
            <button
              onClick={() => setShowMovePicker(false)}
              className="w-full mt-3 py-3 bg-gray-100 text-gray-600 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
            >
              取消
            </button>
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
          <div key={chatKey} className="space-y-4 animate-chat-fade-in">
            {messages.map((msg) => {
              const isUser = msg.role === 'user'
              return (
                <div key={msg.id} className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
                  <div
                    className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                      isUser
                        ? 'bg-gradient-to-br from-blue-500 to-blue-600'
                        : 'bg-gradient-to-br from-purple-500 to-blue-600'
                    }`}
                  >
                    {isUser ? (
                      user?.avatarUrl ? (
                        <img
                          src={
                            user.avatarUrl.startsWith('/files/')
                              ? `/api${user.avatarUrl}`
                              : user.avatarUrl
                          }
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
                  <div
                    className={`max-w-[80%] ${isUser ? 'items-end' : 'items-start'} flex flex-col`}
                  >
                    <div
                      className={`px-4 py-2.5 rounded-2xl text-sm leading-relaxed select-text ${
                        isUser
                          ? 'bg-blue-600 text-white rounded-tr-sm whitespace-pre-wrap'
                          : 'prose prose-sm max-w-none bg-white border border-gray-100 text-gray-700 rounded-tl-sm shadow-sm [&_p]:my-1.5 [&_ul]:my-1.5 [&_ol]:my-1.5 [&_li]:my-0.5 [&_strong]:text-gray-900 [&_code]:bg-gray-100 [&_code]:px-1 [&_code]:py-0.5 [&_code]:rounded [&_code]:text-xs [&_code]:font-mono [&_pre]:bg-gray-900 [&_pre]:text-gray-100 [&_pre]:p-3 [&_pre]:rounded-lg [&_pre]:overflow-x-auto [&_a]:text-blue-600 [&_a]:underline [&_h1]:text-base [&_h2]:text-sm [&_h3]:text-sm'
                      }`}
                    >
                      {isUser ? (
                        msg.content
                      ) : (
                        <ReactMarkdown components={markdownComponents(msg.refs)}>
                          {preprocessContentWithRefs(msg.content, msg.refs)}
                        </ReactMarkdown>
                      )}
                      {!isUser &&
                        msg.id === messages[messages.length - 1]?.id &&
                        streaming &&
                        msg.content && (
                          <span className="inline-block w-1.5 h-4 bg-blue-500 ml-0.5 align-middle animate-pulse" />
                        )}
                      {!isUser &&
                        msg.id === messages[messages.length - 1]?.id &&
                        streaming &&
                        !msg.content && (
                          <span className="inline-flex gap-1">
                            <span
                              className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"
                              style={{ animationDelay: '0ms' }}
                            />
                            <span
                              className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"
                              style={{ animationDelay: '150ms' }}
                            />
                            <span
                              className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"
                              style={{ animationDelay: '300ms' }}
                            />
                          </span>
                        )}
                    </div>
                    {!isUser && msg.refs && msg.refs.length > 0 && <RefCardList refs={msg.refs} />}
                    {!isUser && msg.navigate && <NavigateCard nav={msg.navigate} />}
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
              onChange={(e) => setInputValue(e.target.value)}
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
      </div>

      <NavBar />
    </div>
  )
}
