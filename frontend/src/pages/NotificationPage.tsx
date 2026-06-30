import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, Heart, Star, UserPlus, MessageSquare, MessageCircle, Pin, ChevronRight, Bell } from 'lucide-react'
import { notificationApi } from '../services/api'
import type { NotificationItem, NotificationDetail } from '../services/api'
import { formatTime } from '../utils/time'
import { toast } from '../stores/toastStore'
import NavBar from '../components/common/NavBar'

const typeConfig: Record<string, { icon: React.ReactNode; color: string }> = {
  SYSTEM: { icon: <Bell className="w-5 h-5" />, color: 'bg-amber-50 text-amber-500' },
  LIKE: { icon: <Heart className="w-5 h-5" />, color: 'bg-red-50 text-red-500' },
  COMMENT_LIKE: { icon: <Heart className="w-5 h-5" />, color: 'bg-pink-50 text-pink-500' },
  STAR: { icon: <Star className="w-5 h-5" />, color: 'bg-orange-50 text-orange-500' },
  FOLLOW: { icon: <UserPlus className="w-5 h-5" />, color: 'bg-blue-50 text-blue-500' },
  COMMENT: { icon: <MessageCircle className="w-5 h-5" />, color: 'bg-green-50 text-green-500' },
  REPLY: { icon: <MessageCircle className="w-5 h-5" />, color: 'bg-emerald-50 text-emerald-500' },
  STRANGER_MSG: { icon: <MessageSquare className="w-5 h-5" />, color: 'bg-gray-50 text-gray-500' },
  CONVERSATION: { icon: <MessageSquare className="w-5 h-5" />, color: 'bg-cyan-50 text-cyan-500' },
}

