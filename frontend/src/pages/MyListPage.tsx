import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { ChevronLeft, Clock, Star, ThumbsUp, Eye, MessageSquare, FileText, BadgeCheck, Crown, Heart, File } from 'lucide-react'
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
  authorLevel?: string
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

function getCreatorLevelColor(level?: string): string {
  switch (level) {
    case 'AUTHORITY': return 'text-yellow-500'
    case 'SENIOR': return 'text-orange-500'
    case 'INTERMEDIATE': return 'text-purple-500'
    case 'JUNIOR': return 'text-blue-500'
    default: return 'text-blue-500'
  }
}

function CreatorLevelIcon({ level }: { level?: string }) {
  if (!level || level === 'NONE') return null
  const colorClass = getCreatorLevelColor(level)
  if (level === 'AUTHORITY' || level === 'SENIOR') {
    return <Crown className={`w-3.5 h-3.5 ${colorClass}`} />
  }
  return <BadgeCheck className={`w-3.5 h-3.5 ${colorClass}`} />
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

function formatFileSize(bytes?: number): string | undefined {
  if (!bytes) return undefined
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function getFileUrl(url?: string): string | undefined {
  if (!url) return undefined
  return url.startsWith('/files/') ? `/api${url}` : url
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
          isCreator: p.authorRole === 'CREATOR' || p.authorRole === 'ADMIN' || (p.authorLevel && p.authorLevel !== 'NONE')
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
            {posts.map((post) => {
              const isImage = post.fileType?.startsWith('image/')
              const hasFile = !!post.fileUrl && !isImage
              const avatarUrl = post.authorAvatar
                ? (post.authorAvatar.startsWith('/files/') ? `/api${post.authorAvatar}` : post.authorAvatar)
                : `https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`
              return (
              <div
                key={post.id}
                onClick={() => navigate(`/school/${post.schoolId}/post/${post.id}`)}
                className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-4 cursor-pointer"
              >
                {/* 顶部作者栏 */}
                <div className="flex items-center gap-2 mb-2">
                  <img
                    src={avatarUrl}
                    alt={post.authorName || post.authorId}
                    className="w-8 h-8 rounded-full flex-shrink-0 object-cover"
                  />
                  <span className="text-sm text-gray-700 font-medium flex items-center gap-1">
                    {post.authorName || post.authorId.slice(0, 8)}
                    {post.authorLevel && post.authorLevel !== 'NONE' && (
                      <CreatorLevelIcon level={post.authorLevel} />
                    )}
                  </span>
                  <span className="text-xs text-gray-400 ml-1">{formatTime(post.createTime)}</span>
                </div>

                {/* 内容区 */}
                <div>
                  <h3 className="text-base font-semibold text-gray-900 leading-snug line-clamp-2">
                    {post.title}
                  </h3>
                  {post.content && (
                    <p className="text-sm text-gray-600 mt-1 line-clamp-2">{post.content}</p>
                  )}

                  {/* 图片附件缩略图 */}
                  {isImage && post.fileUrl && (
                    <img
                      src={getFileUrl(post.fileUrl)}
                      alt={post.fileName || '附件'}
                      className="w-16 h-16 object-cover rounded mt-2"
                    />
                  )}

                  {/* 文件附件条 */}
                  {hasFile && (
                    <div className="flex items-center gap-2 bg-gray-50 rounded-lg p-2 mt-2">
                      <File className="w-4 h-4 text-gray-500 flex-shrink-0" />
                      <span className="text-sm text-gray-700 truncate flex-1">{post.fileName || '附件'}</span>
                      {formatFileSize(post.fileSize) && (
                        <span className="text-xs text-gray-400 flex-shrink-0">{formatFileSize(post.fileSize)}</span>
                      )}
                    </div>
                  )}
                </div>

                {/* 底部数据栏 */}
                <div className="flex items-center gap-4 mt-2 text-xs text-gray-400">
                  <span className="flex items-center gap-1">
                    <Eye className="w-3.5 h-3.5" />
                    {formatNumber(post.viewCount)}
                  </span>
                  <span className="flex items-center gap-1">
                    <MessageSquare className="w-3.5 h-3.5" />
                    {formatNumber(post.commentCount)}
                  </span>
                  <span className="flex items-center gap-1">
                    <Star className="w-3.5 h-3.5" />
                    {formatNumber(post.starCount)}
                  </span>
                  <span className="flex items-center gap-1">
                    <Heart className="w-3.5 h-3.5" />
                    {formatNumber(post.likeCount)}
                  </span>
                </div>
              </div>
              )
            })}
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
