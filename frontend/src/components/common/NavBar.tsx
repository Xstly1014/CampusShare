import { Home, Package, User } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'

export default function NavBar() {
  const location = useLocation()
  const navigate = useNavigate()

  const navItems = [
    { path: '/home', icon: Home, label: '首页' },
    { path: '/warehouse', icon: Package, label: '仓库' },
    { path: '/profile', icon: User, label: '个人主页' },
  ]

  const isActive = (path: string) => {
    return location.pathname === path
  }

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 shadow-lg">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex justify-around items-center h-16">
          {navItems.map((item) => {
            const Icon = item.icon
            const active = isActive(item.path)
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex flex-col items-center justify-center py-2 px-3 transition-colors ${
                  active
                    ? 'text-blue-600'
                    : 'text-gray-600 hover:text-blue-600'
                }`}
              >
                <Icon
                  className={`w-6 h-6 ${
                    active ? 'scale-110' : ''
                  } transition-transform duration-200`}
                />
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