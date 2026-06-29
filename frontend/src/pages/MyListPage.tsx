import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { ChevronLeft, Clock, Star, ThumbsUp, Eye, MessageSquare, FileText, BadgeCheck } from 'lucide-react'
import { postApi } from '../services/api'
import { toast } from '../stores/toastStore'

type ListType = 'history' | 'starred' | 'liked' | 'mine' | 'comments'

interface BackendPost {
  id: string
  schoolId: string
  authorId: string
  authorName?: string
  authorAvatar?: string
  authorRole?: string
  isCreator?: boolean
  postType: string
  title: string
  content: string
  fileUrl?: string
  fileName?: string
  fileType?: string
  fileSize?: number
  viewCount: number
  starCount: number
  likeCount: number
  commentCount: number
  createTime: string
}

interface CommentItem {
  id: string
  postId: string
  schoolId?: string
  userId: string
  username: string
  avatarUrl: string
  content: string
  likeCount: number
  createTime: string
}

const listConfig: Record<ListType, { title: string; icon: React.ReactNode; fetcher: (page: number, size: number) => Promise<any> }> = {
  history: { title: '浏览历史', icon: <Clock className="w-5 h-5" />, fetcher: postApi.getHistory },
  starred: { title: '我的收藏', icon: <Star className="w-5 h-5" />, fetcher: postApi.getStarred },
  liked: { title: '我的点赞', icon: <ThumbsUp className="w-5 h-5" />, fetcher: postApi.getLiked },
  mine: { title: '我的帖子', icon: <FileText className="w-5 h-5" />, fetcher: postApi.getMyPosts },
  comments: { title: '我的回复', icon: <MessageSquare className="w-5 h-5" />, fetcher: async () => postApi.getMyComments() },
}

function formatNumber(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr.replace(' ', 'T'))
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))

  const isSameDay = date.toDateString() === now.toDateString()
  if (isSameDay) {
    if (seconds < 60) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
  }

  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return '昨天'

  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

export default function MyListPage() {
  const { type } = useParams<{ type: string }>()
  const navigate = useNavigate()
  const location = useLocation()

  const listType = (type as ListType) || 'history'
  const config = listConfig[listType]

  const [posts, setPosts] = useState<BackendPost[]>([])
  const [comments, setComments] = useState<CommentItem[]>([])
  const [loading, setLoading] = useState(true)

  const fetchPosts = useCallback(async () => {
    setLoading(true)
    try {
      const res = await listConfig[listType].fetcher(1, 50)
      if (listType === 'comments') {
        setComments(res.data || [])
        setPosts([])
      } else {
        setPosts((res.data || []).map((p: any) => ({
          ...p,
          isCreator: p.authorRole === 'CREATOR' || p.authorRole === 'ADMIN'
        })))
        setComments([])
      }
    } catch (err) {
      toast.error('加载失败')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listType])

  // Fetch on mount, on type change, and on navigation back to this page
  useEffect(() => {
    fetchPosts()
  }, [fetchPosts, location.key])

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 顶部导航 */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button
            onClick={() => navigate('/profile')}
            className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
          >
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <span className="text-gray-700">{config.icon}</span>
            <span className="text-sm font-medium text-gray-900">{config.title}</span>
            {((listType === 'comments' ? comments.length : posts.length) > 0) && (
              <span className="text-xs text-gray-400">({listType === 'comments' ? comments.length : posts.length})</span>
            )}
          </div>
        </div>
      </div>

      {/* 列表内容 */}
      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
            <p className="text-gray-400 text-sm">加载中...</p>
          </div>
        ) : listType === 'comments' && comments.length > 0 ? (
          <div className="space-y-3">
            {comments.map((comment) => (
              <div
                key={comment.id}
                onClick={() => navigate(`/school/${comment.schoolId || '1'}/post/${comment.postId}`)}
                className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-4 cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <img
                    src={comment.avatarUrl.startsWith('/files/') ? `/api${comment.avatarUrl}` : comment.avatarUrl}
                    alt={comment.username}
                    className="w-10 h-10 rounded-full flex-shrink-0"
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium text-gray-900">{comment.username}</span>
                      <span className="text-xs text-gray-400">{formatTime(comment.createTime)}</span>
                    </div>
                    <p className="text-sm text-gray-700 leading-relaxed mb-1 line-clamp-3">{comment.content}</p>
                    <div className="flex items-center gap-1 mt-2 text-xs text-gray-400">
                      <MessageSquare className="w-3.5 h-3.5" />
                      <span>回复的帖子</span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : listType !== 'comments' && posts.length > 0 ? (
          <div className="space-y-3">
            {posts.map((post) => (
              <div
                key={post.id}
                onClick={() => navigate(`/school/${post.schoolId}/post/${post.id}`)}
                className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-4 cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  {/* 头像 */}
                  <img
                    src={post.authorAvatar
                      ? (post.authorAvatar.startsWith('/files/') ? `/api${post.authorAvatar}` : post.authorAvatar)
                      : `https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`}
                    alt={post.authorName || post.authorId}
                    className="w-10 h-10 rounded-full flex-shrink-0"
                  />

                  <div className="flex-1 min-w-0">
                    {/* 标签行 */}
                    <div className="flex items-center gap-2 mb-1.5">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                        post.postType === 'resource'
                          ? 'bg-blue-100 text-blue-700'
                          : 'bg-orange-100 text-orange-700'
                      }`}>
                        {post.postType === 'resource' ? '资料' : '讨论'}
                      </span>
                      <span className="text-xs text-gray-400">{formatTime(post.createTime)}</span>
                    </div>

                    {/* 标题 */}
                    <h3 className="text-sm font-medium text-gray-900 mb-1 leading-snug line-clamp-2">
                      {post.title}
                    </h3>

                    {/* 内容摘要 */}
                    {post.content && (
                      <p className="text-xs text-gray-500 mb-2 line-clamp-2">{post.content}</p>
                    )}

                    {/* 作者信息 */}
                    <div className="flex items-center justify-between mt-2">
                      <span className="text-xs text-gray-500 flex items-center gap-0.5">
                        {post.authorName || post.authorId.slice(0, 8)}
                        {post.isCreator && <span title="认证创作者"><BadgeCheck className="w-3.5 h-3.5 text-blue-500" /></span>}
                      </span>

                      <div className="flex items-center gap-3 text-gray-400">
                        <span className="flex items-center gap-1 text-xs">
                          <Eye className="w-3.5 h-3.5" />
                          {formatNumber(post.viewCount)}
                        </span>
                        <span className="flex items-center gap-1 text-xs">
                          <Star className="w-3.5 h-3.5" />
                          {formatNumber(post.starCount)}
                        </span>
                        <span className="flex items-center gap-1 text-xs">
                          <MessageSquare className="w-3.5 h-3.5" />
                          {formatNumber(post.commentCount)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
              {config.icon}
            </div>
            <p className="text-gray-400 text-sm">暂无{config.title}记录</p>
          </div>
        )}
      </div>
    </div>
  )
}
