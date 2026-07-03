import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import ErrorBoundary from '../components/common/ErrorBoundary'
import ScrollToTop from '../components/common/ScrollToTop'
import AuthPage from '../pages/AuthPage'
import HomePage from '../pages/HomePage'
import WarehousePage from '../pages/WarehousePage'
import ProfilePage from '../pages/ProfilePage'
import SchoolDetailPage from '../pages/SchoolDetailPage'
import PostDetailPage from '../pages/PostDetailPage'
import CategoryDetailPage from '../pages/CategoryDetailPage'
import MyListPage from '../pages/MyListPage'
import SettingsPage from '../pages/SettingsPage'
import UserProfilePage from '../pages/UserProfilePage'
import FollowListPage from '../pages/FollowListPage'
import MessagePage from '../pages/MessagePage'
import NotificationPage from '../pages/NotificationPage'
import NotificationBasketPage from '../pages/NotificationBasketPage'
import CreatorVerificationPage from '../pages/CreatorVerificationPage'
import AdminCreatorPage from '../pages/AdminCreatorPage'
import AgentPage from '../pages/AgentPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}

function withPageBoundary(page: React.ReactNode) {
  return <ErrorBoundary level="page">{page}</ErrorBoundary>
}

export default function Router() {
  return (
    <BrowserRouter>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<AuthPage />} />

        <Route
          path="/home"
          element={<PrivateRoute>{withPageBoundary(<HomePage />)}</PrivateRoute>}
        />
        <Route
          path="/agent"
          element={<PrivateRoute>{withPageBoundary(<AgentPage />)}</PrivateRoute>}
        />
        <Route
          path="/school/:schoolId"
          element={<PrivateRoute>{withPageBoundary(<SchoolDetailPage />)}</PrivateRoute>}
        />
        <Route
          path="/school/:schoolId/post/:postId"
          element={<PrivateRoute>{withPageBoundary(<PostDetailPage />)}</PrivateRoute>}
        />
        <Route
          path="/warehouse"
          element={<PrivateRoute>{withPageBoundary(<WarehousePage />)}</PrivateRoute>}
        />
        <Route
          path="/profile"
          element={<PrivateRoute>{withPageBoundary(<ProfilePage />)}</PrivateRoute>}
        />
        <Route
          path="/profile/:type"
          element={<PrivateRoute>{withPageBoundary(<MyListPage />)}</PrivateRoute>}
        />
        <Route
          path="/settings/:type"
          element={<PrivateRoute>{withPageBoundary(<SettingsPage />)}</PrivateRoute>}
        />
        <Route
          path="/user/:userId"
          element={<PrivateRoute>{withPageBoundary(<UserProfilePage />)}</PrivateRoute>}
        />
        <Route
          path="/profile/following"
          element={<PrivateRoute>{withPageBoundary(<FollowListPage />)}</PrivateRoute>}
        />
        <Route
          path="/profile/followers"
          element={<PrivateRoute>{withPageBoundary(<FollowListPage />)}</PrivateRoute>}
        />
        <Route
          path="/profile/mutual"
          element={<PrivateRoute>{withPageBoundary(<FollowListPage />)}</PrivateRoute>}
        />
        <Route
          path="/messages"
          element={<PrivateRoute>{withPageBoundary(<MessagePage />)}</PrivateRoute>}
        />
        <Route
          path="/messages/:userId"
          element={<PrivateRoute>{withPageBoundary(<MessagePage />)}</PrivateRoute>}
        />
        <Route
          path="/notifications"
          element={<PrivateRoute>{withPageBoundary(<NotificationPage />)}</PrivateRoute>}
        />
        <Route
          path="/notifications/:basketType"
          element={<PrivateRoute>{withPageBoundary(<NotificationBasketPage />)}</PrivateRoute>}
        />
        <Route
          path="/creator-verification"
          element={<PrivateRoute>{withPageBoundary(<CreatorVerificationPage />)}</PrivateRoute>}
        />
        <Route
          path="/admin/creator"
          element={<PrivateRoute>{withPageBoundary(<AdminCreatorPage />)}</PrivateRoute>}
        />
        <Route
          path="/category/:categoryId"
          element={<PrivateRoute>{withPageBoundary(<CategoryDetailPage />)}</PrivateRoute>}
        />
        <Route
          path="/category/:categoryId/post/:postId"
          element={<PrivateRoute>{withPageBoundary(<PostDetailPage />)}</PrivateRoute>}
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
