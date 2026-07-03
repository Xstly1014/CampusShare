import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoginForm from '../components/auth/LoginForm'
import RegisterForm from '../components/auth/RegisterForm'
import ForgotPasswordForm from '../components/auth/ForgotPasswordForm'
import { toast } from '../stores/toastStore'

type AuthMode = 'login' | 'register' | 'forgot-password'

export default function AuthPage() {
  const [mode, setMode] = useState<AuthMode>('login')
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const { login, register, sendCode, resetPassword, loading } = useAuth()

  const handleLogin = async (account: string, password: string, rememberMe: boolean) => {
    setError('')
    try {
      await login(account, password)
      if (rememberMe) {
        localStorage.setItem('campusshare_saved_account', account)
      }
      navigate('/home')
    } catch (e: any) {
      setError(e.message || '登录失败')
    }
  }

  const handleRegister = async (
    registerType: 'phone' | 'email',
    account: string,
    username: string,
    password: string,
    verifyCode: string,
  ) => {
    setError('')
    try {
      await register({ registerType, account, username, password, verifyCode })
      navigate('/home')
    } catch (e: any) {
      setError(e.message || '注册失败')
    }
  }

  const handleResetPassword = async (
    _resetType: 'phone' | 'email',
    account: string,
    verifyCode: string,
    newPassword: string,
  ) => {
    setError('')
    try {
      await resetPassword(account, verifyCode, newPassword)
      toast.success('密码重置成功！')
      setMode('login')
    } catch (e: any) {
      setError(e.message || '重置密码失败')
    }
  }

  const handleSendCode = async (account: string, type = 'phone') => {
    try {
      await sendCode(account, type)
      return true
    } catch (e: any) {
      setError(e.message || '发送验证码失败')
      return false
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-blue-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-600 rounded-full mb-4">
            <span className="text-white text-2xl font-bold">CS</span>
          </div>
          <h2 className="text-3xl font-bold text-gray-900 mb-2">CampusShare</h2>
          <p className="text-gray-600">校园资源共享平台</p>
        </div>

        <div className="bg-white rounded-2xl shadow-xl p-8 relative">
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg">
              {error}
            </div>
          )}

          {mode === 'login' && (
            <LoginForm
              onLogin={handleLogin}
              onSwitchToRegister={() => {
                setMode('register')
                setError('')
              }}
              onSwitchToForgotPassword={() => {
                setMode('forgot-password')
                setError('')
              }}
              loading={loading}
            />
          )}

          {mode === 'register' && (
            <RegisterForm
              onRegister={handleRegister}
              onSwitchToLogin={() => {
                setMode('login')
                setError('')
              }}
              onSendCode={handleSendCode}
              loading={loading}
            />
          )}

          {mode === 'forgot-password' && (
            <ForgotPasswordForm
              onResetPassword={handleResetPassword}
              onSwitchToLogin={() => {
                setMode('login')
                setError('')
              }}
              onSendCode={handleSendCode}
              loading={loading}
            />
          )}
        </div>

        <div className="text-center mt-6 text-sm text-gray-600">
          <p>© 2024 CampusShare. 让资源共享更简单</p>
        </div>
      </div>
    </div>
  )
}
