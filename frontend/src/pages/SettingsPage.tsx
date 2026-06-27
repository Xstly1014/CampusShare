import { useParams, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { ChevronLeft, ChevronRight, Shield, Settings, HelpCircle, User, Mail, Phone, Lock, Eye, Bell, Globe, Send } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { toast } from '../stores/toastStore'

type SettingsType = 'account' | 'privacy' | 'general' | 'help'

const configMap: Record<SettingsType, { title: string; icon: React.ReactNode }> = {
  account: { title: '账号与安全', icon: <User className="w-5 h-5" /> },
  privacy: { title: '隐私设置', icon: <Shield className="w-5 h-5" /> },
  general: { title: '通用设置', icon: <Settings className="w-5 h-5" /> },
  help: { title: '帮助与反馈', icon: <HelpCircle className="w-5 h-5" /> },
}

function ToggleRow({ icon, label, defaultChecked }: { icon: React.ReactNode; label: string; defaultChecked: boolean }) {
  const [checked, setChecked] = useState(defaultChecked)
  return (
    <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50 last:border-0">
      {icon}
      <span className="flex-1 text-sm text-gray-700">{label}</span>
      <button
        onClick={() => setChecked(!checked)}
        className={`relative w-11 h-6 rounded-full transition-colors ${checked ? 'bg-blue-600' : 'bg-gray-200'}`}
      >
        <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${checked ? 'translate-x-5' : ''}`} />
      </button>
    </div>
  )
}

export default function SettingsPage() {
  const { type } = useParams<{ type: string }>()
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const settingsType = (type as SettingsType) || 'account'
  const config = configMap[settingsType]

  const [feedback, setFeedback] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false)

  const handleFeedback = () => {
    if (!feedback.trim() || submitting) return
    setSubmitting(true)
    setTimeout(() => {
      toast.success('反馈已提交，感谢您的意见！')
      setFeedback('')
      setSubmitting(false)
    }, 500)
  }

  const faqs = [
    { q: '如何发布帖子？', a: '进入学校社区页面，点击右下角"+"按钮，选择帖子类型并填写内容即可发布。' },
    { q: '如何修改个人资料？', a: '在个人主页点击头像旁的编辑按钮，可修改昵称和个人简介。点击头像可上传新头像。' },
    { q: '帖子可以删除吗？', a: '可以。在帖子详情页，作者可以点击右上角删除按钮删除自己的帖子。' },
    { q: '评论可以被删除吗？', a: '评论作者和帖子作者都可以删除评论。评论作者删除后，该评论从"我的回复"中移除。' },
    { q: '忘记密码怎么办？', a: '在登录页面点击"忘记密码"，通过手机号或邮箱验证码重置密码。' },
  ]

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate('/profile')} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <span className="text-gray-700">{config.icon}</span>
            <span className="text-sm font-medium text-gray-900">{config.title}</span>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        {settingsType === 'account' && (
          <div className="space-y-3">
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">账号信息</p></div>
              <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50">
                <User className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-sm text-gray-700">用户名</span>
                <span className="text-sm text-gray-400">{user?.username}</span>
              </div>
              <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50">
                <Mail className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-sm text-gray-700">邮箱</span>
                <span className="text-sm text-gray-400">{user?.email || '未绑定'}</span>
              </div>
              <div className="flex items-center gap-3 px-4 py-3.5">
                <Phone className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-sm text-gray-700">手机号</span>
                <span className="text-sm text-gray-400">{user?.phone || '未绑定'}</span>
              </div>
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">安全设置</p></div>
              <button className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
                <Lock className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-left text-sm text-gray-700">修改密码</span>
                <ChevronRight className="w-4 h-4 text-gray-300" />
              </button>
              <button onClick={() => setShowLogoutConfirm(true)} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-red-50 transition-colors">
                <Lock className="w-4 h-4 text-red-400" />
                <span className="flex-1 text-left text-sm text-red-500">退出登录</span>
                <ChevronRight className="w-4 h-4 text-gray-300" />
              </button>
            </div>
          </div>
        )}

        {settingsType === 'privacy' && (
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">隐私设置</p></div>
            <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的帖子" defaultChecked={true} />
            <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的收藏" defaultChecked={false} />
            <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的点赞" defaultChecked={false} />
            <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开浏览历史" defaultChecked={false} />
            <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="允许他人搜索到我" defaultChecked={true} />
          </div>
        )}

        {settingsType === 'general' && (
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">通用设置</p></div>
            <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="新消息通知" defaultChecked={true} />
            <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="帖子回复通知" defaultChecked={true} />
            <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="点赞收藏通知" defaultChecked={false} />
            <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50">
              <Globe className="w-4 h-4 text-gray-400" />
              <span className="flex-1 text-sm text-gray-700">语言</span>
              <span className="text-sm text-gray-400">简体中文</span>
            </div>
            <div className="flex items-center gap-3 px-4 py-3.5">
              <Settings className="w-4 h-4 text-gray-400" />
              <span className="flex-1 text-sm text-gray-700">清除缓存</span>
              <button onClick={() => { localStorage.removeItem('campusshare_user'); toast.success('缓存已清除') }} className="text-sm text-blue-600">清除</button>
            </div>
          </div>
        )}

        {settingsType === 'help' && (
          <div className="space-y-3">
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">常见问题</p></div>
              {faqs.map((faq, idx) => (
                <div key={idx} className="px-4 py-3.5 border-b border-gray-50 last:border-0">
                  <p className="text-sm font-medium text-gray-900 mb-1">{faq.q}</p>
                  <p className="text-xs text-gray-500 leading-relaxed">{faq.a}</p>
                </div>
              ))}
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">意见反馈</p></div>
              <div className="p-4">
                <textarea
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  rows={4}
                  maxLength={500}
                  placeholder="请描述您遇到的问题或建议..."
                  className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all resize-none"
                />
                <div className="flex items-center justify-between mt-3">
                  <span className="text-xs text-gray-400">{feedback.length}/500</span>
                  <button
                    onClick={handleFeedback}
                    disabled={!feedback.trim() || submitting}
                    className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Send className="w-3.5 h-3.5" />
                    提交反馈
                  </button>
                </div>
              </div>
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 p-4 text-center">
              <p className="text-xs text-gray-400">CampusShare v1.0.0</p>
              <p className="text-xs text-gray-300 mt-1">连接知识，共享未来</p>
            </div>
          </div>
        )}
      </div>

      {showLogoutConfirm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={() => setShowLogoutConfirm(false)}>
          <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-bold text-gray-900 mb-2">退出登录</h3>
            <p className="text-sm text-gray-500 mb-5">确定要退出当前账号吗？</p>
            <div className="flex gap-3">
              <button onClick={() => setShowLogoutConfirm(false)} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>
              <button onClick={() => { logout(); navigate('/') }} className="flex-1 py-2.5 bg-red-500 text-white rounded-xl text-sm font-medium hover:bg-red-600 transition-colors">退出</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
