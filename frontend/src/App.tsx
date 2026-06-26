import { AuthProvider } from './context/AuthContext'
import Router from './router'
import ToastContainer from './components/common/Toast'

export default function App() {
  return (
    <AuthProvider>
      <Router />
      <ToastContainer />
    </AuthProvider>
  )
}