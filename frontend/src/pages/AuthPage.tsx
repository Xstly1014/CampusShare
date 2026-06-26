import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoginForm from '../components/auth/LoginForm'
import RegisterForm from '../components/auth/RegisterForm'
import ForgotPasswordForm from '../components/auth/ForgotPasswordForm'

type AuthMode = 'login' | 'register' | 'forgot-password'

export default function AuthPage() {
  const [mode, setMode] = useState<AuthMode>('login')
  const navigate = useNavigate()
  const { login } = useAuth()

  const handleLogin = (account: string, password: string, rememberMe: boolean) => {
    // 模拟登录成功
    const user = {
      id: '1',
      username: account,
      email: account.includes('@') ? account : undefined,
      phone: account.match(/^\d+$/) ? account : undefined,
      avatar: undefined,
    }

    login(user)

    // 如果勾选记住密码，保存到localStorage
    if (rememberMe) {
      localStorage.setItem('campusshare_saved_account', account)
    }

    navigate('/home')
  }

  const handleRegister = (
    registerType: 'phone' | 'email',
    account: string,
    username: string,
    password: string,
    verifyCode: string
  ) => {
    // 模拟注册成功并自动登录
    const user = {
      id: '1',
      username: username,
      email: registerType === 'email' ? account : undefined,
      phone: registerType === 'phone' ? account : undefined,
      avatar: undefined,
    }

    login(user)
    navigate('/home')
  }

  const handleResetPassword = (
    resetType: 'phone' | 'email',
    account: string,
    verifyCode: string,
    newPassword: string
  ) => {
    // 模拟重置密码成功
    alert('密码重置成功！')
    setMode('login')
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-blue-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo和应用名称 */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-600 rounded-full mb-4">
            <span className="text-white text-2xl font-bold">CS</span>
          </div>
          <h2 className="text-3xl font-bold text-gray-900 mb-2">CampusShare</h2>
          <p className="text-gray-600">校园资源共享平台</p>
        </div>

        {/* 表单容器 */}
        <div className="bg-white rounded-2xl shadow-xl p-8 relative">
          {mode === 'login' && (
            <LoginForm
              onLogin={handleLogin}
              onSwitchToRegister={() => setMode('register')}
              onSwitchToForgotPassword={() => setMode('forgot-password')}
            />
          )}

          {mode === 'register' && (
            <RegisterForm
              onRegister={handleRegister}
              onSwitchToLogin={() => setMode('login')}
            />
          )}

          {mode === 'forgot-password' && (
            <ForgotPasswordForm
              onResetPassword={handleResetPassword}
              onSwitchToLogin={() => setMode('login')}
            />
          )}
        </div>

        {/* 底部信息 */}
        <div className="text-center mt-6 text-sm text-gray-600">
          <p>© 2024 CampusShare. 让资源共享更简单</p>
        </div>
      </div>
    </div>
  )
}