import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ChevronLeft, Plus, Flame, Clock, MessageSquare, FileText,
  GraduationCap, Music, Clapperboard, Sparkles, Gamepad2, TrendingUp,
  Briefcase, AppWindow, UtensilsCrossed, Plane, Camera, BookOpen,
  X, Upload, File,
  Mic2, Globe, Languages, Star, Waves, Guitar, Volume2, Mic, Headphones,
  Zap, Smile, Rocket, Heart, Skull, Film, Palette, Tv, BookCheck,
  Sparkle, Users, Coffee, Sprout, Key, Cog, Trophy, Wand2,
  Monitor, Smartphone, Gamepad as GamepadIcon, Puzzle, Lightbulb, Wrench,
  Landmark, Building2, DollarSign, PieChart, Coins, LineChart,
  Globe2, Wallet, Building, Scale, Bookmark,
  MonitorPlay, Command, Tablet, Blocks,
  ChefHat, MapPin, Cake, Wine, Dumbbell,
  Map, Compass, BedDouble,
  User, Mountain, Focus, SlidersHorizontal,
  BookCopy, Code2, Scroll, Target,
  Hash, Search, BadgeCheck, Crown, Eye
} from 'lucide-react'
import { categoryApi, postApi, fileApi, Category, SubCategory } from '../services/api'
import SchoolCard from '../components/home/SchoolCard'
import schoolsData from '../data/schools.json'
import { toast } from '../stores/toastStore'

const ICON_MAP: Record<string, React.ElementType> = {
  GraduationCap, Music, Clapperboard, Sparkles, Gamepad2, TrendingUp,
  Briefcase, AppWindow, UtensilsCrossed, Plane, Camera, BookOpen,
}

const SUB_ICON_MAP: Record<string, React.ElementType> = {
  Mic2, Globe, Languages, Star, Waves, Guitar, Volume2, Mic, Headphones,
  Zap, Smile, Rocket, Heart, Skull, Film, Palette, Tv, BookCheck,
  Flame, Hearts: Heart, Sparkle, Users, Coffee, Sprout, Key, Cog, Trophy, Wand2,
  Monitor, Smartphone, Gamepad: GamepadIcon, Puzzle, Lightbulb, Wrench,
  Landmark, Building2, DollarSign, PieChart, Coins, LineChart,
  Globe2, Wallet, Building, Scale, Bookmark,
  MonitorPlay, Command, Tablet, Blocks,
  ChefHat, MapPin, Cake, Wine, Dumbbell,
  Map, Compass, BedDouble,
  User, Mountain, Focus, Aperture: Focus, SlidersHorizontal,
  BookCopy, BookText: BookCopy, Code2, Scroll, ScrollText: Scroll, Target,
  Piano: Music, PlaneTakeoff: Plane, Laugh: Smile,
  TabletSmartphone: Tablet, BriefcaseBusiness: Briefcase, Banknote: Wallet,
  CakeSlice: Cake, GlassWater: Wine, Bed: BedDouble, BookOpenCheck: BookCheck,
}

const COLOR_MAP: Record<string, { bg: string; light: string; text: string }> = {
  blue:    { bg: 'from-blue-400 to-blue-600',     light: 'bg-blue-50',    text: 'text-blue-600' },
  purple:  { bg: 'from-purple-400 to-purple-600', light: 'bg-purple-50',  text: 'text-purple-600' },
  red:     { bg: 'from-red-400 to-red-600',       light: 'bg-red-50',     text: 'text-red-600' },
  pink:    { bg: 'from-pink-400 to-pink-600',     light: 'bg-pink-50',    text: 'text-pink-600' },
  green:   { bg: 'from-green-400 to-green-600',   light: 'bg-green-50',   text: 'text-green-600' },
  emerald: { bg: 'from-emerald-400 to-emerald-600', light: 'bg-emerald-50', text: 'text-emerald-600' },
  amber:   { bg: 'from-amber-400 to-amber-600',   light: 'bg-amber-50',   text: 'text-amber-600' },
  cyan:    { bg: 'from-cyan-400 to-cyan-600',     light: 'bg-cyan-50',    text: 'text-cyan-600' },
  orange:  { bg: 'from-orange-400 to-orange-600', light: 'bg-orange-50',  text: 'text-orange-600' },
  sky:     { bg: 'from-sky-400 to-sky-600',       light: 'bg-sky-50',     text: 'text-sky-600' },
  indigo:  { bg: 'from-indigo-400 to-indigo-600', light: 'bg-indigo-50',  text: 'text-indigo-600' },
  teal:    { bg: 'from-teal-400 to-teal-600',     light: 'bg-teal-50',    text: 'text-teal-600' },
}

