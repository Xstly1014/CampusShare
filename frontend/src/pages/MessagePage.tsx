import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, Send, MessageSquare } from 'lucide-react'
import { messageApi, userApi } from '../services/api'
import { toast } from '../stores/toastStore'
import { useAuth } from '../context/AuthContext'
import { formatTime } from '../utils/time'

interface MessageItem {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  receiverId: string
  content: string
  isRead: number
  createTime: string
}

export default function MessagePage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()
  const { user: currentUser } = useAuth()

  const [conversations, setConversations] = useState<MessageItem[]>([])
  const [messages, setMessages] = useState<MessageItem[]>([])
  const [newMessage, setNewMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [canSend, setCanSend] = useState(true)
  const [otherUser, setOtherUser] = useState<{ id: string; username: string; avatarUrl?: string } | null>(null)
  const [sending, setSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const msgCountRef = useRef(0)

  const hasSentMessage = messages.some(m => m.senderId === currentUser?.id)

  const fetchConversations = useCallback(async () => {
    try {
      const res = await messageApi.getList()
      setConversations(res.data || [])
    } catch { /* ignore */ }
    finally { setLoading(false) }
  }, [])

  const fetchMessages = useCallback(async () => {
    if (!userId) return
    try {
      const res = await messageApi.getConversation(userId)
      const msgs = res.data || []
      setMessages(msgs)
      msgCountRef.current = msgs.length
      const canRes = await messageApi.canSend(userId)
      setCanSend(canRes.data)
      const profileRes = await userApi.getUserProfile(userId)
      if (profileRes.data) {
        setOtherUser({ id: profileRes.data.id, username: profileRes.data.username, avatarUrl: profileRes.data.avatarUrl })
      }
    } catch { /* ignore */ }
  }, [userId])

  const pollCanSend = useCallback(async () => {
    if (!userId) return
    try {
      const [canRes, convRes] = await Promise.all([
        messageApi.canSend(userId),
        messageApi.getConversation(userId),
      ])
      setCanSend(canRes.data)
      const newMsgs = convRes.data || []
      if (newMsgs.length !== msgCountRef.current) {
        msgCountRef.current = newMsgs.length
        setMessages(newMsgs)
      }
    } catch { /* ignore */ }
  }, [userId])

  useEffect(() => {
    if (userId) {
      fetchMessages()
      pollTimerRef.current = setInterval(pollCanSend, 5000)
      return () => {
        if (pollTimerRef.current) clearInterval(pollTimerRef.current)
      }
    } else {
      fetchConversations()
    }
  }, [userId, fetchMessages, fetchConversations, pollCanSend])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    const content = newMessage.trim()
    if (!content || !userId || sending) return
    if (!canSend && hasSentMessage) {
      toast.error('对方未关注你且未回复，等待对方回复后可继续发送')
      return
    }
    setSending(true)
    try {
      const res = await messageApi.send(userId, content)
      setMessages((prev) => {
        const updated = [...prev, res.data]
        msgCountRef.current = updated.length
        return updated
      })
      setNewMessage('')
      const canRes = await messageApi.canSend(userId)
      setCanSend(canRes.data)
    } catch (err) {
      toast.error((err as Error).message || '发送失败')
      const canRes = await messageApi.canSend(userId)
      setCanSend(canRes.data)
    } finally {
      setSending(false)
    }
  }

  const isInputDisabled = !canSend && hasSentMessage

  if (!userId) {
    return (
      <div className="min-h-screen bg-gray-50 pb-16">
        <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
          <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
            <button onClick={() => navigate(-1)} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
              <ChevronLeft className="w-5 h-5 text-gray-600" />
            </button>
            <span className="text-sm font-medium text-gray-900">私信</span>
          </div>
        </div>
        <div className="max-w-5xl mx-auto px-4 py-4">
          {loading ? (
            <div className="text-center py-16"><div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div></div>
          ) : conversations.length > 0 ? (
            <div className="space-y-2">
              {conversations.map((conv) => {
                const isMe = conv.senderId === currentUser?.id
                const otherId = isMe ? conv.receiverId : conv.senderId
                const otherName = isMe ? '对方' : conv.senderName
                const otherAvatar = isMe ? null : conv.senderAvatar
                return (
                  <div key={conv.id} onClick={() => navigate(`/messages/${otherId}`)} className="bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 cursor-pointer hover:border-gray-200 transition-colors">
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0">
                      {otherAvatar ? <img src={otherAvatar.startsWith('/files/') ? `/api${otherAvatar}` : otherAvatar} alt={otherName} className="w-full h-full object-cover" /> : <span className="text-white font-bold">{otherName?.substring(0, 1).toUpperCase()}</span>}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <p className="text-sm font-medium text-gray-900">{otherName}</p>
                        <span className="text-xs text-gray-400">{formatTime(conv.createTime)}</span>
                      </div>
                      <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">{isMe ? '我: ' : ''}{conv.content}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="text-center py-16">
              <MessageSquare className="w-12 h-12 text-gray-200 mx-auto mb-3" />
              <p className="text-gray-400 text-sm">暂无私信</p>
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden">
              {otherUser?.avatarUrl ? <img src={otherUser.avatarUrl.startsWith('/files/') ? `/api${otherUser.avatarUrl}` : otherUser.avatarUrl} alt={otherUser.username} className="w-full h-full object-cover" /> : <span className="text-white text-xs font-bold">{otherUser?.username?.substring(0, 1).toUpperCase()}</span>}
            </div>
            <span className="text-sm font-medium text-gray-900">{otherUser?.username || '私信'}</span>
          </div>
        </div>
      </div>

      <div className="flex-1 max-w-5xl mx-auto w-full px-4 py-4 overflow-y-auto">
        {messages.length > 0 ? (
          <div className="space-y-3">
            {messages.map((msg) => {
              const isMe = msg.senderId === currentUser?.id
              return (
                <div key={msg.id} className={`flex gap-2 ${isMe ? 'flex-row-reverse' : ''}`}>
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0">
                    {msg.senderAvatar ? <img src={msg.senderAvatar.startsWith('/files/') ? `/api${msg.senderAvatar}` : msg.senderAvatar} alt={msg.senderName} className="w-full h-full object-cover" /> : <span className="text-white text-xs font-bold">{msg.senderName?.substring(0, 1).toUpperCase()}</span>}
                  </div>
                  <div className={`max-w-[70%] ${isMe ? 'items-end' : 'items-start'} flex flex-col`}>
                    <div className={`px-3 py-2 rounded-2xl text-sm ${isMe ? 'bg-blue-600 text-white rounded-tr-sm' : 'bg-white border border-gray-100 text-gray-700 rounded-tl-sm'}`}>
                      {msg.content}
                    </div>
                    <span className="text-xs text-gray-400 mt-1 px-1">{formatTime(msg.createTime)}</span>
                  </div>
                </div>
              )
            })}
            <div ref={messagesEndRef} />
          </div>
        ) : (
          <div className="text-center py-16"><p className="text-gray-400 text-sm">打个招呼吧~</p></div>
        )}
      </div>

      <div className="bg-white border-t border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <input
            type="text"
            value={newMessage}
            onChange={(e) => setNewMessage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
            placeholder={isInputDisabled ? '等待对方回复后可继续发送消息' : (canSend ? '发送消息...' : '发送一条打招呼消息...')}
            disabled={isInputDisabled || sending}
            className="flex-1 px-4 py-2.5 bg-gray-100 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all disabled:opacity-60 disabled:cursor-not-allowed"
          />
          <button
            onClick={handleSend}
            disabled={!newMessage.trim() || isInputDisabled || sending}
            className="p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
        {isInputDisabled && (
          <p className="text-xs text-gray-400 text-center pb-2">对方未关注你且未回复，等待对方回复后可继续发送</p>
        )}
      </div>
    </div>
  )
}
