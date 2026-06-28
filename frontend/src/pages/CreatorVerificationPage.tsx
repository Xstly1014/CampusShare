import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, Award, CheckCircle, Clock, XCircle, Check, AlertCircle } from 'lucide-react'
import { creatorApi, CreatorStats, CreatorStatus } from '../services/api'
import { toast } from '../stores/toastStore'

export default function CreatorVerificationPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<CreatorStats | null>(null)
  const [status, setStatus] = useState<CreatorStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [realName, setRealName] = useState('')
  const [idCard, setIdCard] = useState('')

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, statusRes] = await Promise.all([
          creatorApi.getStats(),
          creatorApi.getStatus(),
        ])
        setStats(statsRes.data)
        setStatus(statusRes.data)
      } catch {
        toast.error('加载失败')
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  const handleSubmit = async () => {
    if (!realName.trim()) {
      toast.error('请输入真实姓名')
      return
    }
    if (!idCard.trim() || !/^\d{17}[\dXx]$/.test(idCard.trim())) {
      toast.error('请输入正确的身份证号')
      return
    }
    if (!stats?.meetsRequirements) {
      toast.error('暂不满足认证条件')
      return
    }
    setSubmitting(true)
    try {
      await creatorApi.apply({
        realName: realName.trim(),
        idCard: idCard.trim().toUpperCase(),
      })
      toast.success('申请已提交，请等待审核')
      const statusRes = await creatorApi.getStatus()
      setStatus(statusRes.data)
    } catch (err) {
      toast.error((err as Error).message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    )
  }

  const renderStatus = () => {
    if (!status || status.status === 'NONE') return null
    if (status.status === 'APPROVED') {
      return (
        <div className="bg-gradient-to-r from-amber-50 to-yellow-50 border border-amber-200 rounded-2xl p-5 flex items-center gap-4">
          <div className="w-14 h-14 bg-gradient-to-br from-amber-400 to-yellow-500 rounded-2xl flex items-center justify-center flex-shrink-0">
            <CheckCircle className="w-7 h-7 text-white" />
          </div>
          <div>
            <p className="text-base font-bold text-amber-900">已认证创作者</p>
            <p className="text-sm text-amber-600 mt-0.5">恭喜你已通过创作者认证，享受专属权益</p>
          </div>
        </div>
      )
    }
    if (status.status === 'PENDING') {
      return (
        <div className="bg-blue-50 border border-blue-200 rounded-2xl p-5 flex items-center gap-4">
          <div className="w-14 h-14 bg-blue-100 rounded-2xl flex items-center justify-center flex-shrink-0">
            <Clock className="w-7 h-7 text-blue-600 animate-pulse" />
          </div>
          <div>
            <p className="text-base font-bold text-blue-900">审核中</p>
            <p className="text-sm text-blue-600 mt-0.5">你的申请已提交，管理员将在3个工作日内审核</p>
            <p className="text-xs text-blue-400 mt-1">申请数据：获赞 {status.totalLikes} · 帖子 {status.totalPosts}</p>
          </div>
        </div>
      )
    }
    if (status.status === 'REJECTED') {
      return (
        <div className="bg-red-50 border border-red-200 rounded-2xl p-5 flex items-start gap-4">
          <div className="w-14 h-14 bg-red-100 rounded-2xl flex items-center justify-center flex-shrink-0">
            <XCircle className="w-7 h-7 text-red-500" />
          </div>
          <div>
            <p className="text-base font-bold text-red-900">认证被驳回</p>
            <p className="text-sm text-red-600 mt-0.5">驳回原因：{status.rejectReason || '资料不符合要求'}</p>
            <p className="text-xs text-red-400 mt-1">请修改资料后重新申请</p>
          </div>
        </div>
      )
    }
    return null
  }

  const canApply = stats?.meetsRequirements && (!status || status.status === 'NONE' || status.status === 'REJECTED')

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <span className="text-sm font-medium text-gray-900">创作者认证</span>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4 space-y-4">
        <div className="bg-gradient-to-r from-amber-500 to-orange-500 rounded-2xl p-5 text-white">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-12 h-12 bg-white/20 rounded-xl flex items-center justify-center">
              <Award className="w-6 h-6 text-white" />
            </div>
            <div>
              <p className="text-lg font-bold">认证创作者</p>
              <p className="text-sm text-white/80">申请认证，享受专属激励奖励</p>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2 mt-4">
            <div className="bg-white/15 rounded-xl p-3">
              <p className="text-xs text-white/70">专属标识</p>
              <p className="text-sm font-medium mt-0.5">头像V标识</p>
            </div>
            <div className="bg-white/15 rounded-xl p-3">
              <p className="text-xs text-white/70">流量扶持</p>
              <p className="text-sm font-medium mt-0.5">优先推荐</p>
            </div>
            <div className="bg-white/15 rounded-xl p-3">
              <p className="text-xs text-white/70">收益激励</p>
              <p className="text-sm font-medium mt-0.5">创作奖励</p>
            </div>
            <div className="bg-white/15 rounded-xl p-3">
              <p className="text-xs text-white/70">更多权益</p>
              <p className="text-sm font-medium mt-0.5">敬请期待</p>
            </div>
          </div>
        </div>

        {renderStatus()}

        {stats && (
          <div className="bg-white rounded-2xl border border-gray-100 p-5">
            <h3 className="text-sm font-bold text-gray-900 mb-4">认证条件</h3>
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${stats.totalLikes >= stats.requiredLikes ? 'bg-green-50' : 'bg-gray-100'}`}>
                    {stats.totalLikes >= stats.requiredLikes ? (
                      <Check className="w-4 h-4 text-green-600" />
                    ) : (
                      <AlertCircle className="w-4 h-4 text-gray-400" />
                    )}
                  </div>
                  <div>
                    <p className="text-sm text-gray-700">总获赞数 ≥ {stats.requiredLikes.toLocaleString()}</p>
                    <p className="text-xs text-gray-400">当前：{stats.totalLikes.toLocaleString()}</p>
                  </div>
                </div>
                {stats.totalLikes < stats.requiredLikes && (
                  <span className="text-xs text-orange-500">还差 {(stats.requiredLikes - stats.totalLikes).toLocaleString()}</span>
                )}
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${stats.totalPosts >= stats.requiredPosts ? 'bg-green-50' : 'bg-gray-100'}`}>
                    {stats.totalPosts >= stats.requiredPosts ? (
                      <Check className="w-4 h-4 text-green-600" />
                    ) : (
                      <AlertCircle className="w-4 h-4 text-gray-400" />
                    )}
                  </div>
                  <div>
                    <p className="text-sm text-gray-700">发布帖子 ≥ {stats.requiredPosts} 篇</p>
                    <p className="text-xs text-gray-400">当前：{stats.totalPosts} 篇</p>
                  </div>
                </div>
                {stats.totalPosts < stats.requiredPosts && (
                  <span className="text-xs text-orange-500">还差 {stats.requiredPosts - stats.totalPosts} 篇</span>
                )}
              </div>
            </div>
          </div>
        )}

        {canApply && (
          <div className="bg-white rounded-2xl border border-gray-100 p-5">
            <h3 className="text-sm font-bold text-gray-900 mb-4">实名认证</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">真实姓名</label>
                <input
                  type="text"
                  value={realName}
                  onChange={(e) => setRealName(e.target.value)}
                  placeholder="请输入身份证上的真实姓名"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">身份证号</label>
                <input
                  type="text"
                  value={idCard}
                  onChange={(e) => setIdCard(e.target.value)}
                  placeholder="请输入18位身份证号"
                  maxLength={18}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
                />
              </div>
              <div className="bg-blue-50 rounded-xl p-3 flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-blue-500 flex-shrink-0 mt-0.5" />
                <p className="text-xs text-blue-600 leading-relaxed">
                  实名认证信息仅用于创作者身份审核，我们将严格保密您的个人信息，不会用于其他用途。
                </p>
              </div>
              <button
                onClick={handleSubmit}
                disabled={submitting || !stats?.meetsRequirements}
                className="w-full py-3 bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-xl text-sm font-medium hover:from-amber-600 hover:to-orange-600 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {submitting ? '提交中...' : '提交认证申请'}
              </button>
            </div>
          </div>
        )}

        {!stats?.meetsRequirements && status?.status !== 'PENDING' && status?.status !== 'APPROVED' && (
          <div className="bg-gray-50 rounded-2xl border border-gray-100 p-5 text-center">
            <p className="text-sm text-gray-500">继续努力，达成条件后即可申请认证</p>
            <button
              onClick={() => navigate('/home')}
              className="mt-3 px-6 py-2 bg-blue-600 text-white rounded-full text-sm font-medium hover:bg-blue-700 transition-colors"
            >
              去发布内容
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