const DEFAULT_COLORS = { bg: 'from-blue-400 to-blue-600', light: 'bg-blue-50', text: 'text-blue-600' }

const SUB_COLORS = [
  { bg: 'bg-rose-50', text: 'text-rose-600' },
  { bg: 'bg-orange-50', text: 'text-orange-600' },
  { bg: 'bg-amber-50', text: 'text-amber-600' },
  { bg: 'bg-emerald-50', text: 'text-emerald-600' },
  { bg: 'bg-teal-50', text: 'text-teal-600' },
  { bg: 'bg-sky-50', text: 'text-sky-600' },
  { bg: 'bg-indigo-50', text: 'text-indigo-600' },
  { bg: 'bg-violet-50', text: 'text-violet-600' },
  { bg: 'bg-pink-50', text: 'text-pink-600' },
  { bg: 'bg-cyan-50', text: 'text-cyan-600' },
  { bg: 'bg-lime-50', text: 'text-lime-600' },
  { bg: 'bg-fuchsia-50', text: 'text-fuchsia-600' },
]

function hashStr(str: string): number {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = ((h << 5) - h) + str.charCodeAt(i)
    h |= 0
  }
  return Math.abs(h)
}

function getSubColor(id: string) {
  return SUB_COLORS[hashStr(id) % SUB_COLORS.length]
}

const CATEGORY_DESC_MAP: Record<string, { unit: string; desc: string }> = {
  'cat-campus':    { unit: '所高校', desc: '浏览优质学习资源' },
  'cat-music':     { unit: '种曲风', desc: '发现好音乐' },
  'cat-movie':     { unit: '种题材', desc: '精彩影片看不停' },
  'cat-anime':     { unit: '种类型', desc: '畅游二次元世界' },
  'cat-game':      { unit: '个平台', desc: '畅玩精彩游戏' },
  'cat-stock':     { unit: '个板块', desc: '掌握财经动态' },
  'cat-interview': { unit: '个方向', desc: '拿下理想offer' },
  'cat-software':  { unit: '种分类', desc: '实用软件任你选' },
  'cat-food':      { unit: '种菜系', desc: '发现美味食谱' },
  'cat-travel':    { unit: '个目的地', desc: '探索精彩世界' },
  'cat-photo':     { unit: '个技巧', desc: '提升摄影水平' },
  'cat-book':      { unit: '类书籍', desc: '享受阅读时光' },
}

const DEFAULT_CAT_DESC = { unit: '个板块', desc: '浏览精选内容' }

type SortType = 'latest' | 'hottest' | 'active'
type ViewMode = 'subs' | 'posts'

