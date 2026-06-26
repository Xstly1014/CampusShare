import { useState } from 'react'
import { Phone, Mail, Send, Lock, Eye, EyeOff, ChevronLeft } from 'lucide-react'

interface ForgotPasswordFormProps {
  onResetPassword: (
    resetType: 'phone' | 'email',
    account: string,
    verifyCode: string,
    newPassword: string
  ) => void
  onSwitchToLogin: () => void
  onSendCode?: (account: string, type: string) => Promise<boolean>
  loading?: boolean
}

export default function ForgotPasswordForm({
  onResetPassword,
  onSwitchToLogin,
  onSendCode,
  loading = false,
}: ForgotPasswordFormProps) {
  const [resetType, setResetType] = useState<'phone' | 'email'>('phone')
  const [account, setAccount] = useState('')
  const [verifyCode, setVerifyCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [step, setStep] = useState(1)
  const [countdown, setCountdown] = useState(0)

  const handleSendCode = async () => {
    if (countdown === 0 && account) {
      if (onSendCode) {
        const success = await onSendCode(account, resetType)
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
    } else if (step === 3 && newPassword) {
      onResetPassword(resetType, account, verifyCode, newPassword)
    }
  }

  const handleBack = () => {
    if (step === 2) {
      setStep(1)
      setAccount('')
      setVerifyCode('')
    } else if (step === 3) {
      setStep(2)
      setNewPassword('')
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
        <h1 className="text-2xl font-bold text-gray-900 mb-2">重置密码</h1>
        <p className="text-gray-600 text-sm">找回您的账号密码</p>
      </div>

      {step === 1 && (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => {
              setResetType('phone')
              setStep(2)
            }}
            className="w-full py-3 border border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all duration-200 flex items-center justify-center space-x-2"
          >
            <Phone className="w-5 h-5 text-blue-600" />
            <span className="text-gray-900 font-medium">手机号重置</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setResetType('email')
              setStep(2)
            }}
            className="w-full py-3 border border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all duration-200 flex items-center justify-center space-x-2"
          >
            <Mail className="w-5 h-5 text-blue-600" />
            <span className="text-gray-900 font-medium">邮箱重置</span>
          </button>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-3">
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2">
              {resetType === 'phone' ? (
                <Phone className="w-5 h-5 text-gray-400" />
              ) : (
                <Mail className="w-5 h-5 text-gray-400" />
              )}
            </div>
            <input
              type={resetType === 'phone' ? 'tel' : 'email'}
              placeholder={resetType === 'phone' ? '手机号码' : '邮箱地址'}
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
            <div className="absolute left-3 top-1/2 -translate-y-1/2">
              <Lock className="w-5 h-5 text-gray-400" />
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="设置新密码"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
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
            disabled={!newPassword || loading}
            className="w-full py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-all duration-200 font-medium"
          >
            {loading ? '重置中...' : '重置密码'}
          </button>
        </div>
      )}

      <div className="text-center pt-4">
        <button
          type="button"
          onClick={onSwitchToLogin}
          className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors"
        >
          返回登录
        </button>
      </div>
    </form>
  )
}
