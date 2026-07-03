import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, Send, MessageSquare, Trash2 } from 'lucide-react'
import { toast } from '../stores'
import { useAuth } from '../context/AuthContext'
import { formatTime } from '../utils/time'
import {
  useConversationList,
  useConversationMessages,
  useCanSendMessage,
  useOtherUserProfile,
  useSendMessage,
  useHideConversation,
  type MessageItem,
} from '../hooks/queries/useMessages'

export default function MessagePage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()
  const { user: currentUser } = useAuth()

  const [newMessage, setNewMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [hideConfirm, setHideConfirm] = useState<{ otherId: string; otherName: string } | null>(
    null,
  )
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const { data: conversations = [], isLoading: listLoading } = useConversationList()
  const { data: messages = [] } = useConversationMessages(userId)
  const { data: canSend = true } = useCanSendMessage(userId)
  const { data: otherUser } = useOtherUserProfile(userId)
  const sendMessageMutation = useSendMessage()
  const hideConversationMutation = useHideConversation()

  const hasSentMessage = messages.some((m) => m.senderId === currentUser?.id)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleHideConversation = (e: React.MouseEvent, otherId: string, otherName: string) => {
    e.stopPropagation()
    setHideConfirm({ otherId, otherName })
  }

  const confirmHideConversation = async () => {
    if (!hideConfirm) return
    const { otherId } = hideConfirm
    try {
      await hideConversationMutation.mutateAsync(otherId)
      toast.success('会话已隐藏')
    } catch {
      toast.error('隐藏失败')
    } finally {
      setHideConfirm(null)
    }
  }

  const handleSend = async () => {
    const content = newMessage.trim()
    if (!content || !userId || sending) return
    if (!canSend && hasSentMessage) {
      toast.error('对方未关注你且未回复，等待对方回复后可继续发送')
      return
    }
    setSending(true)
    try {
      await sendMessageMutation.mutateAsync({ receiverId: userId, content })
      setNewMessage('')
    } catch (err) {
      toast.error((err as Error).message || '发送失败')
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
            <button
              onClick={() => navigate(-1)}
              className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
            >
              <ChevronLeft className="w-5 h-5 text-gray-600" />
            </button>
            <span className="text-sm font-medium text-gray-900">私信</span>
          </div>
        </div>
        <div className="max-w-5xl mx-auto px-4 py-4">
          {listLoading ? (
            <div className="text-center py-16">
              <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
            </div>
          ) : conversations.length > 0 ? (
            <div className="space-y-2">
              {conversations.map((conv) => {
                const isMe = conv.senderId === currentUser?.id
                const otherId = isMe ? conv.receiverId : conv.senderId
                const otherName = isMe ? conv.receiverName : conv.senderName
                const otherAvatar = isMe ? conv.receiverAvatar : conv.senderAvatar
                const unread = !isMe && conv.isRead === 0
                return (
                  <div
                    key={conv.id}
                    className="bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 hover:border-gray-200 transition-colors group"
                  >
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                      <div
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/user/${otherId}`)
                        }}
                        className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0 relative cursor-pointer hover:opacity-80 transition-opacity"
                      >
                        {otherAvatar ? (
                          <img
                            src={
                              otherAvatar.startsWith('/files/') ? `/api${otherAvatar}` : otherAvatar
                            }
                            alt={otherName}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <span className="text-white font-bold">
                            {(otherName || '?').substring(0, 1).toUpperCase()}
                          </span>
                        )}
                        {unread && (
                          <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-white"></span>
                        )}
                      </div>
                      <div
                        onClick={() => navigate(`/messages/${otherId}`)}
                        className="flex-1 min-w-0 cursor-pointer"
                      >
                        <div className="flex items-center justify-between">
                          <p className="text-sm font-medium text-gray-900">{otherName || '用户'}</p>
                          <span className="text-xs text-gray-400 flex-shrink-0 ml-2">
                            {formatTime(conv.createTime)}
                          </span>
                        </div>
                        <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">
                          {isMe ? '我: ' : ''}
                          {conv.content}
                        </p>
                      </div>
                    </div>
                    <button
                      onClick={(e) => handleHideConversation(e, otherId, otherName || '用户')}
                      className="p-2 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                      title="隐藏会话"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
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

        {hideConfirm && (
          <div
            className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center"
            onClick={() => setHideConfirm(null)}
          >
            <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
              <h3 className="text-base font-bold text-gray-900 mb-2">隐藏会话</h3>
              <p className="text-sm text-gray-500 mb-5">
                确定要隐藏与「{hideConfirm.otherName}
                」的会话吗？消息不会被删除，对方发送新消息后会重新显示。
              </p>
              <div className="flex gap-3">
                <button
                  onClick={() => setHideConfirm(null)}
                  className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
                >
                  取消
                </button>
                <button
                  onClick={confirmHideConversation}
                  className="flex-1 py-2.5 bg-red-500 text-white rounded-xl text-sm font-medium hover:bg-red-600 transition-colors"
                >
                  隐藏
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
          >
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div
            onClick={() => userId && navigate(`/user/${userId}`)}
            className="flex items-center gap-2 cursor-pointer hover:opacity-80 transition-opacity"
          >
            <div className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden">
              {otherUser?.avatarUrl ? (
                <img
                  src={
                    otherUser.avatarUrl.startsWith('/files/')
                      ? `/api${otherUser.avatarUrl}`
                      : otherUser.avatarUrl
                  }
                  alt={otherUser.username}
                  className="w-full h-full object-cover"
                />
              ) : (
                <span className="text-white text-xs font-bold">
                  {otherUser?.username?.substring(0, 1).toUpperCase()}
                </span>
              )}
            </div>
            <span className="text-sm font-medium text-gray-900">
              {otherUser?.username || '私信'}
            </span>
          </div>
        </div>
      </div>

      <div className="flex-1 max-w-5xl mx-auto w-full px-4 py-4 overflow-y-auto">
        {messages.length > 0 ? (
          <div className="space-y-3">
            {messages.map((msg: MessageItem) => {
              const isMe = msg.senderId === currentUser?.id
              return (
                <div key={msg.id} className={`flex gap-2 ${isMe ? 'flex-row-reverse' : ''}`}>
                  <div
                    onClick={() => navigate(`/user/${msg.senderId}`)}
                    className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0 cursor-pointer hover:opacity-80 transition-opacity"
                  >
                    {msg.senderAvatar ? (
                      <img
                        src={
                          msg.senderAvatar.startsWith('/files/')
                            ? `/api${msg.senderAvatar}`
                            : msg.senderAvatar
                        }
                        alt={msg.senderName}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span className="text-white text-xs font-bold">
                        {msg.senderName?.substring(0, 1).toUpperCase()}
                      </span>
                    )}
                  </div>
                  <div
                    className={`max-w-[70%] ${isMe ? 'items-end' : 'items-start'} flex flex-col`}
                  >
                    <div
                      className={`px-3 py-2 rounded-2xl text-sm ${isMe ? 'bg-blue-600 text-white rounded-tr-sm' : 'bg-white border border-gray-100 text-gray-700 rounded-tl-sm'}`}
                    >
                      {msg.content}
                    </div>
                    <span className="text-xs text-gray-400 mt-1 px-1">
                      {formatTime(msg.createTime)}
                    </span>
                  </div>
                </div>
              )
            })}
            <div ref={messagesEndRef} />
          </div>
        ) : (
          <div className="text-center py-16">
            <p className="text-gray-400 text-sm">打个招呼吧~</p>
          </div>
        )}
      </div>

      <div className="bg-white border-t border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <input
            type="text"
            value={newMessage}
            onChange={(e) => setNewMessage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
            placeholder={
              isInputDisabled
                ? '等待对方回复后可继续发送消息'
                : canSend
                  ? '发送消息...'
                  : '发送一条打招呼消息...'
            }
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
          <p className="text-xs text-gray-400 text-center pb-2">
            对方未关注你且未回复，等待对方回复后可继续发送
          </p>
        )}
      </div>
    </div>
  )
}
