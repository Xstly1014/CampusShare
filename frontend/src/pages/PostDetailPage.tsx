import { useState, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, Star, MessageSquare, Eye, Download, Send, ThumbsUp, FileText, Share2 } from 'lucide-react'
import { postsData, commentsData, Post, PostType, Comment } from '../data/posts'
import schoolsData from '../data/schools.json'

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

interface CommentItemProps {
  comment: Comment
  onLike: (commentId: string) => void
}

function CommentItem({ comment, onLike }: CommentItemProps) {
  return (
    <div className="flex gap-3 py-4 border-b border-gray-50">
      <img
        src={comment.author.avatar}
        alt={comment.author.username}
        className="w-9 h-9 rounded-full flex-shrink-0"
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-1">
          <span className="text-sm font-medium text-gray-900">{comment.author.username}</span>
          <span className="text-xs text-gray-400">{formatTime(comment.createdAt)}</span>
        </div>
        {comment.replyTo && (
          <p className="text-xs text-gray-400 mb-1">
            回复 <span className="text-blue-600">@{comment.replyTo.username}</span>
          </p>
        )}
        <p className="text-sm text-gray-700 leading-relaxed mb-2">{comment.content}</p>
        <div className="flex items-center gap-4">
          <button
            onClick={() => onLike(comment.id)}
            className={`flex items-center gap-1 text-xs transition-colors ${
              comment.isLiked ? 'text-red-500' : 'text-gray-400 hover:text-red-500'
            }`}
          >
            <ThumbsUp className={`w-3.5 h-3.5 ${comment.isLiked ? 'fill-current' : ''}`} />
            {comment.likes > 0 && comment.likes}
          </button>
          <button className="text-xs text-gray-400 hover:text-blue-600">回复</button>
        </div>
      </div>
    </div>
  )
}

export default function PostDetailPage() {
  const { postId, schoolId } = useParams<{ postId: string; schoolId: string }>()
  const navigate = useNavigate()

  const post = postsData.find((p) => p.id === postId)
  const school = schoolsData.find((s) => s.id === schoolId)
  const postComments = commentsData.filter((c) => c.postId === postId)

  const [isStarred, setIsStarred] = useState(post?.isStarred || false)
  const [starCount, setStarCount] = useState(post?.stars || 0)
  const [likedComments, setLikedComments] = useState<Set<string>>(
    new Set(postComments.filter((c) => c.isLiked).map((c) => c.id))
  )
  const [commentLikes, setCommentLikes] = useState<Record<string, number>>(
    Object.fromEntries(postComments.map((c) => [c.id, c.likes]))
  )
  const [newComment, setNewComment] = useState('')
  const [comments, setComments] = useState<Comment[]>(postComments)

  const handleStar = () => {
    setIsStarred(!isStarred)
    setStarCount(isStarred ? starCount - 1 : starCount + 1)
  }

  const handleCommentLike = (commentId: string) => {
    setLikedComments((prev) => {
      const next = new Set(prev)
      if (next.has(commentId)) {
        next.delete(commentId)
        setCommentLikes((cl) => ({ ...cl, [commentId]: (cl[commentId] || 0) - 1 }))
      } else {
        next.add(commentId)
        setCommentLikes((cl) => ({ ...cl, [commentId]: (cl[commentId] || 0) + 1 }))
      }
      return next
    })
  }

  const handleSubmitComment = () => {
    if (!newComment.trim()) return
    const comment: Comment = {
      id: `c-new-${Date.now()}`,
      postId: postId || '',
      author: {
        id: 'me',
        username: '我',
        avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=me',
      },
      content: newComment,
      createdAt: new Date().toISOString().split('T')[0],
      likes: 0,
      isLiked: false,
    }
    setComments([comment, ...comments])
    setNewComment('')
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
          <span className={`text-xs px-2.5 py-1 rounded-full font-medium mb-4 inline-block ${typeColors[post.type]}`}>
            {typeLabels[post.type]}
          </span>

          {/* 标题 */}
          <h1 className="text-lg font-bold text-gray-900 mb-4 leading-snug">{post.title}</h1>

          {/* 作者信息 */}
          <div className="flex items-center justify-between mb-5 pb-5 border-b border-gray-100">
            <div className="flex items-center gap-3">
              <img
                src={post.author.avatar}
                alt={post.author.username}
                className="w-10 h-10 rounded-full"
              />
              <div>
                <p className="text-sm font-medium text-gray-900">{post.author.username}</p>
                <p className="text-xs text-gray-400">{formatTime(post.createdAt)}</p>
              </div>
            </div>
            <button className="px-3 py-1.5 text-xs font-medium text-blue-600 bg-blue-50 rounded-full hover:bg-blue-100 transition-colors">
              + 关注
            </button>
          </div>

          {/* 资源贴：描述 + 文件信息 */}
          {post.type === 'resource' && (
            <>
              {post.description && (
                <div className="mb-5">
                  <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                    {post.description}
                  </p>
                </div>
              )}

              {/* 文件卡片 */}
              <div className="bg-gray-50 rounded-xl p-4 flex items-center gap-4 mb-5">
                <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center border border-gray-200">
                  <FileText className="w-6 h-6 text-blue-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{post.title}</p>
                  <p className="text-xs text-gray-400">
                    {post.fileType?.toUpperCase()} · {post.fileSize}
                  </p>
                </div>
                <button className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 transition-colors">
                  <Download className="w-4 h-4" />
                  下载
                </button>
              </div>
            </>
          )}

          {/* 讨论帖：正文内容 */}
          {post.type === 'discussion' && post.content && (
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
              {formatNumber(post.views)} 浏览
            </span>
            <button
              onClick={handleStar}
              className={`flex items-center gap-1.5 text-xs transition-colors ${
                isStarred ? 'text-orange-500' : 'hover:text-orange-500'
              }`}
            >
              <Star className={`w-4 h-4 ${isStarred ? 'fill-current' : ''}`} />
              {formatNumber(starCount)} 收藏
            </button>
            <span className="flex items-center gap-1.5 text-xs">
              <MessageSquare className="w-4 h-4" />
              {comments.length} 评论
            </span>
          </div>
        </div>

        {/* 评论区 */}
        <div className="mt-4">
          <h2 className="text-sm font-semibold text-gray-900 mb-2 px-1">
            全部评论 <span className="text-gray-400 font-normal">({comments.length})</span>
          </h2>
          <div className="bg-white rounded-2xl border border-gray-100 px-4">
            {comments.length > 0 ? (
              comments.map((comment) => (
                <CommentItem
                  key={comment.id}
                  comment={{ ...comment, isLiked: likedComments.has(comment.id), likes: commentLikes[comment.id] || comment.likes }}
                  onLike={handleCommentLike}
                />
              ))
            ) : (
              <div className="py-12 text-center">
                <MessageSquare className="w-10 h-10 text-gray-200 mx-auto mb-2" />
                <p className="text-gray-400 text-sm">暂无评论，来发第一条评论吧</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 底部评论输入框 */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <div className="flex-1 relative">
            <input
              type="text"
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSubmitComment()}
              placeholder="说点什么..."
              className="w-full px-4 py-2.5 bg-gray-100 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all pr-10"
            />
          </div>
          <button
            onClick={handleSubmitComment}
            disabled={!newComment.trim()}
            className="p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  )
}
