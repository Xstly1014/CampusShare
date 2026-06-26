import { useState } from 'react'
import { User, Lock, Eye, EyeOff } from 'lucide-react'

interface LoginFormProps {
  onLogin: (account: string, password: string, rememberMe: boolean) => void
  onSwitchToRegister: () => void
  onSwitchToForgotPassword: () => void
  loading?: boolean
}

export default function LoginForm({
  onLogin,
  onSwitchToRegister,
  onSwitchToForgotPassword,
  loading = false,
}: LoginFormProps) {
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (account && password) {
      onLogin(account, password, rememberMe)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="text-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">欢迎回来</h1>
        <p className="text-gray-600 text-sm">登录到CampusShare</p>
      </div>

      <div className="space-y-3">
        <div className="relative">
          <div className="absolute left-3 top-1/2 -translate-y-1/2">
            <User className="w-5 h-5 text-gray-400" />
          </div>
          <input
            type="text"
            placeholder="用户名/手机号/邮箱"
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
            required
          />
        </div>

        <div className="relative">
          <div className="absolute left-3 top-1/2 -translate-y-1/2">
            <Lock className="w-5 h-5 text-gray-400" />
          </div>
          <input
            type={showPassword ? 'text' : 'password'}
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full pl-10 pr-12 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
            required
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 hover:opacity-70 transition-opacity"
          >
            {showPassword ? (
              <EyeOff className="w-5 h-5 text-gray-400" />
            ) : (
              <Eye className="w-5 h-5 text-gray-400" />
            )}
          </button>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <label className="flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
            className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
          />
          <span className="ml-2 text-sm text-gray-600">记住密码</span>
        </label>
        <button
          type="button"
          onClick={onSwitchToForgotPassword}
          className="text-sm text-blue-600 hover:text-blue-700 transition-colors"
        >
          忘记密码？
        </button>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 active:scale-98 transition-all duration-200 font-medium disabled:bg-blue-400 disabled:cursor-not-allowed"
      >
        {loading ? '登录中...' : '登录'}
      </button>

      <div className="text-center pt-4">
        <p className="text-sm text-gray-600">
          还没有账号？
          <button
            type="button"
            onClick={onSwitchToRegister}
            className="ml-2 text-blue-600 hover:text-blue-700 font-medium transition-colors"
          >
            立即注册
          </button>
        </p>
      </div>
    </form>
  )
}