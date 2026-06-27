import { useState, useMemo, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Search,
  Flame,
  Clock,
  Star,
  FileText,
  MessageSquare,
  Eye,
  ChevronLeft,
  Plus,
  X,
  Upload,
  File,
} from 'lucide-react'
import NavBar from '../components/common/NavBar'
import schoolsData from '../data/schools.json'
import { PostType } from '../data/posts'
import { fileApi, postApi } from '../services/api'
import { toast } from '../stores/toastStore'

type SortType = 'latest' | 'hottest' | 'active'

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

interface PostView {
  id: string
  schoolId: string
  type: PostType
  title: string
  author: {
    id: string
    username: string
    avatar?: string
  }
  createdAt: string
  stars: number
  comments: number
  views: number
  isStarred: boolean
  fileUrl?: string
  fileType?: string
  fileSize?: string
  description?: string
  content?: string
}

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

function timeAgo(dateStr: string): string {
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

function formatNumber(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

interface PostCardProps {
  post: PostView
  schoolId: string
  onStar: (postId: string) => void
}

function PostCard({ post, schoolId, onStar }: PostCardProps) {
  const navigate = useNavigate()

  const handleClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement
    if (target.closest('button')) return
    navigate(`/school/${schoolId}/post/${post.id}`)
  }

  return (
    <div onClick={handleClick} className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-4 cursor-pointer">
      <div className="flex items-start gap-3">
        {/* 头像 */}
        <img
          src={post.author.avatar}
          alt={post.author.username}
          className="w-10 h-10 rounded-full flex-shrink-0"
        />

        <div className="flex-1 min-w-0">
          {/* 标签行 */}
          <div className="flex items-center gap-2 mb-2">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${typeColors[post.type]}`}>
              {typeLabels[post.type]}
            </span>
            <span className="text-xs text-gray-400">{timeAgo(post.createdAt)}</span>
          </div>

          {/* 标题 */}
          <h3 className="text-sm font-medium text-gray-900 mb-1 leading-snug line-clamp-2 hover:text-blue-600 cursor-pointer">
            {post.title}
          </h3>

          {/* 描述（资源贴） */}
          {post.type === 'resource' && post.description && (
            <p className="text-xs text-gray-500 mb-2 line-clamp-2">{post.description}</p>
          )}

          {/* 讨论帖内容摘要 */}
          {post.type === 'discussion' && post.content && (
            <p className="text-xs text-gray-500 mb-2 line-clamp-2">{post.content}</p>
          )}

          {/* 文件信息（资源贴） */}
          {post.type === 'resource' && post.fileType && (
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded font-mono uppercase">
                {post.fileType}
              </span>
              {post.fileSize && (
                <span className="text-xs text-gray-400">{post.fileSize}</span>
              )}
            </div>
          )}

          {/* 作者信息 */}
          <div className="flex items-center justify-between mt-2">
            <span className="text-xs text-gray-500">{post.author.username}</span>

            <div className="flex items-center gap-3 text-gray-400">
              <span className="flex items-center gap-1 text-xs">
                <Eye className="w-3.5 h-3.5" />
                {formatNumber(post.views)}
              </span>
              <span className="flex items-center gap-1 text-xs">
                <MessageSquare className="w-3.5 h-3.5" />
                {post.comments}
              </span>
              <button
                onClick={() => onStar(post.id)}
                className={`flex items-center gap-1 text-xs transition-colors ${
                  post.isStarred ? 'text-orange-500' : 'hover:text-orange-500'
                }`}
              >
                <Star className={`w-3.5 h-3.5 ${post.isStarred ? 'fill-current' : ''}`} />
                {formatNumber(post.stars)}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function SchoolDetailPage() {
  const { schoolId } = useParams<{ schoolId: string }>()
  const navigate = useNavigate()

  const school = schoolsData.find((s) => s.id === schoolId)

  const [posts, setPosts] = useState<PostView[]>([])
  const [loading, setLoading] = useState(true)
  const [realResourceCount, setRealResourceCount] = useState<number | null>(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [sortType, setSortType] = useState<SortType>('latest')
  const [filterType, setFilterType] = useState<PostType | 'all'>('all')
  const [starredPosts, setStarredPosts] = useState<Set<string>>(new Set())

  const fetchPosts = useCallback(async () => {
    if (!schoolId) return
    setLoading(true)
    try {
      const response = await postApi.getBySchool(schoolId, {
        postType: filterType === 'all' ? undefined : filterType,
        sortType,
        page: 1,
        size: 50,
      })
      // response is ApiResponse, actual post list is in response.data
      const postList: BackendPost[] = response.data || []
      const viewPosts: PostView[] = postList.map((p) => ({
        id: p.id,
        schoolId: p.schoolId,
        type: (p.postType as PostType) || 'discussion',
        title: p.title,
        author: {
          id: p.authorId,
          username: p.authorName || p.authorId.slice(0, 8),
          avatar: p.authorAvatar
            ? (p.authorAvatar.startsWith('/files/') ? `/api${p.authorAvatar}` : p.authorAvatar)
            : `https://api.dicebear.com/7.x/avataaars/svg?seed=${p.authorId}`,
        },
        createdAt: p.createTime,
        stars: p.starCount || 0,
        comments: p.commentCount || 0,
        views: p.viewCount || 0,
        isStarred: false,
        fileUrl: p.fileUrl,
        fileType: p.fileType,
        fileSize: p.fileSize ? (p.fileSize / 1024 / 1024).toFixed(2) + ' MB' : undefined,
        description: p.content,
        content: p.content,
      }))
      setPosts(viewPosts)

      // Fetch star status for each post (if logged in)
      const token = localStorage.getItem('campusshare_token')
      if (token && postList.length > 0) {
        try {
          const starredSet = new Set<string>()
          await Promise.all(
            postList.map(async (p) => {
              try {
                const statusRes = await postApi.getStatus(p.id)
                if (statusRes.data.starred) {
                  starredSet.add(p.id)
                }
              } catch {
                // ignore individual errors
              }
            })
          )
          setStarredPosts(starredSet)
        } catch {
          // ignore
        }
      }
    } catch (err) {
      toast.error('加载帖子列表失败')
    } finally {
      setLoading(false)
    }
  }, [schoolId, filterType, sortType])

  useEffect(() => {
    fetchPosts()
  }, [fetchPosts])

  // Fetch real post count for this school
  useEffect(() => {
    const fetchCount = async () => {
      if (!schoolId) return
      try {
        const res = await postApi.getSchoolPostCounts()
        setRealResourceCount(res.data?.[schoolId] ?? 0)
      } catch {
        // keep null on error
      }
    }
    fetchCount()
  }, [schoolId])

  // 发布弹窗
  const [showPostModal, setShowPostModal] = useState(false)
  const [postType, setPostType] = useState<'resource' | 'discussion'>('resource')
  const [postTitle, setPostTitle] = useState('')
  const [postContent, setPostContent] = useState('')
  const [uploadedFile, setUploadedFile] = useState<{
    url: string
    name: string
    type: string
    size: number
  } | null>(null)
  const [uploading, setUploading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    try {
      const data = await fileApi.upload(file)
      setUploadedFile({
        url: data.url,
        name: data.fileName,
        type: data.fileType,
        size: data.fileSize,
      })
      toast.success('上传成功')
    } catch (err) {
      toast.error((err as Error).message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const handleRemoveFile = () => {
    setUploadedFile(null)
  }

  const handleSubmitPost = async () => {
    if (!postTitle.trim()) {
      toast.warning('请输入标题')
      return
    }
    if (postType === 'resource' && !uploadedFile) {
      toast.warning('请上传资源文件')
      return
    }

    setSubmitting(true)
    try {
      await postApi.create({
        schoolId: schoolId || '',
        postType,
        title: postTitle,
        content: postContent,
        fileUrl: uploadedFile?.url,
        fileName: uploadedFile?.name,
        fileType: uploadedFile?.type,
        fileSize: uploadedFile?.size,
      })

      toast.success('发布成功！')
      setShowPostModal(false)
      setPostTitle('')
      setPostContent('')
      setUploadedFile(null)
      fetchPosts()
    } catch (err) {
      toast.error((err as Error).message || '发布失败')
    } finally {
      setSubmitting(false)
    }
  }

  const filteredPosts = useMemo(() => {
    let result = [...posts]

    // 搜索过滤
    if (searchKeyword) {
      result = result.filter(
        (p) =>
          p.title.toLowerCase().includes(searchKeyword.toLowerCase()) ||
          p.author.username.toLowerCase().includes(searchKeyword.toLowerCase()) ||
          (p.content && p.content.toLowerCase().includes(searchKeyword.toLowerCase()))
      )
    }

    return result
  }, [posts, searchKeyword])

  const handleStar = async (postId: string) => {
    // Optimistic UI update
    const wasStarred = starredPosts.has(postId)
    setStarredPosts((prev) => {
      const next = new Set(prev)
      if (next.has(postId)) {
        next.delete(postId)
      } else {
        next.add(postId)
      }
      return next
    })
    // Update star count optimistically
    setPosts((prev) =>
      prev.map((p) =>
        p.id === postId
          ? { ...p, stars: wasStarred ? p.stars - 1 : p.stars + 1 }
          : p
      )
    )
    try {
      await postApi.toggleStar(postId)
    } catch (err) {
      // Rollback on failure
      setStarredPosts((prev) => {
        const next = new Set(prev)
        if (wasStarred) {
          next.add(postId)
        } else {
          next.delete(postId)
        }
        return next
      })
      setPosts((prev) =>
        prev.map((p) =>
          p.id === postId
            ? { ...p, stars: wasStarred ? p.stars + 1 : p.stars - 1 }
            : p
        )
      )
      toast.error((err as Error).message || '操作失败')
    }
  }

  if (!school) {
    return (
      <div className="min-h-screen bg-gray-50 pb-16 flex items-center justify-center">
        <div className="text-center">
          <p className="text-gray-500 mb-4">学校不存在</p>
          <button onClick={() => navigate('/home')} className="text-blue-600 hover:underline">
            返回首页
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 顶部 */}
      <div className="bg-white border-b border-gray-100">
        {/* 学校标题栏 */}
        <div className="max-w-5xl mx-auto px-4 pt-3 pb-2">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/home')}
              className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
            >
              <ChevronLeft className="w-5 h-5 text-gray-600" />
            </button>
            <img src={school.logo} alt={school.name} className="w-8 h-8" />
            <div>
              <h1 className="text-base font-semibold text-gray-900">{school.name}</h1>
              <p className="text-xs text-gray-400">{realResourceCount !== null ? realResourceCount : school.resourceCount} 份资料</p>
            </div>
          </div>
        </div>

        {/* 搜索栏 */}
        <div className="max-w-5xl mx-auto px-4 pb-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="搜索资料、帖子..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              className="w-full pl-9 pr-4 py-2.5 bg-gray-100 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
          </div>
        </div>

        {/* 筛选栏 */}
        <div className="max-w-5xl mx-auto px-4 pb-3 flex items-center justify-between">
          <div className="flex items-center gap-1">
            {(
              [
                { key: 'all', label: '全部' },
                { key: 'resource', label: '资料' },
                { key: 'discussion', label: '讨论' },
              ] as const
            ).map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setFilterType(key)}
                className={`px-3 py-1.5 text-sm rounded-full transition-colors ${
                  filterType === key
                    ? 'bg-blue-100 text-blue-700 font-medium'
                    : 'text-gray-500 hover:bg-gray-100'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setSortType('latest')}
              className={`px-3 py-1.5 text-xs rounded-full transition-colors flex items-center gap-1 ${
                sortType === 'latest'
                  ? 'bg-gray-900 text-white'
                  : 'text-gray-500 hover:bg-gray-100'
              }`}
            >
              <Clock className="w-3 h-3" />
              最新
            </button>
            <button
              onClick={() => setSortType('hottest')}
              className={`px-3 py-1.5 text-xs rounded-full transition-colors flex items-center gap-1 ${
                sortType === 'hottest'
                  ? 'bg-gray-900 text-white'
                  : 'text-gray-500 hover:bg-gray-100'
              }`}
            >
              <Flame className="w-3 h-3" />
              最热
            </button>
            <button
              onClick={() => setSortType('active')}
              className={`px-3 py-1.5 text-xs rounded-full transition-colors flex items-center gap-1 ${
                sortType === 'active'
                  ? 'bg-gray-900 text-white'
                  : 'text-gray-500 hover:bg-gray-100'
              }`}
            >
              <MessageSquare className="w-3 h-3" />
              活跃
            </button>
          </div>
        </div>
      </div>

      {/* 帖子列表 */}
      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-gray-400 text-sm">加载中...</p>
          </div>
        ) : filteredPosts.length > 0 ? (
          <div className="space-y-3">
            {filteredPosts.map((post) => (
              <PostCard
                key={post.id}
                post={{ ...post, isStarred: starredPosts.has(post.id) }}
                schoolId={schoolId || ''}
                onStar={handleStar}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <FileText className="w-12 h-12 text-gray-200 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">暂无相关帖子</p>
            <p className="text-gray-300 text-xs mt-1">试试其他关键词或筛选条件</p>
          </div>
        )}
      </div>

      {/* 底部导航栏 */}
      <NavBar />

      {/* 发布按钮 */}
      <button
        onClick={() => setShowPostModal(true)}
        className="fixed right-4 bottom-20 w-12 h-12 bg-blue-600 text-white rounded-full shadow-lg hover:bg-blue-700 hover:scale-105 transition-all flex items-center justify-center z-10"
      >
        <Plus className="w-6 h-6" />
      </button>

      {/* 发布弹窗 */}
      {showPostModal && (
        <div
          className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center"
          onClick={() => setShowPostModal(false)}
        >
          <div
            className="bg-white w-full sm:max-w-xl sm:rounded-2xl rounded-t-3xl max-h-[85vh] overflow-hidden flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            {/* 弹窗头部 */}
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="text-lg font-bold text-gray-900">发布</h2>
              <button
                onClick={() => setShowPostModal(false)}
                className="p-1 -mr-1 hover:bg-gray-100 rounded-full transition-colors"
              >
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>

            {/* 类型切换 */}
            <div className="px-5 pt-4 pb-2">
              <div className="flex gap-2">
                <button
                  onClick={() => setPostType('resource')}
                  className={`flex-1 py-2.5 text-sm font-medium rounded-xl transition-colors ${
                    postType === 'resource'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  发布资料
                </button>
                <button
                  onClick={() => setPostType('discussion')}
                  className={`flex-1 py-2.5 text-sm font-medium rounded-xl transition-colors ${
                    postType === 'discussion'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  发讨论帖
                </button>
              </div>
            </div>

            {/* 表单内容 */}
            <div className="px-5 py-4 space-y-4 overflow-y-auto flex-1">
              {/* 标题 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {postType === 'resource' ? '资源标题' : '帖子标题'}
                </label>
                <input
                  type="text"
                  value={postTitle}
                  onChange={(e) => setPostTitle(e.target.value)}
                  placeholder={
                    postType === 'resource'
                      ? '请输入资源名称，例如：高等数学期末复习资料'
                      : '请输入帖子标题，吸引大家来讨论'
                  }
                  maxLength={100}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
                />
              </div>

              {/* 内容描述 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {postType === 'resource' ? '资源描述' : '内容'}
                </label>
                <textarea
                  value={postContent}
                  onChange={(e) => setPostContent(e.target.value)}
                  placeholder={
                    postType === 'resource'
                      ? '简单介绍一下这份资源的内容、适用人群、参考价值等...'
                      : '分享你的想法、问题或者经验，和大家一起讨论...'
                  }
                  rows={5}
                  maxLength={2000}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all resize-none"
                />
                <p className="text-xs text-gray-400 mt-1 text-right">
                  {postContent.length}/2000
                </p>
              </div>

              {/* 文件上传 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {postType === 'resource' ? '上传文件' : '附件（可选）'}
                </label>
                {uploadedFile ? (
                  <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-xl">
                    <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center flex-shrink-0">
                      <File className="w-5 h-5 text-blue-600" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-gray-900 font-medium truncate">
                        {uploadedFile.name}
                      </p>
                      <p className="text-xs text-gray-400">
                        {uploadedFile.type.toUpperCase()} · {(uploadedFile.size / 1024 / 1024).toFixed(2)} MB
                      </p>
                    </div>
                    <button
                      onClick={handleRemoveFile}
                      className="p-1 hover:bg-gray-200 rounded-full transition-colors"
                    >
                      <X className="w-4 h-4 text-gray-400" />
                    </button>
                  </div>
                ) : (
                  <label className="block w-full p-6 border-2 border-dashed border-gray-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50 transition-colors text-center">
                    <Upload className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                    <p className="text-sm text-gray-500">
                      {uploading ? '上传中...' : '点击上传文件'}
                    </p>
                    <p className="text-xs text-gray-400 mt-1">
                      支持 PDF、Word、Excel、PPT、ZIP、图片等格式，最大 50MB
                    </p>
                    <input
                      type="file"
                      className="hidden"
                      onChange={handleFileUpload}
                      disabled={uploading}
                      multiple
                    />
                  </label>
                )}
              </div>
            </div>

            {/* 底部按钮 */}
            <div className="px-5 py-4 border-t border-gray-100">
              <button
                onClick={handleSubmitPost}
                disabled={submitting || !postTitle.trim()}
                className="w-full py-3 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {submitting ? '发布中...' : '发布'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
