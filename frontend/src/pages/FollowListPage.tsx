import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ChevronLeft, UserPlus, Users, UserCheck, MessageSquare } from 'lucide-react'
import { userApi } from '../services/api'
import { toast } from '../stores/toastStore'

type ListType = 'following' | 'followers' | 'mutual'

interface UserItem {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
}

const configMap: Record<ListType, { title: string; icon: React.ReactNode; fetcher: () => Promise<any> }> = {
  following: { title: '关注', icon: <UserPlus className="w-5 h-5" />, fetcher: userApi.getFollowingList },
  followers: { title: '粉丝', icon: <Users className="w-5 h-5" />, fetcher: userApi.getFollowerList },
  mutual: { title: '互相关注', icon: <UserCheck className="w-5 h-5" />, fetcher: userApi.getMutualList },
}

export default function FollowListPage() {
  const { type } = useParams<{ type: string }>()
  const navigate = useNavigate()

  const listType = (type as ListType) || 'following'
  const config = configMap[listType]

  const [users, setUsers] = useState<UserItem[]>([])
  const [loading, setLoading] = useState(true)

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    try {
      const res = await config.fetcher()
      setUsers(res.data || [])
    } catch {
      toast.error('加载失败')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listType])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate('/profile')} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <span className="text-gray-700">{config.icon}</span>
            <span className="text-sm font-medium text-gray-900">{config.title}</span>
            {users.length > 0 && <span className="text-xs text-gray-400">({users.length})</span>}
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {loading ? (
          <div className="text-center py-16"><div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div></div>
        ) : users.length > 0 ? (
          <div className="space-y-2">
            {users.map((u) => (
              <div key={u.id} onClick={() => navigate(`/user/${u.id}`)} className="bg-white rounded-xl border border-gray-100 p-3 flex items-center gap-3 cursor-pointer hover:border-gray-200 transition-colors">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden flex-shrink-0">
                  {u.avatarUrl ? <img src={u.avatarUrl.startsWith('/files/') ? `/api${u.avatarUrl}` : u.avatarUrl} alt={u.username} className="w-full h-full object-cover" /> : <span className="text-white font-bold">{u.username?.substring(0, 1).toUpperCase()}</span>}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{u.username}</p>
                  <p className="text-xs text-gray-400 line-clamp-1">{u.bio || '这个人很懒，什么都没留下...'}</p>
                </div>
                <button
                  onClick={(e) => { e.stopPropagation(); navigate(`/messages/${u.id}`) }}
                  className="p-2 rounded-full hover:bg-blue-50 text-gray-400 hover:text-blue-600 transition-colors flex-shrink-0"
                  title="私信"
                >
                  <MessageSquare className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">{config.icon}</div>
            <p className="text-gray-400 text-sm">暂无{config.title}</p>
          </div>
        )}
      </div>
    </div>
  )
}
