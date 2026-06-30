import { useParams, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { ChevronLeft, ChevronRight, Shield, Settings, HelpCircle, User, Mail, Phone, Lock, Eye, Bell, Globe, Send } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { toast } from '../stores/toastStore'
import { userApi, authApi } from '../services/api'
import type { PrivacySettings, NotificationSettings } from '../services/user'

type SettingsType = 'account' | 'privacy' | 'general' | 'help'

const configMap: Record<SettingsType, { title: string; icon: React.ReactNode }> = {
  account: { title: '账号与安全', icon: <User className="w-5 h-5" /> },
  privacy: { title: '隐私设置', icon: <Shield className="w-5 h-5" /> },
  general: { title: '通用设置', icon: <Settings className="w-5 h-5" /> },
  help: { title: '帮助与反馈', icon: <HelpCircle className="w-5 h-5" /> },
}

function ToggleRow({ icon, label, defaultChecked, checked, onChange, disabled }: {
  icon: React.ReactNode
  label: string
  defaultChecked?: boolean
  checked?: boolean
  onChange?: (checked: boolean) => void
  disabled?: boolean
}) {
  const [internalChecked, setInternalChecked] = useState(defaultChecked ?? false)
  const isControlled = checked !== undefined
  const currentChecked = isControlled ? checked : internalChecked

  const handleToggle = () => {
    if (disabled) return
    if (!isControlled) {
      setInternalChecked(!internalChecked)
    }
    onChange?.(!currentChecked)
  }

  return (
    <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50 last:border-0">
      {icon}
      <span className="flex-1 text-sm text-gray-700">{label}</span>
      <button
        onClick={handleToggle}
        disabled={disabled}
        className={`relative w-11 h-6 rounded-full transition-colors ${currentChecked ? 'bg-blue-600' : 'bg-gray-200'} disabled:opacity-40`}
      >
        <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${currentChecked ? 'translate-x-5' : ''}`} />
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

  // Account security state
  const [currentUser, setCurrentUser] = useState(user)
  const [modal, setModal] = useState<'password' | 'email' | 'phone' | null>(null)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  // Bind/rebind state
  const [rebindStep, setRebindStep] = useState<1 | 2>(1) // 1=verify original, 2=set new
  const [originalAccount, setOriginalAccount] = useState('')
  const [originalVerifyCode, setOriginalVerifyCode] = useState('')
  const [newAccount, setNewAccount] = useState('')
  const [newVerifyCode, setNewVerifyCode] = useState('')
  const [sendingCode, setSendingCode] = useState(false)
  const [saving, setSaving] = useState(false)

  // Privacy settings state
  const [privacy, setPrivacy] = useState<PrivacySettings | null>(null)
  const [privacyLoading, setPrivacyLoading] = useState(false)
  const [privacySaving, setPrivacySaving] = useState(false)

  // Notification settings state
  const [notif, setNotif] = useState<NotificationSettings | null>(null)
  const [notifLoading, setNotifLoading] = useState(false)
  const [notifSaving, setNotifSaving] = useState(false)

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await userApi.getMe()
        setCurrentUser(res.data)
      } catch { /* ignore */ }
    }
    if (settingsType === 'account') fetchUser()
  }, [settingsType])

  useEffect(() => {
    const fetchPrivacy = async () => {
      setPrivacyLoading(true)
      try {
        const res = await userApi.getMe()
        const data = res.data
        setPrivacy({
          publicPosts: data.publicPosts ?? true,
          publicStars: data.publicStars ?? false,
          publicLikes: data.publicLikes ?? false,
          publicHistory: data.publicHistory ?? false,
          searchable: data.searchable ?? true,
        })
      } catch {
        /* ignore */
      } finally {
        setPrivacyLoading(false)
      }
    }
    if (settingsType === 'privacy') fetchPrivacy()
  }, [settingsType])

  const handlePrivacyToggle = async (field: keyof PrivacySettings, value: boolean) => {
    if (!privacy) return
    const prev = privacy
    setPrivacy({ ...privacy, [field]: value })
    setPrivacySaving(true)
    try {
      await userApi.updatePrivacy({ [field]: value })
      toast.success('设置已更新')
    } catch {
      setPrivacy(prev)
      toast.error('设置失败')
    } finally {
      setPrivacySaving(false)
    }
  }

  useEffect(() => {
    const fetchNotif = async () => {
      setNotifLoading(true)
      try {
        const res = await userApi.getMe()
        const data = res.data
        setNotif({
          notifyMessages: data.notifyMessages ?? true,
          notifyReplies: data.notifyReplies ?? true,
          notifyLikes: data.notifyLikes ?? false,
        })
      } catch {
        /* ignore */
      } finally {
        setNotifLoading(false)
      }
    }
    if (settingsType === 'general') fetchNotif()
  }, [settingsType])

  const handleNotifToggle = async (field: keyof NotificationSettings, value: boolean) => {
    if (!notif) return
    const prev = notif
    setNotif({ ...notif, [field]: value })
    setNotifSaving(true)
    try {
      await userApi.updateNotificationSettings({ [field]: value })
      toast.success('设置已更新')
    } catch {
      setNotif(prev)
      toast.error('设置失败')
    } finally {
      setNotifSaving(false)
    }
  }

  const handleSendCode = async (account: string, type: 'phone' | 'email') => {
    if (!account.trim()) {
      toast.warning('请输入' + (type === 'phone' ? '手机号' : '邮箱'))
      return
    }
    setSendingCode(true)
    try {
      await authApi.sendCode(account, type)
      toast.success('验证码已发送')
    } catch (err) {
      toast.error((err as Error).message || '发送失败')
    } finally {
      setSendingCode(false)
    }
  }

  const handleChangePassword = async () => {
    if (!oldPassword || !newPassword || !confirmPassword) {
      toast.warning('请填写完整')
      return
    }
    if (newPassword !== confirmPassword) {
      toast.warning('两次输入的密码不一致')
      return
    }
    setSaving(true)
    try {
      await userApi.changePassword({ oldPassword, newPassword, confirmPassword })
      toast.success('密码修改成功')
      setModal(null)
      setOldPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      toast.error((err as Error).message || '修改失败')
    } finally {
      setSaving(false)
    }
  }

  const handleBindEmail = async () => {
    if (!newAccount || !newVerifyCode) {
      toast.warning('请填写完整')
      return
    }
    setSaving(true)
    try {
      const isRebind = currentUser?.email && rebindStep === 2
      const res = await userApi.bindEmail({
        originalAccount: isRebind ? originalAccount : undefined,
        originalVerifyCode: isRebind ? originalVerifyCode : undefined,
        newAccount,
        newVerifyCode,
      })
      setCurrentUser(res.data)
      updateLocalStorageUser(res.data)
      toast.success('邮箱' + (isRebind ? '换绑' : '绑定') + '成功')
      closeModal()
    } catch (err) {
      toast.error((err as Error).message || '绑定失败')
    } finally {
      setSaving(false)
    }
  }

  const handleBindPhone = async () => {
    if (!newAccount || !newVerifyCode) {
      toast.warning('请填写完整')
      return
    }
    setSaving(true)
    try {
      const isRebind = currentUser?.phone && rebindStep === 2
      const res = await userApi.bindPhone({
        originalAccount: isRebind ? originalAccount : undefined,
        originalVerifyCode: isRebind ? originalVerifyCode : undefined,
        newAccount,
        newVerifyCode,
      })
      setCurrentUser(res.data)
      updateLocalStorageUser(res.data)
      toast.success('手机号' + (isRebind ? '换绑' : '绑定') + '成功')
      closeModal()
    } catch (err) {
      toast.error((err as Error).message || '绑定失败')
    } finally {
      setSaving(false)
    }
  }

  const closeModal = () => {
    setModal(null)
    setRebindStep(1)
    setOriginalAccount('')
    setOriginalVerifyCode('')
    setNewAccount('')
    setNewVerifyCode('')
    setOldPassword('')
    setNewPassword('')
    setConfirmPassword('')
  }

  const updateLocalStorageUser = (userData: any) => {
    const saved = sessionStorage.getItem('campusshare_user')
    if (saved) {
      const old = JSON.parse(saved)
      sessionStorage.setItem('campusshare_user', JSON.stringify({ ...old, ...userData }))
    }
  }

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
                <span className="text-sm text-gray-400">{currentUser?.username}</span>
              </div>
              <button onClick={() => { setOriginalAccount(currentUser?.email || ''); setOriginalVerifyCode(''); setNewAccount(''); setNewVerifyCode(''); setRebindStep(currentUser?.email ? 1 : 2); setModal('email') }} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
                <Mail className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-left text-sm text-gray-700">邮箱</span>
                <span className="text-sm text-gray-400">{currentUser?.email || '未绑定'}</span>
                <ChevronRight className="w-4 h-4 text-gray-300" />
              </button>
              <button onClick={() => { setOriginalAccount(currentUser?.phone || ''); setOriginalVerifyCode(''); setNewAccount(''); setNewVerifyCode(''); setRebindStep(currentUser?.phone ? 1 : 2); setModal('phone') }} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors">
                <Phone className="w-4 h-4 text-gray-400" />
                <span className="flex-1 text-left text-sm text-gray-700">手机号</span>
                <span className="text-sm text-gray-400">{currentUser?.phone || '未绑定'}</span>
                <ChevronRight className="w-4 h-4 text-gray-300" />
              </button>
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">安全设置</p></div>
              <button onClick={() => { setOldPassword(''); setNewPassword(''); setModal('password') }} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
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

        {/* 修改密码弹窗 */}
        {modal === 'password' && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={closeModal}>
            <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
              <h3 className="text-base font-bold text-gray-900 mb-4">修改密码</h3>
              <input type="password" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} placeholder="旧密码" className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white mb-3" />
              <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="新密码（6-20位，含字母数字）" className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white mb-3" />
              <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="确认新密码" className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white mb-4" />
              <div className="flex gap-3">
                <button onClick={closeModal} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>
                <button onClick={handleChangePassword} disabled={saving} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40">{saving ? '保存中...' : '确认'}</button>
              </div>
            </div>
          </div>
        )}

        {/* 绑定/换绑邮箱弹窗 */}
        {modal === 'email' && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={closeModal}>
            <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
              {rebindStep === 1 ? (
                <>
                  <h3 className="text-base font-bold text-gray-900 mb-1">验证原邮箱</h3>
                  <p className="text-xs text-gray-400 mb-4">换绑邮箱需先验证原绑定邮箱</p>
                  <p className="text-sm text-gray-500 mb-3 px-1">原邮箱：{originalAccount}</p>
                  <div className="flex gap-2 mb-4">
                    <input type="text" value={originalVerifyCode} onChange={(e) => setOriginalVerifyCode(e.target.value)} placeholder="验证码" className="flex-1 px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white" />
                    <button onClick={() => handleSendCode(originalAccount, 'email')} disabled={sendingCode} className="px-3 py-2.5 bg-gray-100 text-gray-600 rounded-xl text-xs font-medium hover:bg-gray-200 transition-colors disabled:opacity-40 whitespace-nowrap">{sendingCode ? '发送中' : '发送'}</button>
                  </div>
                  <div className="flex gap-3">
                    <button onClick={closeModal} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>
                    <button onClick={() => { if (originalVerifyCode) setRebindStep(2); else toast.warning('请输入验证码') }} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors">下一步</button>
                  </div>
                </>
              ) : (
                <>
                  <h3 className="text-base font-bold text-gray-900 mb-4">{currentUser?.email ? '绑定新邮箱' : '绑定邮箱'}</h3>
                  <input type="text" value={newAccount} onChange={(e) => setNewAccount(e.target.value)} placeholder="新邮箱地址" className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white mb-3" />
                  <div className="flex gap-2 mb-4">
                    <input type="text" value={newVerifyCode} onChange={(e) => setNewVerifyCode(e.target.value)} placeholder="验证码" className="flex-1 px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white" />
                    <button onClick={() => handleSendCode(newAccount, 'email')} disabled={sendingCode} className="px-3 py-2.5 bg-gray-100 text-gray-600 rounded-xl text-xs font-medium hover:bg-gray-200 transition-colors disabled:opacity-40 whitespace-nowrap">{sendingCode ? '发送中' : '发送'}</button>
                  </div>
                  <div className="flex gap-3">
                    {currentUser?.email ? <button onClick={() => setRebindStep(1)} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">上一步</button> : <button onClick={closeModal} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>}
                    <button onClick={handleBindEmail} disabled={saving} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40">{saving ? '保存中...' : '确认'}</button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {/* 绑定/换绑手机号弹窗 */}
        {modal === 'phone' && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={closeModal}>
            <div className="bg-white w-72 rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
              {rebindStep === 1 ? (
                <>
                  <h3 className="text-base font-bold text-gray-900 mb-1">验证原手机号</h3>
                  <p className="text-xs text-gray-400 mb-4">换绑手机号需先验证原绑定手机号</p>
                  <p className="text-sm text-gray-500 mb-3 px-1">原手机号：{originalAccount}</p>
                  <div className="flex gap-2 mb-4">
                    <input type="text" value={originalVerifyCode} onChange={(e) => setOriginalVerifyCode(e.target.value)} placeholder="验证码" className="flex-1 px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white" />
                    <button onClick={() => handleSendCode(originalAccount, 'phone')} disabled={sendingCode} className="px-3 py-2.5 bg-gray-100 text-gray-600 rounded-xl text-xs font-medium hover:bg-gray-200 transition-colors disabled:opacity-40 whitespace-nowrap">{sendingCode ? '发送中' : '发送'}</button>
                  </div>
                  <div className="flex gap-3">
                    <button onClick={closeModal} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>
                    <button onClick={() => { if (originalVerifyCode) setRebindStep(2); else toast.warning('请输入验证码') }} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors">下一步</button>
                  </div>
                </>
              ) : (
                <>
                  <h3 className="text-base font-bold text-gray-900 mb-4">{currentUser?.phone ? '绑定新手机号' : '绑定手机号'}</h3>
                  <input type="text" value={newAccount} onChange={(e) => setNewAccount(e.target.value)} placeholder="新手机号" className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white mb-3" />
                  <div className="flex gap-2 mb-4">
                    <input type="text" value={newVerifyCode} onChange={(e) => setNewVerifyCode(e.target.value)} placeholder="验证码" className="flex-1 px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white" />
                    <button onClick={() => handleSendCode(newAccount, 'phone')} disabled={sendingCode} className="px-3 py-2.5 bg-gray-100 text-gray-600 rounded-xl text-xs font-medium hover:bg-gray-200 transition-colors disabled:opacity-40 whitespace-nowrap">{sendingCode ? '发送中' : '发送'}</button>
                  </div>
                  <div className="flex gap-3">
                    {currentUser?.phone ? <button onClick={() => setRebindStep(1)} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">上一步</button> : <button onClick={closeModal} className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors">取消</button>}
                    <button onClick={handleBindPhone} disabled={saving} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40">{saving ? '保存中...' : '确认'}</button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {settingsType === 'privacy' && (
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">隐私设置</p></div>
            {privacyLoading ? (
              <div className="text-center py-8">
                <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
              </div>
            ) : privacy ? (
              <>
                <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的帖子" checked={privacy.publicPosts} onChange={(v) => handlePrivacyToggle('publicPosts', v)} disabled={privacySaving} />
                <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的收藏" checked={privacy.publicStars} onChange={(v) => handlePrivacyToggle('publicStars', v)} disabled={privacySaving} />
                <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开我的点赞" checked={privacy.publicLikes} onChange={(v) => handlePrivacyToggle('publicLikes', v)} disabled={privacySaving} />
                <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="公开浏览历史" checked={privacy.publicHistory} onChange={(v) => handlePrivacyToggle('publicHistory', v)} disabled={privacySaving} />
                <ToggleRow icon={<Eye className="w-4 h-4 text-gray-400" />} label="允许他人搜索到我" checked={privacy.searchable} onChange={(v) => handlePrivacyToggle('searchable', v)} disabled={privacySaving} />
              </>
            ) : (
              <p className="text-center text-sm text-gray-400 py-8">加载失败</p>
            )}
          </div>
        )}

        {settingsType === 'general' && (
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-50"><p className="text-xs font-medium text-gray-400">通用设置</p></div>
            {notifLoading ? (
              <div className="text-center py-8">
                <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
              </div>
            ) : notif ? (
              <>
                <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="新消息通知" checked={notif.notifyMessages} onChange={(v) => handleNotifToggle('notifyMessages', v)} disabled={notifSaving} />
                <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="帖子回复通知" checked={notif.notifyReplies} onChange={(v) => handleNotifToggle('notifyReplies', v)} disabled={notifSaving} />
                <ToggleRow icon={<Bell className="w-4 h-4 text-gray-400" />} label="点赞收藏通知" checked={notif.notifyLikes} onChange={(v) => handleNotifToggle('notifyLikes', v)} disabled={notifSaving} />
              </>
            ) : (
              <p className="text-center text-sm text-gray-400 py-8">加载失败</p>
            )}
            <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-50">
              <Globe className="w-4 h-4 text-gray-400" />
              <span className="flex-1 text-sm text-gray-700">语言</span>
              <span className="text-sm text-gray-400">简体中文</span>
            </div>
            <div className="flex items-center gap-3 px-4 py-3.5">
              <Settings className="w-4 h-4 text-gray-400" />
              <span className="flex-1 text-sm text-gray-700">清除缓存</span>
              <button onClick={() => { sessionStorage.removeItem('campusshare_user'); toast.success('缓存已清除') }} className="text-sm text-blue-600">清除</button>
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
