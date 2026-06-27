import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import SearchBar from '../components/home/SearchBar'
import SchoolCard from '../components/home/SchoolCard'
import NavBar from '../components/common/NavBar'
import schoolsData from '../data/schools.json'
import { postApi, userApi } from '../services/api'
import { useAuth } from '../context/AuthContext'

interface School {
  id: string
  name: string
  logo: string
  resourceCount: number
}

interface UserResult {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
}

export default function HomePage() {
  const [searchKeyword, setSearchKeyword] = useState('')
  const [schools, setSchools] = useState<School[]>(schoolsData)
  const [userResults, setUserResults] = useState<UserResult[]>([])
  const { user } = useAuth()

  useEffect(() => {
    const fetchCounts = async () => {
      try {
        const res = await postApi.getSchoolPostCounts()
        const counts = res.data || {}
        setSchools(schoolsData.map((s: School) => ({ ...s, resourceCount: counts[s.id] || 0 })))
      } catch {}
    }
    fetchCounts()
  }, [])

  // Search users when keyword changes
  useEffect(() => {
    if (!searchKeyword.trim()) {
      setUserResults([])
      return
    }
    const timer = setTimeout(async () => {
      try {
        const res = await userApi.searchUsers(searchKeyword.trim())
        setUserResults(res.data || [])
      } catch { setUserResults([]) }
    }, 300)
    return () => clearTimeout(timer)
  }, [searchKeyword])

  const filteredSchools = useMemo(() => {
    if (!searchKeyword) return schools
    return schools.filter((s: School) => s.name.toLowerCase().includes(searchKeyword.toLowerCase()))
  }, [searchKeyword, schools])

  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="sticky top-0 bg-white border-b border-gray-100 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3">
          <SearchBar onSearch={setSearchKeyword} />
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* 用户搜索结果 */}
        {searchKeyword && userResults.length > 0 && (
          <div className="mb-6">
            <h2 className="text-sm font-semibold text-gray-900 mb-3">用户</h2>
            <div className="space-y-2">
              {userResults.map((u) => (
                <div key={u.id} onClick={() => navigate(u.id === user?.id ? '/profile' : `/user/${u.id}`)} className="bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 cursor-pointer hover:border-gray-200 transition-colors">
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0">
                    {u.avatarUrl ? <img src={u.avatarUrl.startsWith('/files/') ? `/api${u.avatarUrl}` : u.avatarUrl} alt={u.username} className="w-full h-full object-cover" /> : <span className="text-white font-bold">{u.username?.substring(0, 1).toUpperCase()}</span>}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900">{u.username}</p>
                    <p className="text-xs text-gray-400 line-clamp-1">{u.bio || '这个人很懒，什么都没留下...'}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 学校列表 */}
        <div className="mb-6">
          <h1 className="text-xl font-bold text-gray-900 mb-1">选择学校</h1>
          <p className="text-sm text-gray-500">共收录 {schoolsData.length} 所高校，浏览优质学习资源</p>
        </div>

        {filteredSchools.length > 0 ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {filteredSchools.map((school: School) => (
              <SchoolCard key={school.id} school={school} onClick={() => navigate(`/school/${school.id}`)} />
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <p className="text-gray-400 text-sm">未找到匹配的学校</p>
            {userResults.length === 0 && <p className="text-gray-300 text-xs mt-1">请尝试其他搜索关键词</p>}
          </div>
        )}
      </div>

      <NavBar />
    </div>
  )
}
