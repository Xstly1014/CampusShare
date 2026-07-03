import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ChevronLeft,
  FileText,
  Star,
  ThumbsUp,
  Clock,
  UserPlus,
  UserCheck,
  Copy,
  MessageSquare,
  BadgeCheck,
  Crown,
  Eye,
  Heart,
  File,
} from 'lucide-react'
import { userApi } from '../services/api'
import { toast } from '../stores/toastStore'

type TabType = 'posts' | 'starred' | 'liked' | 'history'

interface UserProfile {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
  postCount: number
  totalViews: number
  totalLikes: number
  totalStars: number
  followerCount: number
  followingCount: number
  isFollowing: boolean
  isSelf: boolean
  isCreator?: boolean
  creatorLevel?: string
  creatorLevelName?: string
}

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
    case 'AUTHORITY':
      return 'text-yellow-500'
    case 'SENIOR':
      return 'text-orange-500'
    case 'INTERMEDIATE':
      return 'text-purple-500'
    case 'JUNIOR':
      return 'text-blue-500'
    default:
      return 'text-blue-500'
  }
}

function CreatorLevelIcon({ level, size = 'w-3.5 h-3.5' }: { level?: string; size?: string }) {
  if (!level || level === 'NONE') return null
  const colorClass = getCreatorLevelColor(level)
  if (level === 'AUTHORITY' || level === 'SENIOR') {
    return <Crown className={`${size} ${colorClass}`} />
  }
  return <BadgeCheck className={`${size} ${colorClass}`} />
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
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}月${date.getDate()}日`
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

const tabs: { key: TabType; label: string; icon: React.ReactNode }[] = [
  { key: 'posts', label: '帖子', icon: <FileText className="w-4 h-4" /> },
  { key: 'starred', label: '收藏', icon: <Star className="w-4 h-4" /> },
  { key: 'liked', label: '点赞', icon: <ThumbsUp className="w-4 h-4" /> },
  { key: 'history', label: '浏览', icon: <Clock className="w-4 h-4" /> },
]

export default function UserProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<TabType>('posts')
  const [posts, setPosts] = useState<BackendPost[]>([])
  const [listLoading, setListLoading] = useState(false)
  const [showAvatarModal, setShowAvatarModal] = useState(false)

  const fetchProfile = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await userApi.getUserProfile(userId)
      setProfile(res.data)
    } catch {
      toast.error('用户不存在')
      navigate(-1)
    } finally {
      setLoading(false)
    }
  }, [userId, navigate])

  useEffect(() => {
    fetchProfile()
  }, [fetchProfile])

  const fetchList = useCallback(async () => {
    if (!userId) return
    setListLoading(true)
    try {
      let res
      if (activeTab === 'posts') res = await userApi.getUserPosts(userId)
      else if (activeTab === 'starred') res = await userApi.getUserStarred(userId)
      else if (activeTab === 'liked') res = await userApi.getUserLiked(userId)
      else res = await userApi.getUserHistory(userId)
      setPosts(
        (res.data?.records || res.data || []).map((p: any) => ({
          ...p,
          isCreator:
            p.authorRole === 'CREATOR' ||
            p.authorRole === 'ADMIN' ||
            (p.authorLevel && p.authorLevel !== 'NONE'),
        })),
      )
    } catch {
      setPosts([])
    } finally {
      setListLoading(false)
    }
  }, [userId, activeTab])

  useEffect(() => {
    fetchList()
  }, [fetchList])

  const handleFollow = async () => {
    if (!userId) return
    try {
      const res = await userApi.toggleFollow(userId)
      setProfile((prev) =>
        prev
          ? {
              ...prev,
              isFollowing: res.data,
              followerCount: res.data ? prev.followerCount + 1 : prev.followerCount - 1,
            }
          : prev,
      )
    } catch (err) {
      toast.error((err as Error).message || '操作失败')
    }
  }

  const handleCopyId = () => {
    if (userId) {
      navigator.clipboard.writeText(userId)
      toast.success('ID已复制')
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    )
  }

  if (!profile) return null

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 顶部导航 */}
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors"
          >
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <span className="text-sm font-medium text-gray-900">
            {profile.isSelf ? '我的主页' : '用户主页'}
          </span>
        </div>
      </div>

      {/* 用户信息 */}
      <div className="bg-white border-b border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-6">
          <div className="flex items-start gap-4">
            <div
              onClick={() => profile.avatarUrl && setShowAvatarModal(true)}
              className={`w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0 ${profile.avatarUrl ? 'cursor-pointer' : ''}`}
            >
              {profile.avatarUrl ? (
                <img
                  src={
                    profile.avatarUrl.startsWith('/files/')
                      ? `/api${profile.avatarUrl}`
                      : profile.avatarUrl
                  }
                  alt={profile.username}
                  className="w-full h-full object-cover"
                />
              ) : (
                <span className="text-white text-xl font-bold">
                  {profile.username?.substring(0, 1).toUpperCase() || 'U'}
                </span>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold text-gray-900">{profile.username}</h1>
                {profile.creatorLevel && profile.creatorLevel !== 'NONE' && (
                  <span title={profile.creatorLevelName || '认证创作者'}>
                    <CreatorLevelIcon level={profile.creatorLevel} size="w-5 h-5" />
                  </span>
                )}
                {profile.isCreator && !profile.creatorLevel && (
                  <span
                    className="inline-flex items-center justify-center w-5 h-5 bg-gradient-to-br from-amber-400 to-orange-500 rounded-full flex-shrink-0"
                    title="认证创作者"
                  >
                    <span className="text-white text-[10px] font-bold leading-none">V</span>
                  </span>
                )}
                <button
                  onClick={handleCopyId}
                  className="p-1 hover:bg-gray-100 rounded-full transition-colors"
                  title="复制ID"
                >
                  <Copy className="w-3.5 h-3.5 text-gray-400" />
                </button>
              </div>
              <p className="text-xs text-gray-400 mt-0.5 line-clamp-1">
                {profile.bio || '这个人很懒，什么都没留下...'}
              </p>
              <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                <span>关注 {profile.followingCount}</span>
                <span>粉丝 {profile.followerCount}</span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {profile.isSelf ? (
                <button
                  onClick={() => navigate('/profile')}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
                >
                  编辑资料
                </button>
              ) : (
                <>
                  <button
                    onClick={() => navigate(`/messages/${userId}`)}
                    className="flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
                  >
                    <MessageSquare className="w-4 h-4" />
                    私信
                  </button>
                  <button
                    onClick={handleFollow}
                    className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${profile.isFollowing ? 'bg-gray-100 text-gray-600' : 'bg-blue-600 text-white hover:bg-blue-700'}`}
                  >
                    {profile.isFollowing ? (
                      <>
                        <UserCheck className="w-4 h-4" />
                        已关注
                      </>
                    ) : (
                      <>
                        <UserPlus className="w-4 h-4" />
                        关注
                      </>
                    )}
                  </button>
                </>
              )}
            </div>
          </div>

          {/* 统计数据 */}
          <div className="grid grid-cols-4 gap-2 mt-5">
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{profile.totalViews}</p>
              <p className="text-xs text-gray-400 mt-0.5">总浏览</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{profile.totalLikes}</p>
              <p className="text-xs text-gray-400 mt-0.5">获赞</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{profile.totalStars}</p>
              <p className="text-xs text-gray-400 mt-0.5">被收藏</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{profile.postCount}</p>
              <p className="text-xs text-gray-400 mt-0.5">帖子</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tab 切换 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 p-1 flex">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm rounded-xl transition-colors ${activeTab === tab.key ? 'bg-gray-900 text-white font-medium' : 'text-gray-500 hover:bg-gray-50'}`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* 帖子列表 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        {listLoading ? (
          <div className="text-center py-12">
            <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
          </div>
        ) : posts.length > 0 ? (
          <div className="space-y-3">
            {posts.map((post) => {
              const isImage = post.fileType?.startsWith('image/')
              const hasFile = !!post.fileUrl && !isImage
              const avatarUrl = post.authorAvatar
                ? post.authorAvatar.startsWith('/files/')
                  ? `/api${post.authorAvatar}`
                  : post.authorAvatar
                : `https://api.dicebear.com/7.x/avataaars/svg?seed=${post.authorId}`
              return (
                <div
                  key={post.id}
                  onClick={() => navigate(`/school/${post.schoolId}/post/${post.id}`)}
                  className="bg-white rounded-2xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all p-4 cursor-pointer"
                >
                  {/* 顶部作者栏 */}
                  <div className="flex items-center gap-2 mb-2">
                    <img
                      src={avatarUrl}
                      alt={post.authorName}
                      className="w-8 h-8 rounded-full flex-shrink-0 object-cover"
                    />
                    <span className="text-sm text-gray-700 font-medium flex items-center gap-1">
                      {post.authorName || post.authorId.slice(0, 8)}
                      {post.authorLevel && post.authorLevel !== 'NONE' && (
                        <CreatorLevelIcon level={post.authorLevel} />
                      )}
                    </span>
                    <span className="text-xs text-gray-400 ml-1">
                      {formatTime(post.createTime)}
                    </span>
                  </div>

                  {/* 内容区 */}
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${
                          post.postType === 'resource'
                            ? 'bg-blue-50 text-blue-600'
                            : 'bg-orange-50 text-orange-600'
                        }`}
                      >
                        {post.postType === 'resource' ? '资料' : '讨论'}
                      </span>
                    </div>
                    <h3
                      className="text-base font-semibold text-gray-900 leading-snug"
                      style={{
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                    >
                      {post.title}
                    </h3>
                    {post.content && post.content.trim() && (
                      <p
                        className="text-sm text-gray-600 mt-1"
                        style={{
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                        }}
                      >
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
                        <span className="text-sm text-gray-700 truncate flex-1">
                          {post.fileName || '附件'}
                        </span>
                        {formatFileSize(post.fileSize) && (
                          <span className="text-xs text-gray-400 flex-shrink-0">
                            {formatFileSize(post.fileSize)}
                          </span>
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
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-400 text-sm">暂无内容</p>
          </div>
        )}
      </div>

      {/* Avatar viewer modal */}
      {showAvatarModal && profile?.avatarUrl && (
        <div
          className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center"
          onClick={() => setShowAvatarModal(false)}
        >
          <div className="relative" onClick={(e) => e.stopPropagation()}>
            <img
              src={
                profile.avatarUrl.startsWith('/files/')
                  ? `/api${profile.avatarUrl}`
                  : profile.avatarUrl
              }
              alt={profile.username}
              className="w-72 h-72 sm:w-80 sm:h-80 rounded-full object-cover border-4 border-white/20"
            />
          </div>
          <button
            onClick={() => setShowAvatarModal(false)}
            className="absolute top-6 right-6 w-10 h-10 bg-white/20 rounded-full flex items-center justify-center text-white text-xl hover:bg-white/30 transition-colors"
          >
            ✕
          </button>
        </div>
      )}
    </div>
  )
}
