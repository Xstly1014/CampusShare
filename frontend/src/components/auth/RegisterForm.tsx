import { useState } from 'react'
import { Phone, Mail, Lock, Eye, EyeOff, ChevronLeft, Send } from 'lucide-react'

interface RegisterFormProps {
  onRegister: (
    registerType: 'phone' | 'email',
    account: string,
    username: string,
    password: string,
    verifyCode: string,
  ) => void
  onSwitchToLogin: () => void
  onSendCode?: (account: string, type: string) => Promise<boolean>
  loading?: boolean
}

export default function RegisterForm({
  onRegister,
  onSwitchToLogin,
  onSendCode,
  loading = false,
}: RegisterFormProps) {
  const [registerType, setRegisterType] = useState<'phone' | 'email'>('phone')
  const [account, setAccount] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [verifyCode, setVerifyCode] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [step, setStep] = useState(1)
  const [countdown, setCountdown] = useState(0)

  const handleSendCode = async () => {
    if (countdown === 0 && account) {
      if (onSendCode) {
        const success = await onSendCode(account, registerType)
        if (!success) return
      }
      setCountdown(60)
      const timer = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(timer)
            return 0
          }
          return prev - 1
        })
      }, 1000)
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (step === 2 && account && verifyCode) {
      setStep(3)
    } else if (step === 3 && username && password) {
      onRegister(registerType, account, username, password, verifyCode)
    }
  }

  const handleBack = () => {
    if (step === 2) {
      setStep(1)
      setAccount('')
      setVerifyCode('')
    } else if (step === 3) {
      setStep(2)
      setUsername('')
      setPassword('')
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="text-center mb-6">
        {step > 1 && (
          <button
            type="button"
            onClick={handleBack}
            className="absolute left-0 top-0 text-gray-600 hover:text-gray-900 transition-colors"
          >
            <ChevronLeft className="w-6 h-6" />
          </button>
        )}
        <h1 className="text-2xl font-bold text-gray-900 mb-2">注册账号</h1>
        <p className="text-gray-600 text-sm">加入CampusShare社区</p>
      </div>

      <div className="flex justify-center items-center space-x-2 mb-8">
        <div
          className={`w-8 h-8 rounded-full flex items-center justify-center ${
            step >= 1 ? 'bg-blue-600 text-white' : 'bg-gray-300 text-gray-600'
          }`}
        >
          1
        </div>
        <div className={`w-16 h-1 ${step >= 2 ? 'bg-blue-600' : 'bg-gray-300'}`} />
        <div
          className={`w-8 h-8 rounded-full flex items-center justify-center ${
            step >= 2 ? 'bg-blue-600 text-white' : 'bg-gray-300 text-gray-600'
          }`}
        >
          2
        </div>
        <div className={`w-16 h-1 ${step >= 3 ? 'bg-blue-600' : 'bg-gray-300'}`} />
        <div
          className={`w-8 h-8 rounded-full flex items-center justify-center ${
            step >= 3 ? 'bg-blue-600 text-white' : 'bg-gray-300 text-gray-600'
          }`}
        >
          3
        </div>
      </div>

      {step === 1 && (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => {
              setRegisterType('phone')
              setStep(2)
            }}
            className="w-full py-3 border border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all duration-200 flex items-center justify-center space-x-2"
          >
            <Phone className="w-5 h-5 text-blue-600" />
            <span className="text-gray-900 font-medium">手机号注册</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setRegisterType('email')
              setStep(2)
            }}
            className="w-full py-3 border border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all duration-200 flex items-center justify-center space-x-2"
          >
            <Mail className="w-5 h-5 text-blue-600" />
            <span className="text-gray-900 font-medium">邮箱注册</span>
          </button>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-3">
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2">
              {registerType === 'phone' ? (
                <Phone className="w-5 h-5 text-gray-400" />
              ) : (
                <Mail className="w-5 h-5 text-gray-400" />
              )}
            </div>
            <input
              type={registerType === 'phone' ? 'tel' : 'email'}
              placeholder={registerType === 'phone' ? '手机号码' : '邮箱地址'}
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
              required
            />
          </div>

          <div className="relative">
            <input
              type="text"
              placeholder="验证码"
              value={verifyCode}
              onChange={(e) => setVerifyCode(e.target.value)}
              className="w-full pl-4 pr-24 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
              required
            />
            <button
              type="button"
              onClick={handleSendCode}
              disabled={countdown > 0 || !account}
              className="absolute right-2 top-1/2 -translate-y-1/2 px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center space-x-1"
            >
              <Send className="w-4 h-4" />
              <span>{countdown > 0 ? `${countdown}s` : '发送'}</span>
            </button>
          </div>

          <button
            type="submit"
            disabled={!account || !verifyCode}
            className="w-full py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-all duration-200 font-medium"
          >
            下一步
          </button>
        </div>
      )}

      {step === 3 && (
        <div className="space-y-3">
          <div className="relative">
            <input
              type="text"
              placeholder="用户名"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
              required
            />
          </div>

          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2">
              <Lock className="w-5 h-5 text-gray-400" />
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="设置密码"
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

          <button
            type="submit"
            disabled={!username || !password || loading}
            className="w-full py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-all duration-200 font-medium"
          >
            {loading ? '注册中...' : '注册'}
          </button>
        </div>
      )}

      <div className="text-center pt-4">
        <p className="text-sm text-gray-600">
          已有账号？
          <button
            type="button"
            onClick={onSwitchToLogin}
            className="ml-2 text-blue-600 hover:text-blue-700 font-medium transition-colors"
          >
            立即登录
          </button>
        </p>
      </div>
    </form>
  )
}
