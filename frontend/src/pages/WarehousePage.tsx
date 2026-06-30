import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import NavBar from '../components/common/NavBar'
import { postApi, WarehouseStats, WarehouseCategoryStat } from '../services/api'
import { toast } from '../stores/toastStore'
import {
  Package,
  Upload,
  Download,
  Eye,
  ThumbsUp,
  Star,
  FileText,
  MessageSquare,
  Heart,
  File,
  BadgeCheck,
  Crown,
  TrendingUp,
  BarChart3,
  Clock,
  GraduationCap,
  Music,
  Clapperboard,
  Sparkles,
  Gamepad2,
  Briefcase,
  AppWindow,
  UtensilsCrossed,
  Plane,
  Camera,
  BookOpen,
} from 'lucide-react'

interface BackendPost {
  id: string
  schoolId: string
  authorId: string
  authorName?: string
  authorAvatar?: string
  authorRole?: string
  authorLevel?: string
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

const COLOR_MAP: Record<string, { bg: string; text: string; bar: string; soft: string }> = {
  blue:    { bg: 'from-blue-400 to-blue-600',     text: 'text-blue-600',     bar: 'bg-blue-500',     soft: 'bg-blue-50' },
  purple:  { bg: 'from-purple-400 to-purple-600', text: 'text-purple-600',   bar: 'bg-purple-500',   soft: 'bg-purple-50' },
  red:     { bg: 'from-red-400 to-red-600',       text: 'text-red-600',      bar: 'bg-red-500',      soft: 'bg-red-50' },
  pink:    { bg: 'from-pink-400 to-pink-600',     text: 'text-pink-600',     bar: 'bg-pink-500',     soft: 'bg-pink-50' },
  green:   { bg: 'from-green-400 to-green-600',   text: 'text-green-600',    bar: 'bg-green-500',    soft: 'bg-green-50' },
  emerald: { bg: 'from-emerald-400 to-emerald-600', text: 'text-emerald-600', bar: 'bg-emerald-500', soft: 'bg-emerald-50' },
  amber:   { bg: 'from-amber-400 to-amber-600',   text: 'text-amber-600',    bar: 'bg-amber-500',    soft: 'bg-amber-50' },
  cyan:    { bg: 'from-cyan-400 to-cyan-600',     text: 'text-cyan-600',     bar: 'bg-cyan-500',     soft: 'bg-cyan-50' },
  orange:  { bg: 'from-orange-400 to-orange-600', text: 'text-orange-600',   bar: 'bg-orange-500',   soft: 'bg-orange-50' },
  sky:     { bg: 'from-sky-400 to-sky-600',       text: 'text-sky-600',      bar: 'bg-sky-500',      soft: 'bg-sky-50' },
  indigo:  { bg: 'from-indigo-400 to-indigo-600', text: 'text-indigo-600',   bar: 'bg-indigo-500',   soft: 'bg-indigo-50' },
  teal:    { bg: 'from-teal-400 to-teal-600',     text: 'text-teal-600',     bar: 'bg-teal-500',     soft: 'bg-teal-50' },
}

const DEFAULT_COLORS = { bg: 'from-gray-400 to-gray-600', text: 'text-gray-600', bar: 'bg-gray-500', soft: 'bg-gray-50' }

const ICON_MAP: Record<string, React.ElementType> = {
  GraduationCap, Music, Clapperboard, Sparkles, Gamepad2, TrendingUp,
  Briefcase, AppWindow, UtensilsCrossed, Plane, Camera, BookOpen, FileText,
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

const PAGE_SIZE = 20

type TabKey = 'uploads' | 'downloads'

export default function WarehousePage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<WarehouseStats | null>(null)
  const [statsLoading, setStatsLoading] = useState(true)

  const [activeTab, setActiveTab] = useState<TabKey>('uploads')
  const [posts, setPosts] = useState<BackendPost[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const loadingRef = useRef(false)
  const observerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await postApi.getWarehouseStats()
        setStats(res.data || null)
      } catch {
        /* ignore */
      } finally {
        setStatsLoading(false)
      }
    }
    fetchStats()
  }, [])

  const loadPage = useCallback(async (pageNum: number, isLoadMore = false) => {
    if (loadingRef.current) return
    loadingRef.current = true

    if (isLoadMore) {
      setLoadingMore(true)
    } else {
      setListLoading(true)
    }

    try {
      const fetcher = activeTab === 'uploads' ? postApi.getMyPosts : postApi.getMyDownloads
      const res = await fetcher(pageNum, PAGE_SIZE)
      const pageData: PageResponse<BackendPost> = res.data || { records: [], total: 0, size: PAGE_SIZE, current: 1, pages: 0 }
      if (isLoadMore) {
        setPosts(prev => [...prev, ...pageData.records])
      } else {
        setPosts(pageData.records)
      }
      setTotal(pageData.total)
      setPage(pageNum)
      setHasMore(pageNum * PAGE_SIZE < pageData.total)
    } catch {
      toast.error('加载失败')
    } finally {
      setListLoading(false)
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [activeTab])

  useEffect(() => {
    setPosts([])
    setTotal(0)
    setPage(1)
    setHasMore(false)
    loadPage(1, false)
  }, [loadPage])

  useEffect(() => {
    if (!hasMore || listLoading || loadingMore) return
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
  }, [hasMore, page, loadPage, listLoading, loadingMore, posts.length])

  const statsCards = [
    { key: 'uploads', label: '我的上传', value: stats?.uploadCount || 0, icon: Upload, color: 'bg-blue-50 text-blue-600' },
    { key: 'downloads', label: '我的下载', value: stats?.downloadCount || 0, icon: Download, color: 'bg-green-50 text-green-600' },
    { key: 'dl-mine', label: '我的资料被下载', value: stats?.totalDownloadsOfMyPosts || 0, icon: Package, color: 'bg-purple-50 text-purple-600' },
    { key: 'views', label: '总浏览', value: stats?.totalViews || 0, icon: Eye, color: 'bg-cyan-50 text-cyan-600' },
    { key: 'likes', label: '总获赞', value: stats?.totalLikes || 0, icon: ThumbsUp, color: 'bg-red-50 text-red-600' },
    { key: 'stars', label: '总被收藏', value: stats?.totalStars || 0, icon: Star, color: 'bg-amber-50 text-amber-600' },
  ]

  const categoryStats = stats?.categoryStats || []
  const maxCategoryTotal = categoryStats.reduce((max, c) => Math.max(max, c.uploadCount + c.downloadCount), 0) || 1

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 页面标题 */}
      <div className="bg-white border-b border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-4">
          <div className="flex items-center gap-2">
            <Package className="w-5 h-5 text-blue-600" />
            <h1 className="text-lg font-bold text-gray-900">我的仓库</h1>
          </div>
          <p className="text-xs text-gray-400 mt-1">管理上传与下载资料，掌握资源数据动态</p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4 space-y-3">
        {/* 数据统计概览 */}
        <div className="bg-white rounded-2xl border border-gray-100 p-4">
          <div className="flex items-center gap-2 mb-3">
            <BarChart3 className="w-4 h-4 text-gray-700" />
            <h2 className="text-sm font-semibold text-gray-900">数据概览</h2>
          </div>
          {statsLoading ? (
            <div className="grid grid-cols-3 gap-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="h-20 bg-gray-50 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-3">
              {statsCards.map((card) => {
                const Icon = card.icon
                return (
                  <div key={card.key} className="bg-gray-50 rounded-xl p-3 flex flex-col items-center justify-center text-center">
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center mb-1.5 ${card.color}`}>
                      <Icon className="w-4 h-4" />
                    </div>
                    <p className="text-base font-bold text-gray-900 leading-none">{formatNumber(card.value)}</p>
                    <p className="text-[11px] text-gray-500 mt-1">{card.label}</p>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* 分类统计图表 */}
        {!statsLoading && categoryStats.length > 0 && (
          <div className="bg-white rounded-2xl border border-gray-100 p-4">
            <div className="flex items-center gap-2 mb-3">
              <TrendingUp className="w-4 h-4 text-gray-700" />
              <h2 className="text-sm font-semibold text-gray-900">分类分布</h2>
              <span className="text-xs text-gray-400">（上传 / 下载）</span>
            </div>
            <div className="space-y-3">
              {categoryStats.map((cat: WarehouseCategoryStat) => {
                const colors = COLOR_MAP[cat.color] || DEFAULT_COLORS
                const IconComp = ICON_MAP[cat.icon] || FileText
                const total = cat.uploadCount + cat.downloadCount
                const uploadPct = (cat.uploadCount / maxCategoryTotal) * 100
                const downloadPct = (cat.downloadCount / maxCategoryTotal) * 100
                return (
                  <div key={cat.categoryId} className="flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${colors.bg} flex items-center justify-center flex-shrink-0`}>
                      <IconComp className="w-4 h-4 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs font-medium text-gray-700 truncate">{cat.categoryName}</span>
                        <span className="text-[11px] text-gray-400 flex-shrink-0 ml-2">{total} 份</span>
                      </div>
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] text-gray-400 w-8 flex-shrink-0">上传</span>
                          <div className="flex-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                            <div
                              className={`h-full ${colors.bar} rounded-full transition-all duration-500`}
                              style={{ width: `${Math.max(uploadPct, cat.uploadCount > 0 ? 4 : 0)}%` }}
                            />
                          </div>
                          <span className="text-[10px] text-gray-600 w-6 text-right flex-shrink-0">{cat.uploadCount}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] text-gray-400 w-8 flex-shrink-0">下载</span>
                          <div className="flex-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-gray-400 rounded-full transition-all duration-500"
                              style={{ width: `${Math.max(downloadPct, cat.downloadCount > 0 ? 4 : 0)}%` }}
                            />
                          </div>
                          <span className="text-[10px] text-gray-600 w-6 text-right flex-shrink-0">{cat.downloadCount}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* 上传/下载列表切换 */}
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="border-b border-gray-100 flex">
            <button
              onClick={() => setActiveTab('uploads')}
              className={`flex-1 px-4 py-3 text-sm font-medium transition-colors flex items-center justify-center gap-1.5 ${
                activeTab === 'uploads'
                  ? 'text-blue-600 border-b-2 border-blue-600'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              <Upload className="w-4 h-4" />
              我的上传
              {stats && stats.uploadCount > 0 && (
                <span className={`text-[11px] ${activeTab === 'uploads' ? 'text-blue-500' : 'text-gray-400'}`}>
                  ({stats.uploadCount})
                </span>
              )}
            </button>
            <button
              onClick={() => setActiveTab('downloads')}
              className={`flex-1 px-4 py-3 text-sm font-medium transition-colors flex items-center justify-center gap-1.5 ${
                activeTab === 'downloads'
                  ? 'text-blue-600 border-b-2 border-blue-600'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              <Download className="w-4 h-4" />
              我的下载
              {stats && stats.downloadCount > 0 && (
                <span className={`text-[11px] ${activeTab === 'downloads' ? 'text-blue-500' : 'text-gray-400'}`}>
                  ({stats.downloadCount})
                </span>
              )}
            </button>
          </div>

          <div className="p-3">
            {listLoading ? (
              <div className="text-center py-12">
                <div className="w-7 h-7 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                <p className="text-gray-400 text-xs">加载中...</p>
              </div>
            ) : posts.length > 0 ? (
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
                      className="bg-white rounded-xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all duration-200 p-3 cursor-pointer"
                    >
                      <div className="flex items-center gap-2 mb-2">
                        <img
                          src={avatarUrl}
                          alt={post.authorName || post.authorId}
                          className="w-7 h-7 rounded-full flex-shrink-0 object-cover"
                        />
                        <span className="text-xs text-gray-700 font-medium flex items-center gap-1">
                          {post.authorName || post.authorId.slice(0, 8)}
                          {post.authorLevel && post.authorLevel !== 'NONE' && (
                            <CreatorLevelIcon level={post.authorLevel} />
                          )}
                        </span>
                        <span className="text-[11px] text-gray-400 ml-1 flex items-center gap-0.5">
                          <Clock className="w-3 h-3" />
                          {formatTime(post.createTime)}
                        </span>
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
                        <h3 className="text-sm font-semibold text-gray-900 leading-snug" style={{display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'}}>
                          {post.title}
                        </h3>
                        {post.content && post.content.trim() && (
                          <p className="text-xs text-gray-600 mt-1" style={{display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'}}>
                            {post.content.replace(/<[^>]*>/g, '')}
                          </p>
                        )}

                        {isImage && post.fileUrl && (
                          <img
                            src={getFileUrl(post.fileUrl)}
                            alt={post.fileName || '附件'}
                            className="w-14 h-14 object-cover rounded mt-2"
                          />
                        )}

                        {hasFile && (
                          <div className="flex items-center gap-2 bg-gray-50 rounded-lg p-2 mt-2">
                            <File className="w-3.5 h-3.5 text-gray-500 flex-shrink-0" />
                            <span className="text-xs text-gray-700 truncate flex-1">{post.fileName || '附件'}</span>
                            {formatFileSize(post.fileSize) && (
                              <span className="text-[11px] text-gray-400 flex-shrink-0">{formatFileSize(post.fileSize)}</span>
                            )}
                          </div>
                        )}
                      </div>

                      <div className="flex items-center gap-3 mt-2 text-[11px] text-gray-400">
                        <span className="flex items-center gap-0.5">
                          <Eye className="w-3 h-3" />
                          {formatNumber(post.viewCount)}
                        </span>
                        <span className="flex items-center gap-0.5">
                          <MessageSquare className="w-3 h-3" />
                          {formatNumber(post.commentCount)}
                        </span>
                        <span className="flex items-center gap-0.5">
                          <Star className="w-3 h-3" />
                          {formatNumber(post.starCount)}
                        </span>
                        <span className="flex items-center gap-0.5">
                          <Heart className="w-3 h-3" />
                          {formatNumber(post.likeCount)}
                        </span>
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <div className="text-center py-12">
                <div className="w-14 h-14 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-3">
                  {activeTab === 'uploads' ? <Upload className="w-6 h-6 text-gray-300" /> : <Download className="w-6 h-6 text-gray-300" />}
                </div>
                <p className="text-gray-400 text-sm">
                  {activeTab === 'uploads' ? '暂无上传记录' : '暂无下载记录'}
                </p>
                <p className="text-gray-400 text-xs mt-1">
                  {activeTab === 'uploads' ? '开始上传你的第一份资料吧' : '去发现有用的资料下载吧'}
                </p>
              </div>
            )}

            {hasMore && (
              <div ref={observerRef} className="py-4 text-center">
                {loadingMore ? (
                  <div className="w-5 h-5 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
                ) : (
                  <button
                    onClick={() => loadPage(page + 1, true)}
                    className="px-4 py-1.5 text-xs text-blue-600 hover:bg-blue-50 rounded-full transition-colors"
                  >
                    加载更多
                  </button>
                )}
              </div>
            )}

            {!hasMore && posts.length > 0 && (
              <div className="py-4 text-center text-[11px] text-gray-400">
                共 {total} 条记录
              </div>
            )}
          </div>
        </div>
      </div>

      <NavBar />
    </div>
  )
}
