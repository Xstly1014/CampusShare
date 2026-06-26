import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import AuthPage from '../pages/AuthPage'
import HomePage from '../pages/HomePage'
import WarehousePage from '../pages/WarehousePage'
import ProfilePage from '../pages/ProfilePage'

// 认证守卫组件
function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}

export default function Router() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 公开路由：登录注册页 */}
        <Route path="/" element={<AuthPage />} />

        {/* 认证路由：需要登录才能访问 */}
        <Route
          path="/home"
          element={
            <PrivateRoute>
              <HomePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/warehouse"
          element={
            <PrivateRoute>
              <WarehousePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <PrivateRoute>
              <ProfilePage />
            </PrivateRoute>
          }
        />

        {/* 未匹配路由：重定向到首页 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}