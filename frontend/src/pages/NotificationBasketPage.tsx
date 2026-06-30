import { useState, useEffect, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Bell, MessageSquare, CheckCheck } from 'lucide-react'
import { notificationApi } from '../services/api'
import type { NotificationDetail } from '../services/api'
import { formatTime } from '../utils/time'
import { toast } from '../stores/toastStore'

const basketTitleConfig: Record<string, string> = {
  SYSTEM: '系统通知',
  LIKE: '点赞',
  COMMENT_LIKE: '评论点赞',
  STAR: '收藏',
  FOLLOW: '关注',
  COMMENT: '评论',
  REPLY: '回复',
  STRANGER_MSG: '陌生人私信',
}

export default function NotificationBasketPage() {
  const navigate = useNavigate()
  const { basketType = '' } = useParams<{ basketType: string }>()
  const [detail, setDetail] = useState<NotificationDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [markingRead, setMarkingRead] = useState(false)

  const fetchDetail = useCallback(async () => {
    setLoading(true)
    try {
      const res = await notificationApi.getDetail(basketType)
      setDetail(res.data || [])
      await notificationApi.markAsRead(basketType)
    } catch {
      toast.error('加载失败')
    } finally {
      setLoading(false)
    }
  }, [basketType])

  useEffect(() => { fetchDetail() }, [fetchDetail])

  const handleMarkAllRead = async () => {
    setMarkingRead(true)
    try {
      await notificationApi.markAsRead(basketType)
      setDetail(prev => prev.map(d => ({ ...d, isRead: 1 })))
      toast.success('已全部标为已读')
    } catch {
      toast.error('操作失败')
    } finally {
      setMarkingRead(false)
    }
  }

  const handleItemClick = (d: NotificationDetail) => {
    if (d.type === 'SYSTEM') return
    if (d.type === 'STRANGER_MSG') {
      navigate(`/messages/${d.senderId}`)
      return
    }
    if (d.type === 'FOLLOW') {
      navigate(`/user/${d.senderId}`)
      return
    }
    if (d.targetId && d.schoolId) {
      const hash = d.commentId ? `#comment-${d.commentId}` : ''
      navigate(`/school/${d.schoolId}/post/${d.targetId}${hash}`)
    }
  }

  const handleAvatarClick = (e: React.MouseEvent, d: NotificationDetail) => {
    if (d.type === 'SYSTEM') return
    e.stopPropagation()
    navigate(`/user/${d.senderId}`)
  }

  const title = basketTitleConfig[basketType] || '通知'

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
            返回
          </button>
          <span className="text-sm font-medium text-gray-900">{title}</span>
          <button
            onClick={handleMarkAllRead}
            disabled={markingRead || loading || detail.length === 0}
            className="flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700 disabled:text-gray-300 transition-colors"
          >
            <CheckCheck className="w-4 h-4" />
            一键已读
          </button>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
          </div>
        ) : detail.length > 0 ? (
          <div className="space-y-2">
            {detail.map((d) => (
              <div
                key={d.id}
                onClick={() => handleItemClick(d)}
                className={`bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 ${d.type === 'SYSTEM' ? 'cursor-default' : 'cursor-pointer hover:border-gray-200'} transition-colors`}
              >
                <div
                  onClick={(e) => handleAvatarClick(e, d)}
                  className={`w-10 h-10 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0 ${d.type === 'SYSTEM' ? 'bg-amber-50 text-amber-500' : 'bg-gradient-to-br from-blue-500 to-blue-600 cursor-pointer hover:ring-2 hover:ring-blue-200'}`}
                >
                  {d.type === 'SYSTEM' ? (
                    <Bell className="w-5 h-5" />
                  ) : d.senderAvatar ? (
                    <img src={d.senderAvatar.startsWith('/files/') ? `/api${d.senderAvatar}` : d.senderAvatar} alt={d.senderName} className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-white text-sm font-bold">{d.senderName?.substring(0, 1).toUpperCase()}</span>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  {d.type === 'SYSTEM' ? (
                    <>
                      <p className="text-sm font-medium text-gray-900">{d.targetId || '系统通知'}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{d.targetTitle}</p>
                    </>
                  ) : (
                    <>
                      <p className="text-sm text-gray-700">
                        <span className="font-medium">{d.senderName}</span>
                        {d.type === 'LIKE' && ` 赞了你的帖子`}
                        {d.type === 'COMMENT_LIKE' && ` 赞了你的评论`}
                        {d.type === 'STAR' && ` 收藏了你的帖子`}
                        {d.type === 'FOLLOW' && ` 关注了你`}
                        {d.type === 'COMMENT' && ` 评论了你的帖子`}
                        {d.type === 'REPLY' && ` 回复了你的评论`}
                        {d.type === 'STRANGER_MSG' && ` 给你发了一条消息`}
                        {d.targetTitle && `：${d.targetTitle}`}
                      </p>
                      {d.content && <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">{d.content}</p>}
                    </>
                  )}
                </div>
                <span className="text-xs text-gray-400 flex-shrink-0">{formatTime(d.createTime)}</span>
              </div>
            ))}
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
    </div>
  )
}
