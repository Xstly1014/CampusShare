import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, Heart, Star, UserPlus, MessageSquare, MessageCircle, Bell, CheckCheck } from 'lucide-react'
import { notificationApi } from '../services/api'
import type { NotificationListItem, SenderInfo } from '../services/api'
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
}

function resolveAvatar(url?: string): string | undefined {
  if (!url) return undefined
  return url.startsWith('/files/') ? `/api${url}` : url
}

function buildPostUrl(n: NotificationListItem): string | null {
  if (!n.targetId) return null
  if (n.schoolId) {
    const hash = n.commentId ? `#comment-${n.commentId}` : ''
    return `/school/${n.schoolId}/post/${n.targetId}${hash}`
  }
  if (n.categoryId) {
    const hash = n.commentId ? `#comment-${n.commentId}` : ''
    return `/category/${n.categoryId}/post/${n.targetId}${hash}`
  }
  return null
}

export default function NotificationPage() {
  const navigate = useNavigate()
  const [list, setList] = useState<NotificationListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [markingAll, setMarkingAll] = useState(false)

  const fetchList = useCallback(async () => {
    setLoading(true)
    try {
      const res = await notificationApi.getList()
      setList(res.data || [])
    } catch {
      toast.error('加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchList() }, [fetchList])

  const markAsReadLocal = (id: string, isAggregated: boolean, type?: string, targetId?: string) => {
    setList(prev => prev.map(item => {
      if (item.id === id) {
        return { ...item, isRead: 1 }
      }
      return item
    }))
    if (isAggregated && type && targetId) {
      notificationApi.markAggregatedAsRead(type, targetId).catch(() => {})
    } else {
      notificationApi.markSingleAsRead(id).catch(() => {})
    }
  }

  const handleAvatarClick = (e: React.MouseEvent, n: NotificationListItem) => {
    e.stopPropagation()
    if (n.type === 'SYSTEM' || !n.senderId || n.senderId === 'system') return
    navigate(`/user/${n.senderId}`)
  }

  const handleItemClick = (n: NotificationListItem) => {
    const isAggregated = n.aggregatedCount > 1 && !!n.aggregatedSenders
    markAsReadLocal(n.id, isAggregated, n.type, n.targetId)

    if (n.type === 'FOLLOW' && n.senderId) {
      navigate(`/user/${n.senderId}`)
      return
    }

    if (n.type === 'SYSTEM') {
      return
    }

    const postUrl = buildPostUrl(n)
    if (postUrl) {
      navigate(postUrl)
    }
  }

  const handleMarkAll = async () => {
    setMarkingAll(true)
    try {
      await notificationApi.markAllAsRead()
      setList(prev => prev.map(item => ({ ...item, isRead: 1 })))
      toast.success('已全部标记为已读')
    } catch {
      toast.error('操作失败')
    } finally {
      setMarkingAll(false)
    }
  }

  const hasUnread = list.some(n => n.isRead === 0)

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
          <button onClick={() => navigate(-1)} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <span className="text-sm font-medium text-gray-900">通知</span>
          <button
            onClick={handleMarkAll}
            disabled={markingAll || !hasUnread}
            className={`flex items-center gap-1 text-sm px-2 py-1 rounded-md transition-colors ${
              hasUnread && !markingAll
                ? 'text-blue-600 hover:bg-blue-50'
                : 'text-gray-300 cursor-not-allowed'
            }`}
          >
            <CheckCheck className="w-4 h-4" />
            <span>一键已读</span>
          </button>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
          </div>
        ) : list.length > 0 ? (
          <div className="space-y-2">
            {list.map((n) => {
              const config = typeConfig[n.type] || typeConfig.SYSTEM
              const isSystem = n.type === 'SYSTEM' || n.senderId === 'system'
              const isAggregated = n.aggregatedCount > 1 && !!n.aggregatedSenders
              const unread = n.isRead === 0

              return (
                <div
                  key={n.id}
                  onClick={() => handleItemClick(n)}
                  className={`bg-white rounded-xl border ${unread ? 'border-blue-200' : 'border-gray-100'} p-3 flex items-start gap-3 ${
                    isSystem ? 'cursor-default' : 'cursor-pointer hover:border-gray-200'
                  } transition-colors relative`}
                >
                  {unread && (
                    <span className="absolute top-3 right-3 w-2 h-2 bg-red-500 rounded-full flex-shrink-0"></span>
                  )}

                  <div className="flex-shrink-0" onClick={(e) => handleAvatarClick(e, n)}>
                    {isSystem ? (
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center ${config.color}`}>
                        {config.icon}
                      </div>
                    ) : isAggregated && n.aggregatedSenders && n.aggregatedSenders.length > 1 ? (
                      <div className="w-10 h-10 relative flex-shrink-0 cursor-pointer">
                        {n.aggregatedSenders.slice(0, Math.min(4, n.aggregatedSenders.length)).map((s, i) => {
                          const positions = [
                            'top-0 left-0',
                            'top-0 right-0',
                            'bottom-0 left-0',
                            'bottom-0 right-0',
                          ]
                          return (
                            <div
                              key={s.userId}
                              className={`absolute w-5 h-5 rounded-full overflow-hidden border border-white ${positions[i]}`}
                            >
                              {resolveAvatar(s.avatarUrl) ? (
                                <img src={resolveAvatar(s.avatarUrl)} alt={s.username} className="w-full h-full object-cover" />
                              ) : (
                                <div className="w-full h-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center">
                                  <span className="text-white text-[8px] font-bold">{s.username?.substring(0, 1)}</span>
                                </div>
                              )}
                            </div>
                          )
                        })}
                      </div>
                    ) : (
                      <div className="w-10 h-10 rounded-full overflow-hidden bg-gradient-to-br from-blue-500 to-blue-600 cursor-pointer hover:ring-2 hover:ring-blue-200 transition-all">
                        {resolveAvatar(n.senderAvatar) ? (
                          <img src={resolveAvatar(n.senderAvatar)} alt={n.senderName || ''} className="w-full h-full object-cover" />
                        ) : (
                          <span className="w-full h-full flex items-center justify-center text-white font-bold text-sm">
                            {(n.senderName || '?').substring(0, 1).toUpperCase()}
                          </span>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="flex-1 min-w-0 pr-4">
                    {isSystem ? (
                      <>
                        <p className="text-sm font-medium text-gray-900">系统通知</p>
                        <p className="text-sm text-gray-500 mt-0.5">{n.content || n.targetTitle}</p>
                      </>
                    ) : (
                      <>
                        <p className="text-sm text-gray-700 leading-relaxed">
                          {n.content}
                        </p>
                        {n.targetTitle && !n.content?.includes('《') && (
                          <p className="text-xs text-gray-400 line-clamp-1 mt-1">《{n.targetTitle}》</p>
                        )}
                      </>
                    )}
                    <p className="text-xs text-gray-400 mt-1.5">{formatTime(n.createTime)}</p>
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="text-center py-16">
            <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
              <Bell className="w-8 h-8 text-gray-300" />
            </div>
            <p className="text-gray-400 text-sm">暂无通知</p>
          </div>
        )}
      </div>
      <NavBar />
    </div>
  )
}
