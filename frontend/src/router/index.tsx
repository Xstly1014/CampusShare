import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import AuthPage from '../pages/AuthPage'
import HomePage from '../pages/HomePage'
import WarehousePage from '../pages/WarehousePage'
import ProfilePage from '../pages/ProfilePage'
import SchoolDetailPage from '../pages/SchoolDetailPage'
import PostDetailPage from '../pages/PostDetailPage'
import MyListPage from '../pages/MyListPage'
import SettingsPage from '../pages/SettingsPage'
import UserProfilePage from '../pages/UserProfilePage'
import FollowListPage from '../pages/FollowListPage'
import MessagePage from '../pages/MessagePage'
import NotificationPage from '../pages/NotificationPage'

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
          path="/school/:schoolId"
          element={
            <PrivateRoute>
              <SchoolDetailPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/school/:schoolId/post/:postId"
          element={
            <PrivateRoute>
              <PostDetailPage />
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
        <Route
          path="/profile/:type"
          element={
            <PrivateRoute>
              <MyListPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/settings/:type"
          element={
            <PrivateRoute>
              <SettingsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/user/:userId"
          element={
            <PrivateRoute>
              <UserProfilePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/profile/following"
          element={<PrivateRoute><FollowListPage /></PrivateRoute>}
        />
        <Route
          path="/profile/followers"
          element={<PrivateRoute><FollowListPage /></PrivateRoute>}
        />
        <Route
          path="/profile/mutual"
          element={<PrivateRoute><FollowListPage /></PrivateRoute>}
        />
        <Route
          path="/messages"
          element={<PrivateRoute><MessagePage /></PrivateRoute>}
        />
        <Route
          path="/messages/:userId"
          element={<PrivateRoute><MessagePage /></PrivateRoute>}
        />
        <Route
          path="/notifications"
          element={<PrivateRoute><NotificationPage /></PrivateRoute>}
        />

        {/* 未匹配路由：重定向到首页 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}