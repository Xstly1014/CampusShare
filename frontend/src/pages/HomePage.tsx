import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  GraduationCap,
  Music,
  Clapperboard,
  Sparkles,
  Gamepad2,
  TrendingUp,
  Briefcase,
  AppWindow,
  UtensilsCrossed,
  Plane,
  Camera,
  BookOpen,
  Search,
} from 'lucide-react'
import NavBar from '../components/common/NavBar'
import type { Category } from '../services/api'
import { userApi } from '../services/api'
import { useCategories } from '../hooks/queries'
import schools from '../data/schools.json'

const ICON_MAP: Record<string, React.ElementType> = {
  GraduationCap,
  Music,
  Clapperboard,
  Sparkles,
  Gamepad2,
  TrendingUp,
  Briefcase,
  AppWindow,
  UtensilsCrossed,
  Plane,
  Camera,
  BookOpen,
}

const COLOR_MAP: Record<string, { bg: string; text: string; ring: string }> = {
  blue: { bg: 'from-blue-400 to-blue-600', text: 'text-blue-600', ring: 'ring-blue-100' },
  purple: { bg: 'from-purple-400 to-purple-600', text: 'text-purple-600', ring: 'ring-purple-100' },
  red: { bg: 'from-red-400 to-red-600', text: 'text-red-600', ring: 'ring-red-100' },
  pink: { bg: 'from-pink-400 to-pink-600', text: 'text-pink-600', ring: 'ring-pink-100' },
  green: { bg: 'from-green-400 to-green-600', text: 'text-green-600', ring: 'ring-green-100' },
  emerald: {
    bg: 'from-emerald-400 to-emerald-600',
    text: 'text-emerald-600',
    ring: 'ring-emerald-100',
  },
  amber: { bg: 'from-amber-400 to-amber-600', text: 'text-amber-600', ring: 'ring-amber-100' },
  cyan: { bg: 'from-cyan-400 to-cyan-600', text: 'text-cyan-600', ring: 'ring-cyan-100' },
  orange: { bg: 'from-orange-400 to-orange-600', text: 'text-orange-600', ring: 'ring-orange-100' },
  sky: { bg: 'from-sky-400 to-sky-600', text: 'text-sky-600', ring: 'ring-sky-100' },
  indigo: { bg: 'from-indigo-400 to-indigo-600', text: 'text-indigo-600', ring: 'ring-indigo-100' },
  teal: { bg: 'from-teal-400 to-teal-600', text: 'text-teal-600', ring: 'ring-teal-100' },
}

const DEFAULT_COLORS = {
  bg: 'from-gray-400 to-gray-600',
  text: 'text-gray-600',
  ring: 'ring-gray-100',
}

const CATEGORY_UNIT_MAP: Record<string, string> = {
  'cat-campus': '所高校',
  'cat-music': '种曲风',
  'cat-movie': '种题材',
  'cat-anime': '种类型',
  'cat-game': '个平台',
  'cat-stock': '个板块',
  'cat-interview': '个方向',
  'cat-software': '种分类',
  'cat-food': '种菜系',
  'cat-travel': '个目的地',
  'cat-photo': '个技巧',
  'cat-book': '类书籍',
}

interface UserResult {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
}

export default function HomePage() {
  const [searchKeyword, setSearchKeyword] = useState('')
  const [userResults, setUserResults] = useState<UserResult[]>([])
  const navigate = useNavigate()
  const { data: categories = [] } = useCategories()

  useEffect(() => {
    if (!searchKeyword.trim()) {
      setUserResults([])
      return
    }
    const timer = setTimeout(async () => {
      try {
        const res = await userApi.searchUsers(searchKeyword.trim())
        setUserResults(res.data || [])
      } catch {
        setUserResults([])
      }
    }, 300)
    return () => clearTimeout(timer)
  }, [searchKeyword])

  const handleCategoryClick = (cat: Category) => {
    if (cat.type === 'school') {
      navigate(`/category/${cat.id}`)
    } else {
      navigate(`/category/${cat.id}`)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="sticky top-0 bg-white border-b border-gray-100 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="搜索用户..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-gray-100 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:bg-white transition-all"
            />
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-6">
        {searchKeyword && userResults.length > 0 && (
          <div className="mb-6">
            <h2 className="text-sm font-semibold text-gray-900 mb-3">用户</h2>
            <div className="space-y-2">
              {userResults.map((u) => (
                <div
                  key={u.id}
                  onClick={() => navigate(`/user/${u.id}`)}
                  className="bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 cursor-pointer hover:border-gray-200 transition-colors"
                >
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0">
                    {u.avatarUrl ? (
                      <img
                        src={u.avatarUrl.startsWith('/files/') ? `/api${u.avatarUrl}` : u.avatarUrl}
                        alt={u.username}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span className="text-white font-bold">
                        {u.username?.substring(0, 1).toUpperCase()}
                      </span>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900">{u.username}</p>
                    <p className="text-xs text-gray-400 line-clamp-1">
                      {u.bio || '这个人很懒，什么都没留下...'}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {!searchKeyword && (
          <>
            <div className="mb-6">
              <h1 className="text-xl font-bold text-gray-900 mb-1">分类广场</h1>
              <p className="text-sm text-gray-500">选择感兴趣的分类，发现精彩内容</p>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
              {categories.map((cat) => {
                const IconComp = ICON_MAP[cat.icon] || GraduationCap
                const colors = COLOR_MAP[cat.color] || DEFAULT_COLORS
                const blockCount =
                  cat.type === 'school' ? schools.length : cat.subCategories?.length || 0
                const blockLabel = CATEGORY_UNIT_MAP[cat.id] || '个板块'
                return (
                  <div
                    key={cat.id}
                    onClick={() => handleCategoryClick(cat)}
                    className="bg-white rounded-2xl border border-gray-100 p-4 cursor-pointer hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group"
                  >
                    <div
                      className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${colors.bg} flex items-center justify-center mb-3 shadow-sm group-hover:scale-110 transition-transform`}
                    >
                      <IconComp className="w-6 h-6 text-white" />
                    </div>
                    <h3 className="font-semibold text-gray-900 text-sm mb-1">{cat.name}</h3>
                    <p className="text-xs text-gray-400 line-clamp-1">{cat.description}</p>
                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-xs text-gray-500">
                        内含{blockCount}
                        {blockLabel}
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>
          </>
        )}

        {searchKeyword && userResults.length === 0 && (
          <div className="text-center py-16">
            <p className="text-gray-400 text-sm">未找到匹配的用户</p>
          </div>
        )}
      </div>

      <NavBar />
    </div>
  )
}
