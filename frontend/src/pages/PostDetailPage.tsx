import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, Star, MessageSquare, Eye, Download, Send, ThumbsUp, FileText, Share2, Trash2, Edit3, CornerDownRight } from 'lucide-react'
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
  const date = new Date(dateStr.replace(' ', 'T'))
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))

  // Same day: relative time
  const isSameDay = date.toDateString() === now.toDateString()
  if (isSameDay) {
    if (seconds < 60) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
  }

  // Yesterday
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天'
  }

  // Same year: M月D日
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }

  // Different year: XXXX年M月D日
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function formatNumber(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

interface BackendPost {
  id: string
  schoolId: string
  authorId: string
  authorName?: string
  authorAvatar?: string
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
  userId: string
  username: string
  avatarUrl: string
  parentId?: string
  replyToUserId?: string
  replyToUsername?: string
  content: string
  likeCount: number
  liked?: boolean
  isAuthor?: boolean
  createTime: string
}

export default function PostDetailPage() {
  const { postId, schoolId } = useParams<{ postId: string; schoolId: string }>()
  const navigate = useNavigate()
  const { user, isAuthenticated } = useAuth()

  const school = schoolsData.find((s) => s.id === schoolId)

  const [post, setPost] = useState<BackendPost | null>(null)
  const [loading, setLoading] = useState(true)
  const [isStarred, setIsStarred] = useState(false)
  const [isLiked, setIsLiked] = useState(false)
  const [starCount, setStarCount] = useState(0)
  const [likeCount, setLikeCount] = useState(0)
  const [toggling, setToggling] = useState(false)
  const [comments, setComments] = useState<CommentItem[]>([])
  const [newComment, setNewComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [replyTo, setReplyTo] = useState<CommentItem | null>(null)
  const [showEditModal, setShowEditModal] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const [editContent, setEditContent] = useState('')
  const [confirmDialog, setConfirmDialog] = useState<{ title: string; message: string; onConfirm: () => void } | null>(null)

  const fetchPost = useCallback(async () => {
    if (!postId) return
    setLoading(true)
    try {
      const res = await postApi.getDetail(postId)
      const p: BackendPost = res.data
      setPost(p)
      setStarCount(p.starCount || 0)
      setLikeCount(p.likeCount || 0)

      if (isAuthenticated) {
        try {
          const statusRes = await postApi.getStatus(postId)
          setIsStarred(statusRes.data.starred)
          setIsLiked(statusRes.data.liked)
        } catch {
          setIsStarred(false)
          setIsLiked(false)
        }
      }
    } catch (err) {
      toast.error((err as Error).message || '加载帖子失败')
    } finally {
      setLoading(false)
    }
  }, [postId, isAuthenticated])

  useEffect(() => {
    fetchPost()
  }, [fetchPost])

  const fetchComments = useCallback(async () => {
    if (!postId) return
    try {
      const res = await postApi.getComments(postId)
      setComments(res.data || [])
    } catch {
      // ignore
    }
  }, [postId])

  useEffect(() => {
    fetchComments()
  }, [fetchComments])

  // ================================================================
  // Comment actions
  // ================================================================
  const handleSubmitComment = async () => {
    if (!newComment.trim() || !postId || submittingComment) return
    if (!isAuthenticated) {
      toast.warning('请先登录')
      return
    }
    setSubmittingComment(true)
    try {
      // For nested replies: parentId is always the root comment's id
      // replyToUserId is the actual user being replied to
      let parentId = replyTo?.id
      let replyToUserId = replyTo?.userId
      // If replying to a reply (not a root comment), use the root comment's parentId
      if (replyTo?.parentId) {
        parentId = replyTo.parentId
      }
      const res = await postApi.createComment(
        postId,
        newComment.trim(),
        parentId,
        replyToUserId,
      )
      setComments((prev) => [...prev, res.data])
      setNewComment('')
      setReplyTo(null)
      if (post) {
        setPost({ ...post, commentCount: post.commentCount + 1 })
      }
    } catch (err) {
      toast.error((err as Error).message || '评论失败')
    } finally {
      setSubmittingComment(false)
    }
  }

  const handleDeleteComment = async (commentId: string) => {
    setConfirmDialog({
      title: '删除评论',
      message: '确定删除这条评论吗？',
      onConfirm: async () => {
        setConfirmDialog(null)
        try {
          await postApi.deleteComment(commentId)
          setComments((prev) => prev.filter((c) => c.id !== commentId))
          if (post) {
            setPost({ ...post, commentCount: Math.max(0, post.commentCount - 1) })
          }
          toast.success('删除成功')
        } catch (err) {
          toast.error((err as Error).message || '删除失败')
        }
      },
    })
  }

  const handleCommentLike = async (commentId: string) => {
    if (!isAuthenticated) {
      toast.warning('请先登录')
      return
    }
    // Optimistic update
    setComments((prev) =>
      prev.map((c) =>
        c.id === commentId
          ? {
              ...c,
              liked: !c.liked,
              likeCount: c.liked ? c.likeCount - 1 : c.likeCount + 1,
            }
          : c,
      ),
    )
    try {
      await postApi.toggleCommentLike(commentId)
    } catch {
      // Rollback
      setComments((prev) =>
        prev.map((c) =>
          c.id === commentId
            ? {
                ...c,
                liked: !c.liked,
                likeCount: c.liked ? c.likeCount - 1 : c.likeCount + 1,
              }
            : c,
        ),
      )
      toast.error('操作失败')
    }
  }

  // ================================================================
  // Post actions
  // ================================================================
  const handleStar = async () => {
    if (!isAuthenticated) {
      toast.warning('请先登录')
      return
    }
    if (!postId || toggling) return
    setToggling(true)
    const prevStarred = isStarred
    setIsStarred(!prevStarred)
    setStarCount(prevStarred ? starCount - 1 : starCount + 1)
    try {
      const res = await postApi.toggleStar(postId)
      setIsStarred(res.data)
      setStarCount(res.data ? starCount + 1 : starCount - 1)
    } catch {
      setIsStarred(prevStarred)
      setStarCount(prevStarred ? starCount + 1 : starCount - 1)
      toast.error('操作失败')
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
      setIsLiked(res.data)
      setLikeCount(res.data ? likeCount + 1 : likeCount - 1)
    } catch {
      setIsLiked(prevLiked)
      setLikeCount(prevLiked ? likeCount + 1 : likeCount - 1)
      toast.error('操作失败')
    } finally {
      setToggling(false)
    }
  }

  const handleDeletePost = async () => {
    if (!postId) return
    setConfirmDialog({
      title: '删除帖子',
      message: '确定删除这个帖子吗？删除后不可恢复。',
      onConfirm: async () => {
        setConfirmDialog(null)
        try {
          await postApi.delete(postId)
          toast.success('删除成功')
          navigate(-1)
        } catch (err) {
          toast.error((err as Error).message || '删除失败')
        }
      },
    })
  }

  const handleEditPost = () => {
    if (!post) return
    setEditTitle(post.title)
    setEditContent(post.content || '')
    setShowEditModal(true)
  }

  const handleSaveEdit = async () => {
    if (!postId || !editTitle.trim()) return
    try {
      await postApi.edit(postId, { title: editTitle.trim(), content: editContent })
      setPost((prev) => prev ? { ...prev, title: editTitle.trim(), content: editContent } : prev)
      setShowEditModal(false)
      toast.success('修改成功')
    } catch (err) {
      toast.error((err as Error).message || '修改失败')
    }
  }

  // ================================================================
  // Render helpers
  // ================================================================
  const isPostAuthor = user?.id === post?.authorId

  // Separate top-level comments and replies
  const topLevelComments = comments.filter((c) => !c.parentId)
  const getReplies = (parentId: string) => comments.filter((c) => c.parentId === parentId)

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
          <div className="flex items-center gap-1">
            {isPostAuthor && (
              <>
                <button
                  onClick={handleEditPost}
                  className="p-1.5 hover:bg-gray-100 rounded-full transition-colors"
                >
                  <Edit3 className="w-4 h-4 text-gray-500" />
                </button>
                <button
                  onClick={handleDeletePost}
                  className="p-1.5 hover:bg-red-50 rounded-full transition-colors"
                >
                  <Trash2 className="w-4 h-4 text-red-400" />
                </button>
              </>
            )}
            <button className="p-1.5 hover:bg-gray-100 rounded-full transition-colors">
              <Share2 className="w-4 h-4 text-gray-600" />
            </button>
          </div>
        </div>
      </div>

      {/* 帖子内容 */}
      <div className="max-w-5xl mx-auto px-4 py-4">
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <span className={`text-xs px-2.5 py-1 rounded-full font-medium mb-4 inline-block ${typeColors[postType]}`}>
            {typeLabels[postType]}
          </span>

          <h1 className="text-lg font-bold text-gray-900 mb-4 leading-snug">{post.title}</h1>

          <div className="flex items-center justify-between mb-5 pb-5 border-b border-gray-100">
            <div className="flex items-center gap-3">
              <img
                onClick={() => navigate(post.authorId === user?.id ? '/profile' : `/user/${post.authorId}`)}
                src={post.authorAvatar
                  ? (post.authorAvatar.startsWith('/files/') ? `/api${post.authorAvatar}` : post.authorAvatar)
                  : `https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`}
                alt={post.authorName || post.authorId}
                className="w-10 h-10 rounded-full cursor-pointer"
              />
              <div>
                <p className="text-sm font-medium text-gray-900">{post.authorName || post.authorId.slice(0, 8)}</p>
                <p className="text-xs text-gray-400">{formatTime(post.createTime)}</p>
              </div>
            </div>
          </div>

          {postType === 'resource' && (
            <>
              {post.content && (
                <div className="mb-5">
                  <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{post.content}</p>
                </div>
              )}
              {post.fileUrl && (
                <div className="bg-gray-50 rounded-xl p-4 flex items-center gap-4 mb-5">
                  <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center border border-gray-200">
                    <FileText className="w-6 h-6 text-blue-600" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{post.fileName || post.title}</p>
                    <p className="text-xs text-gray-400">
                      {post.fileType?.toUpperCase()}
                      {post.fileSize ? ` · ${post.fileSize >= 1048576 ? (post.fileSize / 1048576).toFixed(2) + ' MB' : (post.fileSize / 1024).toFixed(1) + ' KB'}` : ''}
                    </p>
                  </div>
                  <button
                    onClick={async () => {
                      try {
                        const res = await fetch(`/api${post.fileUrl}`)
                        if (!res.ok) throw new Error('文件不存在')
                        const blob = await res.blob()
                        const url = URL.createObjectURL(blob)
                        const a = document.createElement('a')
                        a.href = url
                        a.download = post.fileName || 'download'
                        document.body.appendChild(a)
                        a.click()
                        document.body.removeChild(a)
                        URL.revokeObjectURL(url)
                        toast.success('下载成功')
                      } catch (err) {
                        toast.error((err as Error).message || '下载失败')
                      }
                    }}
                    className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 transition-colors"
                  >
                    <Download className="w-4 h-4" />
                    下载
                  </button>
                </div>
              )}
            </>
          )}

          {postType === 'discussion' && post.content && (
            <div className="mb-5">
              <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{post.content}</p>
            </div>
          )}

          <div className="flex items-center gap-6 text-gray-400 pt-4 border-t border-gray-100">
            <span className="flex items-center gap-1.5 text-xs">
              <Eye className="w-4 h-4" />
              {formatNumber(post.viewCount)} 浏览
            </span>
            <button
              onClick={handleStar}
              disabled={toggling}
              className={`flex items-center gap-1.5 text-xs transition-colors disabled:opacity-50 ${isStarred ? 'text-orange-500' : 'hover:text-orange-500'}`}
            >
              <Star className={`w-4 h-4 ${isStarred ? 'fill-current' : ''}`} />
              {formatNumber(starCount)} 收藏
            </button>
            <button
              onClick={handleLike}
              disabled={toggling}
              className={`flex items-center gap-1.5 text-xs transition-colors disabled:opacity-50 ${isLiked ? 'text-red-500' : 'hover:text-red-500'}`}
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
            全部评论 <span className="text-gray-400 font-normal">({comments.length})</span>
          </h2>
          <div className="bg-white rounded-2xl border border-gray-100 px-4">
            {topLevelComments.length > 0 ? (
              topLevelComments.map((comment) => (
                <div key={comment.id}>
                  {/* Top-level comment */}
                  <CommentRow
                    comment={comment}
                    onLike={handleCommentLike}
                    onDelete={handleDeleteComment}
                    onReply={setReplyTo}
                  />
                  {/* Replies (楼中楼) - all replies under root comment, ordered by time */}
                  {getReplies(comment.id).map((reply) => (
                    <div key={reply.id} className="flex gap-3 py-3 pl-12 border-b border-gray-50 last:border-0">
                      <CornerDownRight className="w-4 h-4 text-gray-300 flex-shrink-0 mt-1" />
                      <img
                        onClick={() => navigate(reply.userId === user?.id ? '/profile' : `/user/${reply.userId}`)}
                        src={reply.avatarUrl.startsWith('/files/') ? `/api${reply.avatarUrl}` : reply.avatarUrl}
                        alt={reply.username}
                        className="w-7 h-7 rounded-full flex-shrink-0 cursor-pointer"
                      />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between mb-0.5">
                          <div className="flex items-center gap-1 flex-wrap">
                            <span className="text-xs font-medium text-gray-900">{reply.username}</span>
                            {reply.replyToUsername && (
                              <span className="text-xs text-gray-400">回复 <span className="text-blue-500">@{reply.replyToUsername}</span></span>
                            )}
                          </div>
                          <span className="text-xs text-gray-400">{formatTime(reply.createTime)}</span>
                        </div>
                        <p className="text-sm text-gray-700 leading-relaxed mb-1">{reply.content}</p>
                        <div className="flex items-center gap-4">
                          <button
                            onClick={() => handleCommentLike(reply.id)}
                            className={`flex items-center gap-1 text-xs transition-colors ${reply.liked ? 'text-red-500' : 'text-gray-400 hover:text-red-500'}`}
                          >
                            <ThumbsUp className={`w-3 h-3 ${reply.liked ? 'fill-current' : ''}`} />
                            {reply.likeCount > 0 && reply.likeCount}
                          </button>
                          <button
                            onClick={() => setReplyTo(reply)}
                            className="text-xs text-gray-400 hover:text-blue-600"
                          >
                            回复
                          </button>
                          {reply.isAuthor && (
                            <button
                              onClick={() => handleDeleteComment(reply.id)}
                              className="text-xs text-gray-400 hover:text-red-500"
                            >
                              删除
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
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
        <div className="max-w-5xl mx-auto px-4 py-3">
          {replyTo && (
            <div className="flex items-center justify-between mb-2 px-1">
              <span className="text-xs text-gray-500">
                回复 @{replyTo.username}
              </span>
              <button
                onClick={() => setReplyTo(null)}
                className="text-xs text-gray-400 hover:text-gray-600"
              >
                取消回复
              </button>
            </div>
          )}
          <div className="flex items-center gap-3">
            <input
              type="text"
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSubmitComment()}
              placeholder={replyTo ? `回复 @${replyTo.username}...` : '说点什么...'}
              className="flex-1 px-4 py-2.5 bg-gray-100 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
            <button
              onClick={handleSubmitComment}
              disabled={!newComment.trim() || submittingComment}
              className="p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* 编辑帖子弹窗 */}
      {showEditModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center" onClick={() => setShowEditModal(false)}>
          <div className="bg-white w-full sm:max-w-lg sm:rounded-2xl rounded-t-3xl p-6 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-gray-900 mb-5">编辑帖子</h2>
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">标题</label>
              <input
                type="text"
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                maxLength={100}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
              />
            </div>
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">内容</label>
              <textarea
                value={editContent}
                onChange={(e) => setEditContent(e.target.value)}
                rows={5}
                maxLength={2000}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all resize-none"
              />
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setShowEditModal(false)}
                className="flex-1 py-3 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSaveEdit}
                disabled={!editTitle.trim()}
                className="flex-1 py-3 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 自定义确认弹窗 */}
      {confirmDialog && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={() => setConfirmDialog(null)}>
          <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-bold text-gray-900 mb-2">{confirmDialog.title}</h3>
            <p className="text-sm text-gray-500 mb-5">{confirmDialog.message}</p>
            <div className="flex gap-3">
              <button
                onClick={() => setConfirmDialog(null)}
                className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={confirmDialog.onConfirm}
                className="flex-1 py-2.5 bg-red-500 text-white rounded-xl text-sm font-medium hover:bg-red-600 transition-colors"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
// ================================================================
function CommentRow({
  comment,
  onLike,
  onDelete,
  onReply,
}: {
  comment: CommentItem
  onLike: (id: string) => void
  onDelete: (id: string) => void
  onReply: (comment: CommentItem) => void
}) {
  const navigate = useNavigate()
  const { user } = useAuth()
  return (
    <div className="flex gap-3 py-4 border-b border-gray-50 last:border-0">
      <img
        onClick={() => navigate(comment.userId === user?.id ? '/profile' : `/user/${comment.userId}`)}
        src={comment.avatarUrl.startsWith('/files/') ? `/api${comment.avatarUrl}` : comment.avatarUrl}
        alt={comment.username}
        className="w-9 h-9 rounded-full flex-shrink-0 cursor-pointer"
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-1">
          <span className="text-sm font-medium text-gray-900">{comment.username}</span>
          <span className="text-xs text-gray-400">{formatTime(comment.createTime)}</span>
        </div>
        <p className="text-sm text-gray-700 leading-relaxed mb-2">{comment.content}</p>
        <div className="flex items-center gap-4">
          <button
            onClick={() => onLike(comment.id)}
            className={`flex items-center gap-1 text-xs transition-colors ${comment.liked ? 'text-red-500' : 'text-gray-400 hover:text-red-500'}`}
          >
            <ThumbsUp className={`w-3.5 h-3.5 ${comment.liked ? 'fill-current' : ''}`} />
            {comment.likeCount > 0 && comment.likeCount}
          </button>
          <button
            onClick={() => onReply(comment)}
            className="text-xs text-gray-400 hover:text-blue-600"
          >
            回复
          </button>
          {comment.isAuthor && (
            <button
              onClick={() => onDelete(comment.id)}
              className="text-xs text-gray-400 hover:text-red-500"
            >
              删除
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
