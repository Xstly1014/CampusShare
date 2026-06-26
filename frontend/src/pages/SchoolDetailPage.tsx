import { useState, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Search, Flame, Clock, Star, FileText, MessageSquare, Eye, ChevronLeft } from 'lucide-react'
import NavBar from '../components/common/NavBar'
import schoolsData from '../data/schools.json'
import { postsData, Post, PostType } from '../data/posts'

type SortType = 'latest' | 'hottest' | 'active'

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
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return `${Math.floor(days / 30)}个月前`
}

function formatNumber(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

interface PostCardProps {
  post: Post
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
  const schoolPosts = postsData.filter((p) => p.schoolId === schoolId)

  const [searchKeyword, setSearchKeyword] = useState('')
  const [sortType, setSortType] = useState<SortType>('latest')
  const [filterType, setFilterType] = useState<PostType | 'all'>('all')
  const [starredPosts, setStarredPosts] = useState<Set<string>>(
    new Set(schoolPosts.filter((p) => p.isStarred).map((p) => p.id))
  )

  const filteredPosts = useMemo(() => {
    let posts = [...schoolPosts]

    // 搜索过滤
    if (searchKeyword) {
      posts = posts.filter(
        (p) =>
          p.title.toLowerCase().includes(searchKeyword.toLowerCase()) ||
          p.author.username.toLowerCase().includes(searchKeyword.toLowerCase()) ||
          (p.description && p.description.toLowerCase().includes(searchKeyword.toLowerCase()))
      )
    }

    // 类型过滤
    if (filterType !== 'all') {
      posts = posts.filter((p) => p.type === filterType)
    }

    // 排序
    if (sortType === 'latest') {
      posts.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    } else if (sortType === 'hottest') {
      posts.sort((a, b) => b.stars - a.stars)
    } else if (sortType === 'active') {
      posts.sort((a, b) => b.comments - a.comments)
    }

    return posts
  }, [schoolPosts, searchKeyword, sortType, filterType])

  const handleStar = (postId: string) => {
    setStarredPosts((prev) => {
      const next = new Set(prev)
      if (next.has(postId)) {
        next.delete(postId)
      } else {
        next.add(postId)
      }
      return next
    })
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
              <p className="text-xs text-gray-400">{school.resourceCount} 份资料</p>
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
        {filteredPosts.length > 0 ? (
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
    </div>
  )
}