interface PostItem {
  id: string
  schoolId?: string
  categoryId?: string
  subCategoryId?: string
  categoryName?: string
  subCategoryName?: string
  authorId: string
  authorName: string
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

interface School {
  id: string
  name: string
  logo: string
  resourceCount: number
}

export default function CategoryDetailPage() {
  const { categoryId } = useParams<{ categoryId: string }>()
  const navigate = useNavigate()
  const [category, setCategory] = useState<Category | null>(null)
  const [schoolCounts, setSchoolCounts] = useState<Record<string, number>>({})
  const [catCounts, setCatCounts] = useState<Record<string, number>>({})
  const [schools, setSchools] = useState<School[]>(schoolsData as School[])

  const [viewMode, setViewMode] = useState<ViewMode>('subs')
  const [activeSub, setActiveSub] = useState<SubCategory | null>(null)
  const [posts, setPosts] = useState<PostItem[]>([])
  const [sortType, setSortType] = useState<SortType>('latest')
  const [postType, setPostType] = useState('all')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [hasMore, setHasMore] = useState(true)

  const [subSearch, setSubSearch] = useState('')
  const [postKeyword, setPostKeyword] = useState('')
  const [postSearchInput, setPostSearchInput] = useState('')

  const [showCreate, setShowCreate] = useState(false)
  const [createTitle, setCreateTitle] = useState('')
  const [createContent, setCreateContent] = useState('')
  const [createPostType, setCreatePostType] = useState('discussion')
  const [uploadedFile, setUploadedFile] = useState<{ url: string; name: string; type: string; size: number } | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!categoryId) return
    const fetchData = async () => {
      try {
        const [catRes, schoolCountsRes, catCountsRes] = await Promise.all([
          categoryApi.getDetail(categoryId),
          postApi.getSchoolPostCounts(),
          categoryApi.getCounts(),
        ])
        setCategory(catRes.data)
        setSchoolCounts(schoolCountsRes.data || {})
        setCatCounts(catCountsRes.data || {})
        const counts = schoolCountsRes.data || {}
        setSchools((schoolsData as School[]).map(s => ({ ...s, resourceCount: counts[s.id] || 0 })))
      } catch {}
    }
    fetchData()
  }, [categoryId])

  const loadPosts = useCallback(async (sub: SubCategory | null, reset = false, keyword?: string) => {
    if (!sub || !categoryId) return
    setLoading(true)
    try {
      const currentPage = reset ? 1 : page
      const res = await categoryApi.getSubCategoryPosts(sub.id, {
        postType,
        sortType,
        page: currentPage,
        size: 20,
        keyword: keyword || undefined,
      })
      const newPosts = (res.data || []).map((p: any) => ({
        ...p,
        isCreator: p.authorRole === 'CREATOR' || p.authorRole === 'ADMIN' || (p.authorLevel && p.authorLevel !== 'NONE')
      }))
      setPosts(reset ? newPosts : [...posts, ...newPosts])
      setHasMore(newPosts.length === 20)
      setPage(currentPage + 1)
    } catch {}
    setLoading(false)
  }, [categoryId, postType, sortType, page, posts])

  useEffect(() => {
    if (viewMode === 'posts' && activeSub) {
      setPage(1)
      setPosts([])
      loadPosts(activeSub, true, postKeyword || undefined)
    }
  }, [viewMode, activeSub?.id, postType, sortType, postKeyword])

  const handleSubClick = (sub: SubCategory) => {
    setActiveSub(sub)
    setViewMode('posts')
    setPage(1)
    setPosts([])
    setPostKeyword('')
    setPostSearchInput('')
  }

  const handleBackToSubs = () => {
    setViewMode('subs')
    setActiveSub(null)
    setPosts([])
    setPostKeyword('')
    setPostSearchInput('')
  }

  const handlePostSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setPostKeyword(postSearchInput.trim())
  }

  const handleClearPostSearch = () => {
    setPostSearchInput('')
    setPostKeyword('')
  }

  const filteredSchools = useMemo(() => {
    if (!subSearch.trim()) return schools
    const kw = subSearch.trim().toLowerCase()
    return schools.filter(s => s.name.toLowerCase().includes(kw))
  }, [schools, subSearch])

  const filteredSubs = useMemo(() => {
    if (!category?.subCategories) return []
    if (!subSearch.trim()) return category.subCategories
    const kw = subSearch.trim().toLowerCase()
    return category.subCategories.filter(s => s.name.toLowerCase().includes(kw))
  }, [category?.subCategories, subSearch])

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const result = await fileApi.upload(file)
      setUploadedFile({ url: result.url, name: result.originalName || file.name, type: file.type, size: file.size })
      toast.success('文件上传成功')
    } catch {
      toast.error('文件上传失败')
    }
  }

  const handleSubmitPost = async () => {
    if (!createTitle.trim()) {
      toast.error('请输入标题')
      return
    }
    if (!createContent.trim() && !uploadedFile) {
      toast.error('请输入内容或上传文件')
      return
    }
    setSubmitting(true)
    try {
      const payload: any = {
        title: createTitle.trim(),
        content: createContent,
        postType: createPostType,
      }
      if (category?.type === 'school') {
        if (activeSub?.id) {
          payload.schoolId = activeSub.id
        }
      } else {
        payload.categoryId = categoryId
        payload.subCategoryId = activeSub?.id
      }
      if (uploadedFile) {
        payload.fileUrl = uploadedFile.url
        payload.fileName = uploadedFile.name
        payload.fileType = uploadedFile.type
        payload.fileSize = uploadedFile.size
      }
      await postApi.create(payload)
      toast.success('发布成功')
      setShowCreate(false)
      setCreateTitle('')
      setCreateContent('')
      setCreatePostType('discussion')
      setUploadedFile(null)
      if (viewMode === 'posts' && activeSub) {
        loadPosts(activeSub, true, postKeyword || undefined)
      }
    } catch (err: any) {
      toast.error(err.message || '发布失败')
    }
    setSubmitting(false)
  }

  if (!category) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <p className="text-gray-400">加载中...</p>
      </div>
    )
  }

  const IconComp = ICON_MAP[category.icon] || GraduationCap
  const colors = COLOR_MAP[category.color] || DEFAULT_COLORS
  const isSchool = category.type === 'school'
  const catDesc = CATEGORY_DESC_MAP[category.id] || DEFAULT_CAT_DESC

  const getSubCountText = () => {
    const count = isSchool ? schools.length : (category.subCategories?.length || 0)
    return `共收录 ${count} ${catDesc.unit}，${catDesc.desc}`
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => {
            if (viewMode === 'posts') handleBackToSubs()
            else navigate(-1)
          }} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          {(() => {
            if (viewMode === 'posts' && activeSub) {
              const SubIconComp = SUB_ICON_MAP[activeSub.icon] || Hash
              const subColor = getSubColor(activeSub.id)
              return (
                <div className={`w-9 h-9 rounded-xl ${subColor.bg} flex items-center justify-center flex-shrink-0`}>
                  <SubIconComp className={`w-5 h-5 ${subColor.text}`} />
                </div>
              )
            }
            const iconColor = getSubColor(category.id)
            return (
              <div className={`w-9 h-9 rounded-xl ${iconColor.bg} flex items-center justify-center flex-shrink-0`}>
                <IconComp className={`w-5 h-5 ${iconColor.text}`} />
              </div>
            )
          })()}
          <div className="flex-1 min-w-0">
            <h1 className="text-sm font-semibold text-gray-900">
              {viewMode === 'posts' && activeSub ? activeSub.name : category.name}
            </h1>
            <p className="text-xs text-gray-400 truncate">
              {viewMode === 'posts' && activeSub
                ? `${catCounts[`sub_${activeSub.id}`] || activeSub.postCount || 0} 份资料`
                : `${category.postCount || 0} 份资料`}
            </p>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {viewMode === 'subs' && (
          <>
            <p className="text-sm text-gray-500 mb-3">{getSubCountText()}</p>

            <div className="relative mb-4">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={subSearch}
                onChange={(e) => setSubSearch(e.target.value)}
                placeholder="搜索资料、帖子..."
                className="w-full pl-9 pr-4 py-2.5 bg-gray-100 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              />
              {subSearch && (
                <button
                  onClick={() => setSubSearch('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-0.5 hover:bg-gray-200 rounded-full"
                >
                  <X className="w-3.5 h-3.5 text-gray-400" />
                </button>
              )}
            </div>

            {isSchool && (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                {filteredSchools.map((school) => (
                  <SchoolCard key={school.id} school={school} onClick={() => navigate(`/school/${school.id}`)} />
                ))}
                {filteredSchools.length === 0 && (
                  <div className="col-span-full text-center py-8 text-sm text-gray-400">未找到匹配的高校</div>
                )}
              </div>
            )}

            {!isSchool && (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                {filteredSubs.map((sub) => {
                  const count = catCounts[`sub_${sub.id}`] || sub.postCount || 0
                  const SubIconComp = SUB_ICON_MAP[sub.icon] || Hash
                  const subColor = getSubColor(sub.id)
                  return (
                    <div
                      key={sub.id}
                      onClick={() => handleSubClick(sub)}
                      className="bg-white rounded-2xl border border-gray-100 p-4 cursor-pointer hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group"
                    >
                      <div className="flex items-start gap-3">
                        <div className={`w-12 h-12 rounded-xl ${subColor.bg} flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform`}>
                          <SubIconComp className={`w-6 h-6 ${subColor.text}`} />
                        </div>
                        <div className="flex-1 min-w-0 pt-0.5">
                          <h3 className="font-semibold text-gray-900 text-sm mb-1 group-hover:text-blue-600 transition-colors truncate">
                            {sub.name}
                          </h3>
                          <div className="flex items-center gap-1">
                            <FileText className="w-3 h-3 text-gray-400" />
                            <span className="text-xs text-gray-400">{count} 份资料</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  )
                })}
                {filteredSubs.length === 0 && (
                  <div className="col-span-full text-center py-8 text-sm text-gray-400">未找到匹配的板块</div>
                )}
              </div>
            )}
          </>
        )}

        {viewMode === 'posts' && activeSub && (
          <>
            <form onSubmit={handlePostSearch} className="relative mb-3">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={postSearchInput}
                onChange={(e) => setPostSearchInput(e.target.value)}
                placeholder="搜索资料、帖子..."
                className="w-full pl-9 pr-10 py-2.5 bg-gray-100 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              />
              {postSearchInput && (
                <button
                  type="button"
                  onClick={handleClearPostSearch}
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-0.5 hover:bg-gray-200 rounded-full"
                >
                  <X className="w-3.5 h-3.5 text-gray-400" />
                </button>
              )}
            </form>

            {postKeyword && (
              <div className="flex items-center gap-2 mb-3 px-3 py-1.5 bg-blue-50 rounded-lg text-xs text-blue-600">
                <span>搜索: "{postKeyword}"</span>
                <button onClick={handleClearPostSearch} className="ml-auto hover:text-blue-800">
                  <X className="w-3 h-3" />
                </button>
              </div>
            )}

            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-1">
                {(['all', 'resource', 'discussion'] as const).map((t) => (
                  <button
                    key={t}
                    onClick={() => setPostType(t)}
                    className={`px-3 py-1.5 text-sm rounded-full transition-colors ${
                      postType === t
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-gray-500 hover:bg-gray-100'
                    }`}
                  >
                    {t === 'all' ? '全部' : t === 'resource' ? '资源' : '讨论'}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-1">
                {([
                  { key: 'latest', icon: Clock, label: '最新' },
                  { key: 'hottest', icon: Flame, label: '最热' },
                  { key: 'active', icon: MessageSquare, label: '活跃' },
                ] as const).map((s) => (
                  <button
                    key={s.key}
                    onClick={() => setSortType(s.key)}
                    className={`px-3 py-1.5 text-xs rounded-full transition-colors flex items-center gap-1 ${
                      sortType === s.key
                        ? 'bg-gray-900 text-white'
                        : 'text-gray-500 hover:bg-gray-100'
                    }`}
                  >
                    <s.icon className="w-3 h-3" />
                    {s.label}
                  </button>
                ))}
              </div>
            </div>

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
                  onClick={() => {
                    if (post.schoolId) {
                      navigate(`/school/${post.schoolId}/post/${post.id}`)
                    } else {
                      navigate(`/category/${post.categoryId}/post/${post.id}`)
                    }
                  }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 cursor-pointer hover:border-gray-200 hover:shadow-sm transition-all"
                >
                  {/* 顶部作者栏 */}
                  <div className="flex items-center gap-2 mb-2">
                    <img
                      src={avatarUrl}
                      alt={post.authorName}
                      className="w-8 h-8 rounded-full flex-shrink-0 object-cover"
                    />
                    <span className="text-sm text-gray-700 font-medium flex items-center gap-1">
                      {post.authorName}
                      {post.authorLevel && post.authorLevel !== 'NONE' && (
                        <CreatorLevelIcon level={post.authorLevel} />
                      )}
                    </span>
                    <span className="text-xs text-gray-400 ml-1">{timeAgo(post.createTime)}</span>
                  </div>

                  {/* 内容区 */}
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
                      {post.commentCount}
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

              {loading && (
                <div className="text-center py-4 text-sm text-gray-400">加载中...</div>
              )}

              {!loading && posts.length === 0 && (
                <div className="text-center py-16">
                  <p className="text-gray-400 text-sm">暂无内容</p>
                  <p className="text-gray-300 text-xs mt-1">成为第一个发帖的人吧</p>
                </div>
              )}

              {!loading && hasMore && posts.length > 0 && (
                <button
                  onClick={() => loadPosts(activeSub, false, postKeyword || undefined)}
                  className="w-full py-3 text-sm text-gray-400 hover:text-gray-600 transition-colors"
                >
                  加载更多
                </button>
              )}
            </div>
          </>
        )}
      </div>

      {viewMode === 'posts' && activeSub && (
        <button
          onClick={() => setShowCreate(true)}
          className="fixed bottom-6 right-6 w-12 h-12 rounded-full bg-blue-600 text-white shadow-lg hover:bg-blue-700 hover:scale-105 transition-all flex items-center justify-center z-30"
        >
          <Plus className="w-6 h-6" />
        </button>
      )}

      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center justify-center" onClick={() => setShowCreate(false)}>
          <div className="bg-white w-full sm:max-w-lg sm:rounded-2xl rounded-t-2xl max-h-[85vh] overflow-hidden flex flex-col" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900">发布帖子</h3>
              <button onClick={() => setShowCreate(false)} className="p-1 hover:bg-gray-100 rounded-full">
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              <div>
                <label className="text-xs text-gray-500 mb-1 block">标题</label>
                <input
                  type="text"
                  value={createTitle}
                  onChange={(e) => setCreateTitle(e.target.value)}
                  placeholder="输入帖子标题"
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setCreatePostType('discussion')}
                  className={`flex-1 py-2 text-xs rounded-lg border transition-colors ${
                    createPostType === 'discussion' ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-500'
                  }`}
                >
                  讨论帖
                </button>
                <button
                  onClick={() => setCreatePostType('resource')}
                  className={`flex-1 py-2 text-xs rounded-lg border transition-colors ${
                    createPostType === 'resource' ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-500'
                  }`}
                >
                  资源帖
                </button>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">内容</label>
                <textarea
                  value={createContent}
                  onChange={(e) => setCreateContent(e.target.value)}
                  placeholder="分享你的想法..."
                  rows={5}
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-blue-400 resize-none"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">附件（可选）</label>
                {uploadedFile ? (
                  <div className="flex items-center gap-2 bg-gray-50 rounded-lg p-2">
                    <File className="w-4 h-4 text-blue-500" />
                    <span className="text-xs text-gray-600 flex-1 truncate">{uploadedFile.name}</span>
                    <button onClick={() => setUploadedFile(null)} className="text-gray-400 hover:text-red-500">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ) : (
                  <label className="flex items-center justify-center gap-2 border-2 border-dashed border-gray-200 rounded-lg p-4 cursor-pointer hover:border-blue-300 transition-colors">
                    <Upload className="w-5 h-5 text-gray-400" />
                    <span className="text-xs text-gray-400">点击上传文件</span>
                    <input type="file" className="hidden" onChange={handleFileUpload} />
                  </label>
                )}
              </div>
            </div>
            <div className="p-4 border-t border-gray-100">
              <button
                onClick={handleSubmitPost}
                disabled={submitting}
                className="w-full py-2.5 rounded-lg text-sm font-medium text-white transition-all bg-blue-600 hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed"
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
