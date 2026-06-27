import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/common/NavBar'
import { postApi, fileApi, userApi } from '../services/api'
import { toast } from '../stores/toastStore'
import {
  User,
  Mail,
  Phone,
  LogOut,
  ChevronRight,
  Camera,
  Edit3,
  Award,
  Clock,
  Star,
  ThumbsUp,
  MessageSquare,
  Settings,
  HelpCircle,
  Shield,
  FileText,
  Eye,
} from 'lucide-react'

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [showEditModal, setShowEditModal] = useState(false)
  const [editUsername, setEditUsername] = useState(user?.username || '')
  const [editBio, setEditBio] = useState(user?.bio || '这个人很懒，什么都没留下...')
  const [bio, setBio] = useState(user?.bio || '这个人很懒，什么都没留下...')
  const [avatar, setAvatar] = useState<string | null>(user?.avatarUrl || null)
  const [counts, setCounts] = useState({ browse: 0, starred: 0, liked: 0 })
  const [stats, setStats] = useState({ totalViews: 0, totalLikes: 0, totalStars: 0, postCount: 0 })
  const [commentCount, setCommentCount] = useState(0)

  // Fetch latest user profile from backend
  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await userApi.getMe()
        const u = res.data
        if (u.avatarUrl) setAvatar(u.avatarUrl)
        if (u.bio) {
          setBio(u.bio)
          setEditBio(u.bio)
        }
        if (u.username) setEditUsername(u.username)
        // Update localStorage
        localStorage.setItem('campusshare_user', JSON.stringify(u))
      } catch {
        // ignore
      }
    }
    fetchUser()
  }, [])

  // Fetch counts for the three lists + my post stats
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [historyRes, starredRes, likedRes, statsRes, commentsRes] = await Promise.all([
          postApi.getHistory(1, 1),
          postApi.getStarred(1, 1),
          postApi.getLiked(1, 1),
          postApi.getMyPostStats(),
          postApi.getMyComments(),
        ])
        setCounts({
          browse: (historyRes.data || []).length,
          starred: (starredRes.data || []).length,
          liked: (likedRes.data || []).length,
        })
        setStats(statsRes.data || { totalViews: 0, totalLikes: 0, totalStars: 0, postCount: 0 })
        setCommentCount((commentsRes.data || []).length)
      } catch (err) {
        // Silently ignore, counts stay 0
      }
    }
    fetchData()
  }, [])

  const listEntries = [
    { key: 'mine', label: '我的帖子', icon: <FileText className="w-5 h-5" />, count: stats.postCount, color: 'bg-indigo-50 text-indigo-600' },
    { key: 'comments', label: '我的回复', icon: <MessageSquare className="w-5 h-5" />, count: commentCount, color: 'bg-teal-50 text-teal-600' },
    { key: 'history', label: '浏览历史', icon: <Clock className="w-5 h-5" />, count: counts.browse, color: 'bg-blue-50 text-blue-600' },
    { key: 'starred', label: '我的收藏', icon: <Star className="w-5 h-5" />, count: counts.starred, color: 'bg-orange-50 text-orange-600' },
    { key: 'liked', label: '我的点赞', icon: <ThumbsUp className="w-5 h-5" />, count: counts.liked, color: 'bg-red-50 text-red-600' },
  ]

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // Preview locally first
    const reader = new FileReader()
    reader.onload = (ev) => {
      setAvatar(ev.target?.result as string)
    }
    reader.readAsDataURL(file)

    // Upload then save to backend
    try {
      const uploadRes = await fileApi.upload(file)
      const avatarUrl = uploadRes.url
      const res = await userApi.updateProfile({ avatarUrl })
      // Update auth context user data in localStorage
      const savedUser = localStorage.getItem('campusshare_user')
      if (savedUser) {
        const userData = JSON.parse(savedUser)
        userData.avatarUrl = avatarUrl
        localStorage.setItem('campusshare_user', JSON.stringify(userData))
      }
      setAvatar(avatarUrl)
      toast.success('头像修改成功')
    } catch (err) {
      toast.error((err as Error).message || '头像上传失败')
    }
  }

  const handleSaveProfile = async () => {
    try {
      const res = await userApi.updateProfile({
        username: editUsername.trim() || undefined,
        bio: editBio,
      })
      // Update auth context user data in localStorage
      const savedUser = localStorage.getItem('campusshare_user')
      if (savedUser) {
        const userData = JSON.parse(savedUser)
        userData.username = editUsername.trim() || userData.username
        userData.bio = editBio
        localStorage.setItem('campusshare_user', JSON.stringify(userData))
      }
      setBio(editBio)
      setShowEditModal(false)
      toast.success('资料保存成功')
      // Reload to reflect updated username
      window.location.reload()
    } catch (err) {
      toast.error((err as Error).message || '保存失败')
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      {/* 顶部个人信息 */}
      <div className="bg-white border-b border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-6">
          <div className="flex items-start gap-4">
            {/* 头像 */}
            <div className="relative group">
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden">
                {avatar ? (
                  <img
                    src={avatar.startsWith('/files/') ? `/api${avatar}` : avatar}
                    alt="头像"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <span className="text-white text-xl font-bold">
                    {user?.username?.substring(0, 1).toUpperCase() || 'U'}
                  </span>
                )}
              </div>
              <label className="absolute bottom-0 right-0 w-6 h-6 bg-gray-900 rounded-full flex items-center justify-center cursor-pointer opacity-0 group-hover:opacity-100 transition-opacity">
                <Camera className="w-3 h-3 text-white" />
                <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
              </label>
            </div>

            {/* 用户信息 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold text-gray-900">{user?.username || '用户'}</h1>
                <button
                  onClick={() => setShowEditModal(true)}
                  className="p-1 hover:bg-gray-100 rounded-full transition-colors"
                >
                  <Edit3 className="w-3.5 h-3.5 text-gray-400" />
                </button>
              </div>
              <p className="text-xs text-gray-400 mt-0.5 line-clamp-1">{bio}</p>
              <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                <span className="flex items-center gap-1">
                  <Mail className="w-3 h-3" />
                  {user?.email || '未绑定邮箱'}
                </span>
                {user?.phone && (
                  <span className="flex items-center gap-1">
                    <Phone className="w-3 h-3" />
                    {user.phone}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* 统计数据 - 纯展示，我的帖子的总浏览量/获赞/被收藏/帖子数 */}
          <div className="grid grid-cols-4 gap-2 mt-5">
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{stats.totalViews}</p>
              <p className="text-xs text-gray-400 mt-0.5">总浏览</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{stats.totalLikes}</p>
              <p className="text-xs text-gray-400 mt-0.5">获赞</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{stats.totalStars}</p>
              <p className="text-xs text-gray-400 mt-0.5">被收藏</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">{stats.postCount}</p>
              <p className="text-xs text-gray-400 mt-0.5">帖子</p>
            </div>
          </div>
        </div>
      </div>

      {/* 认证创作者入口 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-gradient-to-r from-amber-50 to-orange-50 rounded-2xl border border-amber-100 p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-amber-400 to-orange-500 rounded-xl flex items-center justify-center">
              <Award className="w-5 h-5 text-white" />
            </div>
            <div>
              <p className="text-sm font-semibold text-amber-900">认证创作者</p>
              <p className="text-xs text-amber-600">申请认证，享受专属激励奖励</p>
            </div>
          </div>
          <button className="px-3 py-1.5 bg-amber-500 text-white text-xs font-medium rounded-full hover:bg-amber-600 transition-colors">
            立即申请
          </button>
        </div>
      </div>

      {/* 我的互动 - 入口按钮（类似抖音） */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          {listEntries.map((entry, idx) => (
            <button
              key={entry.key}
              onClick={() => navigate(`/profile/${entry.key}`)}
              className={`w-full flex items-center gap-3 px-4 py-4 hover:bg-gray-50 transition-colors ${
                idx < listEntries.length - 1 ? 'border-b border-gray-50' : ''
              }`}
            >
              <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${entry.color}`}>
                {entry.icon}
              </div>
              <span className="flex-1 text-left text-sm text-gray-700 font-medium">{entry.label}</span>
              {entry.count > 0 && (
                <span className="text-xs text-gray-400">{entry.count}</span>
              )}
              <ChevronRight className="w-4 h-4 text-gray-300" />
            </button>
          ))}
        </div>
      </div>

      {/* 功能菜单 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <button onClick={() => navigate('/settings/account')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-blue-50 rounded-lg flex items-center justify-center">
              <User className="w-4 h-4 text-blue-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">账号与安全</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/privacy')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-green-50 rounded-lg flex items-center justify-center">
              <Shield className="w-4 h-4 text-green-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">隐私设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/general')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-purple-50 rounded-lg flex items-center justify-center">
              <Settings className="w-4 h-4 text-purple-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">通用设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/help')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors">
            <div className="w-8 h-8 bg-gray-50 rounded-lg flex items-center justify-center">
              <HelpCircle className="w-4 h-4 text-gray-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">帮助与反馈</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
        </div>
      </div>

      {/* 退出登录 */}
      <div className="max-w-5xl mx-auto px-4 mt-3 mb-6">
        <button
          onClick={logout}
          className="w-full bg-white rounded-2xl border border-gray-100 py-3.5 flex items-center justify-center gap-2 text-red-500 text-sm font-medium hover:bg-red-50 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          退出登录
        </button>
      </div>

      {/* 编辑资料弹窗 */}
      {showEditModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center" onClick={() => setShowEditModal(false)}>
          <div
            className="bg-white w-full sm:max-w-md sm:rounded-2xl rounded-t-3xl p-6 max-h-[80vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-lg font-bold text-gray-900 mb-5">编辑资料</h2>

            {/* 头像 */}
            <div className="flex justify-center mb-5">
              <div className="relative group">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden">
                  {avatar ? (
                    <img
                      src={avatar.startsWith('/files/') ? `/api${avatar}` : avatar}
                      alt="头像"
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <span className="text-white text-2xl font-bold">
                      {user?.username?.substring(0, 1).toUpperCase() || 'U'}
                    </span>
                  )}
                </div>
                <label className="absolute bottom-0 right-0 w-7 h-7 bg-gray-900 rounded-full flex items-center justify-center cursor-pointer">
                  <Camera className="w-3.5 h-3.5 text-white" />
                  <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
                </label>
              </div>
            </div>

            {/* 用户名 */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">用户名</label>
              <input
                type="text"
                value={editUsername}
                onChange={(e) => setEditUsername(e.target.value)}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
              />
            </div>

            {/* 个人简介 */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">个人简介</label>
              <textarea
                value={editBio}
                onChange={(e) => setEditBio(e.target.value)}
                rows={3}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all resize-none"
              />
            </div>

            {/* 按钮 */}
            <div className="flex gap-3">
              <button
                onClick={() => setShowEditModal(false)}
                className="flex-1 py-3 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSaveProfile}
                className="flex-1 py-3 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 底部导航栏 */}
      <NavBar />
    </div>
  )
}
