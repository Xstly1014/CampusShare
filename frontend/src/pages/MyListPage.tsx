import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { ChevronLeft, Clock, Star, ThumbsUp, Eye, MessageSquare, FileText, BadgeCheck, Crown, Heart, File } from 'lucide-react'
import { postApi } from '../services/api'
import { toast } from '../stores/toastStore'

type ListType = 'history' | 'starred' | 'liked' | 'mine' | 'comments'

interface BackendPost {
  id: string
  schoolId?: string
  categoryId?: string
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

interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
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
  categoryId?: string
  userId: string
  username: string
  avatarUrl: string
  content: string
  likeCount: number
  createTime: string
}

const PAGE_SIZE = 20

const listConfig: Record<ListType, {
  title: string
  icon: React.ReactNode
  fetcher: (page: number, size: number) => Promise<any>
  isComments?: boolean
}> = {
  history: { title: '浏览历史', icon: <Clock className="w-5 h-5" />, fetcher: postApi.getHistory },
  starred: { title: '我的收藏', icon: <Star className="w-5 h-5" />, fetcher: postApi.getStarred },
  liked: { title: '我的点赞', icon: <ThumbsUp className="w-5 h-5" />, fetcher: postApi.getLiked },
  mine: { title: '我的帖子', icon: <FileText className="w-5 h-5" />, fetcher: postApi.getMyPosts },
  comments: { title: '我的回复', icon: <MessageSquare className="w-5 h-5" />, fetcher: async () => postApi.getMyComments(), isComments: true },
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
  const isCommentsType = listType === 'comments'

  const [posts, setPosts] = useState<BackendPost[]>([])
  const [comments, setComments] = useState<CommentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const loadingRef = useRef(false)
  const observerRef = useRef<HTMLDivElement>(null)

  const loadPage = useCallback(async (pageNum: number, isLoadMore = false) => {
    if (loadingRef.current) return
    loadingRef.current = true

    if (isLoadMore) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }

    try {
      const res = await config.fetcher(pageNum, PAGE_SIZE)
      if (isCommentsType) {
        const list = res.data || []
        setComments(list)
        setTotal(list.length)
        setHasMore(false)
      } else {
        const pageData: PageResponse<BackendPost> = res.data || { records: [], total: 0, size: PAGE_SIZE, current: 1, pages: 0 }
        const mapped = pageData.records.map((p: any) => ({
          ...p,
          isCreator: p.authorRole === 'CREATOR' || p.authorRole === 'ADMIN' || (p.authorLevel && p.authorLevel !== 'NONE')
        }))
        if (isLoadMore) {
          setPosts(prev => [...prev, ...mapped])
        } else {
          setPosts(mapped)
        }
        setTotal(pageData.total)
        setPage(pageNum)
        setHasMore(pageNum * PAGE_SIZE < pageData.total)
      }
    } catch (err) {
      toast.error('加载失败')
    } finally {
      setLoading(false)
      setLoadingMore(false)
      loadingRef.current = false
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listType])

  useEffect(() => {
    setPosts([])
    setComments([])
    setTotal(0)
    setPage(1)
    setHasMore(false)
    loadPage(1, false)
  }, [loadPage, location.key])

  useEffect(() => {
    if (isCommentsType || !hasMore || loading || loadingMore) return
    const sentinel = observerRef.current
    if (!sentinel) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingRef.current) {
          loadPage(page + 1, true)
        }
      },
      { rootMargin: '300px' }
    )

    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, page, loadPage, loading, loadingMore, isCommentsType, posts.length])

  const displayCount = total
  const itemCount = isCommentsType ? comments.length : posts.length

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
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
            {displayCount > 0 && (
              <span className="text-xs text-gray-400">({displayCount})</span>
            )}
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
            <p className="text-gray-400 text-sm">加载中...</p>
          </div>
        ) : isCommentsType && comments.length > 0 ? (
          <div className="space-y-3">
            {comments.map((comment) => {
              let postUrl: string
              if (comment.schoolId) {
                postUrl = `/school/${comment.schoolId}/post/${comment.postId}#comment-${comment.id}`
              } else if (comment.categoryId) {
                postUrl = `/category/${comment.categoryId}/post/${comment.postId}#comment-${comment.id}`
              } else {
                postUrl = `/school/1/post/${comment.postId}#comment-${comment.id}`
              }
              return (
              <div
                key={comment.id}
                onClick={() => navigate(postUrl)}
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
                      <span>点击查看评论位置</span>
                    </div>
                  </div>
                </div>
              </div>
              )
            })}
          </div>
        ) : !isCommentsType && posts.length > 0 ? (
          <div className="space-y-3">
            {posts.map((post) => {
              const isImage = post.fileType?.startsWith('image/')
              const hasFile = !!post.fileUrl && !isImage
              const avatarUrl = post.authorAvatar
                ? (post.authorAvatar.startsWith('/files/') ? `/api${post.authorAvatar}` : post.authorAvatar)
                : `https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`
              let postUrl: string
              if (post.schoolId) {
                postUrl = `/school/${post.schoolId}/post/${post.id}`
              } else if (post.categoryId) {
                postUrl = `/category/${post.categoryId}/post/${post.id}`
              } else {
                postUrl = `/school/1/post/${post.id}`
              }
              return (
              <div
                key={post.id}
                onClick={() => navigate(postUrl)}
                className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-4 cursor-pointer"
              >
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

                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${
                      post.postType === 'resource'
                        ? 'bg-blue-50 text-blue-600'
                        : 'bg-orange-50 text-orange-600'
                    }`}>
                      {post.postType === 'resource' ? '资料' : '讨论'}
                    </span>
                  </div>
                  <h3 className="text-base font-semibold text-gray-900 leading-snug" style={{display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'}}>
                    {post.title}
                  </h3>
                  {post.content && post.content.trim() && (
                    <p className="text-sm text-gray-600 mt-1" style={{display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'}}>
                      {post.content.replace(/<[^>]*>/g, '')}
                    </p>
                  )}

                  {isImage && post.fileUrl && (
                    <img
                      src={getFileUrl(post.fileUrl)}
                      alt={post.fileName || '附件'}
                      className="w-16 h-16 object-cover rounded mt-2"
                    />
                  )}

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

        {!isCommentsType && hasMore && (
          <div ref={observerRef} className="py-6 text-center">
            {loadingMore ? (
              <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
            ) : (
              <button
                onClick={() => loadPage(page + 1, true)}
                className="px-5 py-2 text-sm text-blue-600 hover:bg-blue-50 rounded-full transition-colors"
              >
                加载更多
              </button>
            )}
          </div>
        )}

        {!isCommentsType && !hasMore && itemCount > 0 && (
          <div className="py-6 text-center text-xs text-gray-400">
            共 {total} 条记录
          </div>
        )}
      </div>
    </div>
  )
}
