import { AuthProvider } from './context/AuthContext'
import Router from './router'
import ToastContainer from './components/common/Toast'
import NavigationProgress from './components/common/NavigationProgress'
import ErrorBoundary from './components/common/ErrorBoundary'
import { initTheme } from './stores'

initTheme()

export default function App() {
  return (
    <ErrorBoundary level="root">
      <AuthProvider>
        <NavigationProgress />
        <Router />
        <ToastContainer />
      </AuthProvider>
    </ErrorBoundary>
  )
}
