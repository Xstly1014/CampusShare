import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, Star, MessageSquare, Eye, Download, Send, ThumbsUp, FileText, Share2 } from 'lucide-react'
import schoolsData from '../data/schools.json'
import { postApi } from '../services/api'
import { toast } from '../stores/toastStore'
import { useAuth } from '../context/AuthContext'

type PostType = 'resource' | 'discussion' | 'note'

const typeLabels: Record<PostType, string> = {
  resource: '资料',
  discussion: '讨论',
  note: '笔记',
}

const typeColors: Record<PostType, string> = {
  resource: 'bg-blue-100 text-blue-700',
  discussion: 'bg-orange-100 text-orange-700',
  note: 'bg-green-100 text-green-700',
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return dateStr
}

function formatNumber(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

interface BackendPost {
  id: string
  schoolId: string
  authorId: string
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

export default function PostDetailPage() {
  const { postId, schoolId } = useParams<{ postId: string; schoolId: string }>()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const school = schoolsData.find((s) => s.id === schoolId)

  const [post, setPost] = useState<BackendPost | null>(null)
  const [loading, setLoading] = useState(true)
  const [isStarred, setIsStarred] = useState(false)
  const [isLiked, setIsLiked] = useState(false)
  const [starCount, setStarCount] = useState(0)
  const [likeCount, setLikeCount] = useState(0)
  const [toggling, setToggling] = useState(false)

  const fetchPost = useCallback(async () => {
    if (!postId) return
    setLoading(true)
    try {
      // 1. Get post detail (increments view count + records history)
      const res = await postApi.getDetail(postId)
      const p: BackendPost = res.data
      setPost(p)
      setStarCount(p.starCount || 0)
      setLikeCount(p.likeCount || 0)

      // 2. Get current user's star/like status (if logged in)
      if (isAuthenticated) {
        try {
          const statusRes = await postApi.getStatus(postId)
          setIsStarred(statusRes.data.starred)
          setIsLiked(statusRes.data.liked)
        } catch (err) {
          // Status query failed, default to false
          setIsStarred(false)
          setIsLiked(false)
        }
      }
    } catch (err) {
      toast.error('加载帖子失败')
    } finally {
      setLoading(false)
    }
  }, [postId, isAuthenticated])

  useEffect(() => {
    fetchPost()
  }, [fetchPost])

  const handleStar = async () => {
    if (!isAuthenticated) {
      toast.warning('请先登录')
      return
    }
    if (!postId || toggling) return
    setToggling(true)
    // Optimistic UI update
    const prevStarred = isStarred
    setIsStarred(!prevStarred)
    setStarCount(prevStarred ? starCount - 1 : starCount + 1)
    try {
      const res = await postApi.toggleStar(postId)
      // Sync with server response
      const serverStarred = res.data
      setIsStarred(serverStarred)
      setStarCount(serverStarred ? starCount + 1 : starCount - 1)
    } catch (err) {
      // Rollback on failure
      setIsStarred(prevStarred)
      setStarCount(prevStarred ? starCount + 1 : starCount - 1)
      toast.error((err as Error).message || '操作失败')
    } finally {
      setToggling(false)
    }
  }

  const handleLike = async () => {
    if (!isAuthenticated) {
      toast.warning('请先登录')
      return
    }
    if (!postId || toggling) return
    setToggling(true)
    const prevLiked = isLiked
    setIsLiked(!prevLiked)
    setLikeCount(prevLiked ? likeCount - 1 : likeCount + 1)
    try {
      const res = await postApi.toggleLike(postId)
      const serverLiked = res.data
      setIsLiked(serverLiked)
      setLikeCount(serverLiked ? likeCount + 1 : likeCount - 1)
    } catch (err) {
      setIsLiked(prevLiked)
      setLikeCount(prevLiked ? likeCount + 1 : likeCount - 1)
      toast.error((err as Error).message || '操作失败')
    } finally {
      setToggling(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    )
  }

  if (!post) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-gray-500 mb-4">帖子不存在</p>
          <button onClick={() => navigate(-1)} className="text-blue-600 hover:underline">
            返回
          </button>
        </div>
      </div>
    )
  }

  const postType = (post.postType as PostType) || 'discussion'

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* 顶部导航 */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
          <button
            onClick={() => navigate(-1)}
            className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
          >
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <span className="text-sm font-medium text-gray-900">
            {school ? school.name : '帖子详情'}
          </span>
          <button className="p-1.5 -mr-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <Share2 className="w-5 h-5 text-gray-600" />
          </button>
        </div>
      </div>

      {/* 帖子内容 */}
      <div className="max-w-5xl mx-auto px-4 py-4">
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          {/* 标签 */}
          <span className={`text-xs px-2.5 py-1 rounded-full font-medium mb-4 inline-block ${typeColors[postType]}`}>
            {typeLabels[postType]}
          </span>

          {/* 标题 */}
          <h1 className="text-lg font-bold text-gray-900 mb-4 leading-snug">{post.title}</h1>

          {/* 作者信息 */}
          <div className="flex items-center justify-between mb-5 pb-5 border-b border-gray-100">
            <div className="flex items-center gap-3">
              <img
                src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`}
                alt={post.authorId}
                className="w-10 h-10 rounded-full"
              />
              <div>
                <p className="text-sm font-medium text-gray-900">{post.authorId.slice(0, 8)}</p>
                <p className="text-xs text-gray-400">{formatTime(post.createTime)}</p>
              </div>
            </div>
            <button className="px-3 py-1.5 text-xs font-medium text-blue-600 bg-blue-50 rounded-full hover:bg-blue-100 transition-colors">
              + 关注
            </button>
          </div>

          {/* 资源贴：描述 + 文件信息 */}
          {postType === 'resource' && (
            <>
              {post.content && (
                <div className="mb-5">
                  <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                    {post.content}
                  </p>
                </div>
              )}

              {/* 文件卡片 */}
              {post.fileUrl && (
                <div className="bg-gray-50 rounded-xl p-4 flex items-center gap-4 mb-5">
                  <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center border border-gray-200">
                    <FileText className="w-6 h-6 text-blue-600" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{post.fileName || post.title}</p>
                    <p className="text-xs text-gray-400">
                      {post.fileType?.toUpperCase()}
                      {post.fileSize ? ` · ${(post.fileSize / 1024).toFixed(1)} KB` : ''}
                    </p>
                  </div>
                  <a
                    href={`/api${post.fileUrl}`}
                    download={post.fileName}
                    className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 transition-colors"
                  >
                    <Download className="w-4 h-4" />
                    下载
                  </a>
                </div>
              )}
            </>
          )}

          {/* 讨论帖：正文内容 */}
          {postType === 'discussion' && post.content && (
            <div className="mb-5">
              <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                {post.content}
              </p>
            </div>
          )}

          {/* 统计数据 */}
          <div className="flex items-center gap-6 text-gray-400 pt-4 border-t border-gray-100">
            <span className="flex items-center gap-1.5 text-xs">
              <Eye className="w-4 h-4" />
              {formatNumber(post.viewCount)} 浏览
            </span>
            <button
              onClick={handleStar}
              disabled={toggling}
              className={`flex items-center gap-1.5 text-xs transition-colors disabled:opacity-50 ${
                isStarred ? 'text-orange-500' : 'hover:text-orange-500'
              }`}
            >
              <Star className={`w-4 h-4 ${isStarred ? 'fill-current' : ''}`} />
              {formatNumber(starCount)} 收藏
            </button>
            <button
              onClick={handleLike}
              disabled={toggling}
              className={`flex items-center gap-1.5 text-xs transition-colors disabled:opacity-50 ${
                isLiked ? 'text-red-500' : 'hover:text-red-500'
              }`}
            >
              <ThumbsUp className={`w-4 h-4 ${isLiked ? 'fill-current' : ''}`} />
              {formatNumber(likeCount)} 点赞
            </button>
            <span className="flex items-center gap-1.5 text-xs">
              <MessageSquare className="w-4 h-4" />
              {post.commentCount} 评论
            </span>
          </div>
        </div>

        {/* 评论区 */}
        <div className="mt-4">
          <h2 className="text-sm font-semibold text-gray-900 mb-2 px-1">
            全部评论 <span className="text-gray-400 font-normal">({post.commentCount})</span>
          </h2>
          <div className="bg-white rounded-2xl border border-gray-100 px-4">
            <div className="py-12 text-center">
              <MessageSquare className="w-10 h-10 text-gray-200 mx-auto mb-2" />
              <p className="text-gray-400 text-sm">暂无评论，来发第一条评论吧</p>
            </div>
          </div>
        </div>
      </div>

      {/* 底部评论输入框 */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <div className="flex-1 relative">
            <input
              type="text"
              placeholder="说点什么..."
              className="w-full px-4 py-2.5 bg-gray-100 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all pr-10"
            />
          </div>
          <button
            className="p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  )
}
