import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/common/NavBar'
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
  Settings,
  HelpCircle,
  Shield,
  FileText,
  Download,
} from 'lucide-react'

type TabType = 'browse' | 'starred' | 'liked'

interface MockPost {
  id: string
  title: string
  type: 'resource' | 'discussion'
  school: string
  author: string
  time: string
  stats: string
}

const mockBrowsePosts: MockPost[] = [
  { id: '1', title: '高等数学期末复习重点整理', type: 'resource', school: '北京大学', author: '数学小王子', time: '2小时前', stats: '328收藏 · 2103浏览' },
  { id: '2', title: '2025年春季学期选课交流', type: 'discussion', school: '北京大学', author: '校园生活家', time: '5小时前', stats: '89收藏 · 3560浏览' },
  { id: '3', title: '线性代数知识点总结（思维导图版）', type: 'resource', school: '北京大学', author: '学霸小李', time: '昨天', stats: '256收藏 · 1820浏览' },
]

const mockStarredPosts: MockPost[] = [
  { id: '2', title: '2025年春季学期选课交流', type: 'discussion', school: '北京大学', author: '校园生活家', time: '5小时前', stats: '89收藏 · 3560浏览' },
  { id: '4', title: '英语四六级高频词汇表', type: 'resource', school: '北京大学', author: '英语达人', time: '3天前', stats: '892收藏 · 7800浏览' },
]

const mockLikedPosts: MockPost[] = [
  { id: '5', title: '期末考试周图书馆占座攻略', type: 'discussion', school: '北京大学', author: '早起鸟', time: '1天前', stats: '445收藏 · 5200浏览' },
  { id: '6', title: '校园美食推荐第二弹', type: 'discussion', school: '北京大学', author: '吃货小分队', time: '2天前', stats: '567收藏 · 8900浏览' },
]

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const [activeTab, setActiveTab] = useState<TabType>('browse')
  const [showEditModal, setShowEditModal] = useState(false)
  const [editUsername, setEditUsername] = useState(user?.username || '')
  const [editBio, setEditBio] = useState('这个人很懒，什么都没留下...')
  const [bio, setBio] = useState('这个人很懒，什么都没留下...')
  const [avatar, setAvatar] = useState<string | null>(null)

  const tabPosts: Record<TabType, MockPost[]> = {
    browse: mockBrowsePosts,
    starred: mockStarredPosts,
    liked: mockLikedPosts,
  }

  const tabIcons: Record<TabType, React.ReactNode> = {
    browse: <Clock className="w-4 h-4" />,
    starred: <Star className="w-4 h-4" />,
    liked: <ThumbsUp className="w-4 h-4" />,
  }

  const tabLabels: Record<TabType, string> = {
    browse: '浏览历史',
    starred: '我的收藏',
    liked: '我的点赞',
  }

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (ev) => {
        setAvatar(ev.target?.result as string)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleSaveProfile = () => {
    setBio(editBio)
    setShowEditModal(false)
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
                  <img src={avatar} alt="头像" className="w-full h-full object-cover" />
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

          {/* 统计数据 */}
          <div className="grid grid-cols-4 gap-2 mt-5">
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">3</p>
              <p className="text-xs text-gray-400 mt-0.5">浏览</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">2</p>
              <p className="text-xs text-gray-400 mt-0.5">收藏</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">2</p>
              <p className="text-xs text-gray-400 mt-0.5">点赞</p>
            </div>
            <div className="text-center">
              <p className="text-base font-bold text-gray-900">0</p>
              <p className="text-xs text-gray-400 mt-0.5">上传</p>
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

      {/* Tab 切换 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 p-1 flex">
          {(Object.keys(tabLabels) as TabType[]).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm rounded-xl transition-colors ${
                activeTab === tab
                  ? 'bg-gray-900 text-white font-medium'
                  : 'text-gray-500 hover:bg-gray-50'
              }`}
            >
              {tabIcons[tab]}
              {tabLabels[tab]}
            </button>
          ))}
        </div>
      </div>

      {/* 帖子列表 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        {tabPosts[activeTab].length > 0 ? (
          <div className="space-y-2">
            {tabPosts[activeTab].map((post) => (
              <div
                key={post.id}
                className="bg-white rounded-xl border border-gray-100 p-3 hover:border-gray-200 transition-colors cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
                    post.type === 'resource' ? 'bg-blue-50' : 'bg-orange-50'
                  }`}>
                    {post.type === 'resource' ? (
                      <FileText className="w-4 h-4 text-blue-600" />
                    ) : (
                      <Download className="w-4 h-4 text-orange-600" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-gray-900 font-medium line-clamp-1">{post.title}</p>
                    <div className="flex items-center gap-2 mt-1 text-xs text-gray-400">
                      <span>{post.school}</span>
                      <span>·</span>
                      <span>{post.author}</span>
                      <span>·</span>
                      <span>{post.time}</span>
                    </div>
                    <p className="text-xs text-gray-300 mt-1">{post.stats}</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-gray-300 flex-shrink-0 mt-2" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-gray-100 py-12 text-center">
            <div className="w-12 h-12 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-3">
              {tabIcons[activeTab]}
            </div>
            <p className="text-gray-400 text-sm">暂无{tabLabels[activeTab]}记录</p>
          </div>
        )}
      </div>

      {/* 功能菜单 */}
      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <button className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-blue-50 rounded-lg flex items-center justify-center">
              <User className="w-4 h-4 text-blue-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">账号与安全</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-green-50 rounded-lg flex items-center justify-center">
              <Shield className="w-4 h-4 text-green-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">隐私设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-purple-50 rounded-lg flex items-center justify-center">
              <Settings className="w-4 h-4 text-purple-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">通用设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors">
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
                    <img src={avatar} alt="头像" className="w-full h-full object-cover" />
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
