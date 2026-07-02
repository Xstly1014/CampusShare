import { AuthProvider } from './context/AuthContext'
import Router from './router'
import ToastContainer from './components/common/Toast'
import { initTheme } from './stores'

initTheme()

export default function App() {
  return (
    <AuthProvider>
      <Router />
      <ToastContainer />
    </AuthProvider>
  )
}