export default function NotificationPage() {
  const navigate = useNavigate()
  const [feed, setFeed] = useState<NotificationItem[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedType, setExpandedType] = useState<string | null>(null)
  const [detail, setDetail] = useState<NotificationDetail[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  const fetchFeed = useCallback(async () => {
    setLoading(true)
    try {
      const res = await notificationApi.getFeed()
      setFeed(res.data || [])
    } catch {
      toast.error('加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchFeed() }, [fetchFeed])

  const handleExpand = async (item: NotificationItem) => {
    // If it's a conversation, navigate to chat
    if (item.itemType === 'CONVERSATION' && item.otherUserId) {
      navigate(`/messages/${item.otherUserId}`)
      return
    }
    // If it's stranger messages, navigate to messages list
    if (item.itemType === 'STRANGER_MSG') {
      navigate('/messages')
      return
    }

    // Otherwise expand/collapse notification group
    if (expandedType === item.itemType) {
      setExpandedType(null)
      return
    }

    setExpandedType(item.itemType)
    setDetailLoading(true)
    try {
      const res = await notificationApi.getDetail(item.itemType)
      setDetail(res.data || [])
      // Mark as read
      await notificationApi.markAsRead(item.itemType)
      // Update local feed to clear unread
      setFeed(prev => prev.map(f => f.itemType === item.itemType ? { ...f, unreadCount: 0 } : f))
    } catch {
      toast.error('加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const handlePin = async (e: React.MouseEvent, item: NotificationItem) => {
    e.stopPropagation()
    try {
      await notificationApi.togglePin(item.itemType, item.otherUserId)
      setFeed(prev => prev.map(f =>
        f.itemType === item.itemType && f.otherUserId === item.otherUserId
          ? { ...f, isPinned: !f.isPinned }
          : f
      ))
      toast.success(item.isPinned ? '已取消置顶' : '已置顶')
    } catch {
      toast.error('操作失败')
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate('/home')} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <span className="text-sm font-medium text-gray-900">通知</span>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
          </div>
        ) : feed.length > 0 ? (
          <div className="space-y-2">
            {feed.map((item, idx) => {
              const config = typeConfig[item.itemType] || typeConfig.STRANGER_MSG
              const isExpanded = expandedType === item.itemType
              return (
                <div key={`${item.itemType}-${item.otherUserId || idx}`}>
                  <div
                    onClick={() => handleExpand(item)}
                    className={`bg-white rounded-xl border ${item.unreadCount > 0 ? 'border-blue-200' : 'border-gray-100'} p-3 flex items-center gap-3 cursor-pointer hover:border-gray-200 transition-colors relative group`}
                  >
                    {item.itemType === 'CONVERSATION' ? (
                      <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 overflow-hidden bg-gradient-to-br from-blue-500 to-blue-600">
                        {item.otherUserAvatar ? (
                          <img src={item.otherUserAvatar.startsWith('/files/') ? `/api${item.otherUserAvatar}` : item.otherUserAvatar} alt={item.otherUserName || ''} className="w-full h-full object-cover" />
                        ) : (
                          <span className="text-white font-bold">{(item.otherUserName || '?').substring(0, 1).toUpperCase()}</span>
                        )}
                      </div>
                    ) : (
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 ${config.color}`}>
                        {config.icon}
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium text-gray-900">{item.title}</p>
                        {item.unreadCount > 0 && (
                          <span className="px-1.5 py-0.5 bg-red-500 text-white text-xs rounded-full min-w-[18px] text-center">
                            {item.unreadCount}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">{item.preview}</p>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className="text-xs text-gray-400">{formatTime(item.latestTime)}</span>
                      {item.itemType === 'CONVERSATION' || item.itemType === 'STRANGER_MSG' ? (
                        <ChevronRight className="w-4 h-4 text-gray-300" />
                      ) : null}
                    </div>
                    {/* Pin button - visible on hover or when pinned */}
                    {item.isPinned && <Pin className="w-3.5 h-3.5 text-blue-500 absolute top-1 right-1" fill="currentColor" />}
                  </div>

                  {/* Expanded detail for notification groups */}
                  {isExpanded && (
                    <div className="mt-1 ml-4 space-y-1">
                      {detailLoading ? (
                        <div className="text-center py-4">
                          <div className="w-5 h-5 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
                        </div>
                      ) : detail.length > 0 ? (
                        detail.map((d) => (
                          <div
                            key={d.id}
                            onClick={() => {
                              if (d.type === 'SYSTEM') return;
                              if (d.type === 'FOLLOW') {
                                navigate(`/user/${d.senderId}`)
                                return
                              }
                              if (d.targetId && d.schoolId) {
                                const hash = d.commentId ? `#comment-${d.commentId}` : ''
                                navigate(`/school/${d.schoolId}/post/${d.targetId}${hash}`)
                              }
                            }}
                            className={`bg-gray-50 rounded-lg p-2.5 flex items-center gap-2.5 ${d.type === 'SYSTEM' ? 'cursor-default' : 'cursor-pointer hover:bg-gray-100'} transition-colors`}
                          >
                            <div
                              onClick={(e) => {
                                if (d.type === 'SYSTEM') return
                                e.stopPropagation()
                                navigate(`/user/${d.senderId}`)
                              }}
                              className={`w-8 h-8 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0 ${d.type === 'SYSTEM' ? 'bg-amber-50 text-amber-500' : 'bg-gradient-to-br from-blue-500 to-blue-600 cursor-pointer hover:ring-2 hover:ring-blue-200'}`}
                            >
                              {d.type === 'SYSTEM' ? (
                                <Bell className="w-4 h-4" />
                              ) : d.senderAvatar ? (
                                <img src={d.senderAvatar.startsWith('/files/') ? `/api${d.senderAvatar}` : d.senderAvatar} alt={d.senderName} className="w-full h-full object-cover" />
                              ) : (
                                <span className="text-white text-xs font-bold">{d.senderName?.substring(0, 1).toUpperCase()}</span>
                              )}
                            </div>
                            <div className="flex-1 min-w-0">
                              {d.type === 'SYSTEM' ? (
                                <>
                                  <p className="text-xs font-medium text-gray-900">{d.targetId || '系统通知'}</p>
                                  <p className="text-xs text-gray-500 mt-0.5">{d.targetTitle}</p>
                                </>
                              ) : (
                                <>
                                  <p className="text-xs text-gray-700">
                                    <span className="font-medium">{d.senderName}</span>
                                    {d.type === 'LIKE' && ` 赞了你的帖子`}
                                    {d.type === 'COMMENT_LIKE' && ` 赞了你的评论`}
                                    {d.type === 'STAR' && ` 收藏了你的帖子`}
                                    {d.type === 'FOLLOW' && ` 关注了你`}
                                    {d.type === 'COMMENT' && ` 评论了你的帖子`}
                                    {d.type === 'REPLY' && ` 回复了你的评论`}
                                    {d.targetTitle && `：${d.targetTitle}`}
                                  </p>
                                  {d.content && <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">{d.content}</p>}
                                </>
                              )}
                            </div>
                            <span className="text-xs text-gray-400 flex-shrink-0">{formatTime(d.createTime)}</span>
                          </div>
                        ))
                      ) : (
                        <p className="text-center text-xs text-gray-400 py-3">暂无详情</p>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <div className="text-center py-16">
            <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
              <MessageSquare className="w-8 h-8 text-gray-300" />
            </div>
            <p className="text-gray-400 text-sm">暂无通知</p>
          </div>
        )}
      </div>
      <NavBar />
    </div>
  )
}
