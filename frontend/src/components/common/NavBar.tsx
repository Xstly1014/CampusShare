import { useState, useEffect } from 'react'
import { Home, Package, Bell, User } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { notificationApi } from '../../services/api'
import { useAuth } from '../../context/AuthContext'

export default function NavBar() {
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    if (!isAuthenticated) {
      setUnreadCount(0)
      return
    }
    const fetchUnread = async () => {
      try {
        const res = await notificationApi.getUnreadCount()
        setUnreadCount(res.data || 0)
      } catch { /* ignore */ }
    }
    fetchUnread()
    const interval = setInterval(fetchUnread, 30000)
    return () => clearInterval(interval)
  }, [location.pathname, isAuthenticated])

  const navItems = [
    { path: '/home', icon: Home, label: '首页' },
    { path: '/warehouse', icon: Package, label: '仓库' },
    { path: '/notifications', icon: Bell, label: '通知', badge: unreadCount },
    { path: '/profile', icon: User, label: '我的' },
  ]

  const isActive = (path: string) => {
    return location.pathname === path
  }

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 shadow-lg z-30">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex justify-around items-center h-16">
          {navItems.map((item) => {
            const Icon = item.icon
            const active = isActive(item.path)
            const showBadge = item.badge !== undefined && Number(item.badge) > 0 && !active
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex flex-col items-center justify-center py-2 px-3 transition-colors relative ${active
                  ? 'text-blue-600'
                  : 'text-gray-600 hover:text-blue-600'
                  }`}
              >
                <div className="relative">
                  <Icon
                    className={`w-6 h-6 ${active ? 'scale-110' : ''
                      } transition-transform duration-200`}
                  />
                  {showBadge && (
                    <span className="absolute -top-1 -right-1.5 min-w-[16px] h-[16px] bg-red-500 text-white text-[10px] rounded-full flex items-center justify-center px-0.5 font-medium">
                      {item.badge > 99 ? '99+' : item.badge}
                    </span>
                  )}
                </div>
                <span className="text-xs mt-1 font-medium">{item.label}</span>
                {active && (
                  <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-1 bg-blue-600 rounded-t-full" />
                )}
              </button>
            )
          })}
        </div>
      </div>
    </nav>
  )
}
