import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ChevronLeft,
  Award,
  Clock,
  XCircle,
  Check,
  AlertCircle,
  Crown,
  BadgeCheck,
  TrendingUp,
  FileText,
  ThumbsUp,
  Eye,
  Sparkles,
  Shield,
} from 'lucide-react'
import { creatorApi, CreatorStats, CreatorStatus } from '../services/api'
import { toast } from '../stores/toastStore'

const getCreatorLevelInfo = (level?: string) => {
  switch (level) {
    case 'AUTHORITY':
      return {
        name: '权威创作者',
        color: 'text-yellow-500',
        bgColor: 'bg-yellow-50',
        borderColor: 'border-yellow-200',
        gradientFrom: 'from-yellow-400',
        gradientTo: 'to-amber-500',
        progressColor: 'bg-yellow-500',
        icon: 'crown',
      }
    case 'SENIOR':
      return {
        name: '高级创作者',
        color: 'text-orange-500',
        bgColor: 'bg-orange-50',
        borderColor: 'border-orange-200',
        gradientFrom: 'from-orange-400',
        gradientTo: 'to-orange-500',
        progressColor: 'bg-orange-500',
        icon: 'crown-check',
      }
    case 'INTERMEDIATE':
      return {
        name: '中级创作者',
        color: 'text-purple-500',
        bgColor: 'bg-purple-50',
        borderColor: 'border-purple-200',
        gradientFrom: 'from-purple-400',
        gradientTo: 'to-purple-600',
        progressColor: 'bg-purple-500',
        icon: 'check',
      }
    case 'JUNIOR':
      return {
        name: '初级创作者',
        color: 'text-blue-500',
        bgColor: 'bg-blue-50',
        borderColor: 'border-blue-200',
        gradientFrom: 'from-blue-400',
        gradientTo: 'to-blue-600',
        progressColor: 'bg-blue-500',
        icon: 'check',
      }
    default:
      return {
        name: '',
        color: 'text-gray-400',
        bgColor: '',
        borderColor: '',
        gradientFrom: 'from-gray-400',
        gradientTo: 'to-gray-500',
        progressColor: 'bg-gray-400',
        icon: '',
      }
  }
}

function LevelIcon({ level, className }: { level?: string; className?: string }) {
  const info = getCreatorLevelInfo(level)
  if (level === 'AUTHORITY') {
    return <Crown className={`${className} ${info.color}`} fill="currentColor" />
  }
  if (level === 'SENIOR') {
    return (
      <span className="relative inline-flex">
        <Crown className={`${className} ${info.color}`} />
        <BadgeCheck className={`${className} ${info.color} absolute -bottom-1 -right-1 bg-white rounded-full`} style={{ width: '60%', height: '60%' }} />
      </span>
    )
  }
  return <BadgeCheck className={`${className} ${info.color}`} />
}

function formatNumber(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return n.toString()
}

const levelBenefits = [
  {
    level: 'JUNIOR',
    title: '初级创作者',
    color: 'text-blue-500',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-100',
    icon: BadgeCheck,
    benefits: ['认证标识', '帖子优先展示'],
  },
  {
    level: 'INTERMEDIATE',
    title: '中级创作者',
    color: 'text-purple-500',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-100',
    icon: BadgeCheck,
    benefits: ['帖子加精权限', '紫色专属标识', '内容优先推荐'],
  },
  {
    level: 'SENIOR',
    title: '高级创作者',
    color: 'text-orange-500',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-100',
    icon: Crown,
    benefits: ['帖子置顶权限', '橙色专属标识', '个人数据面板', '官方活动优先参与'],
  },
  {
    level: 'AUTHORITY',
    title: '权威创作者',
    color: 'text-yellow-500',
    bgColor: 'bg-yellow-50',
    borderColor: 'border-yellow-200',
    icon: Crown,
    benefits: ['金色权威标识', '全平台官方推荐', '内容审核绿色通道', '专属客服支持'],
  },
]

const upgradeRequirements: Record<string, { posts: number; likes: number; views?: number }> = {
  JUNIOR: { posts: 50, likes: 10000 },
  INTERMEDIATE: { posts: 100, likes: 50000, views: 500000 },
  SENIOR: { posts: 300, likes: 200000, views: 2000000 },
}

export default function CreatorVerificationPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<CreatorStats | null>(null)
  const [status, setStatus] = useState<CreatorStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [submittingAuthority, setSubmittingAuthority] = useState(false)
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

  const refreshData = async () => {
    try {
      const [statsRes, statusRes] = await Promise.all([
        creatorApi.getStats(),
        creatorApi.getStatus(),
      ])
      setStats(statsRes.data)
      setStatus(statusRes.data)
    } catch {}
  }

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
      toast.success('申请已提交，七个工作日内完成审核，请耐心等待')
      await refreshData()
    } catch (err) {
      toast.error((err as Error).message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleApplyAuthority = async () => {
    setSubmittingAuthority(true)
    try {
      await creatorApi.applyAuthority()
      toast.success('权威创作者申请已提交，七个工作日内完成审核，请耐心等待')
      await refreshData()
    } catch (err) {
      toast.error((err as Error).message || '提交失败')
    } finally {
      setSubmittingAuthority(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    )
  }

  const isApproved = status?.status === 'APPROVED' || (status?.creatorLevel && status.creatorLevel !== 'NONE')
  const isPending = status?.status === 'PENDING' || status?.hasPendingApplication
  const isRejected = status?.status === 'REJECTED'
  const currentLevel = status?.creatorLevel || stats?.creatorLevel || 'NONE'
  const levelInfo = getCreatorLevelInfo(currentLevel)
  const isSenior = currentLevel === 'SENIOR'
  const isAuthority = currentLevel === 'AUTHORITY'
  const canApplyAuthority = isSenior && !isPending && !isAuthority

  const renderApprovedView = () => {
    if (!stats) return null

    return (
      <div className="space-y-4">
        <div className={`rounded-2xl p-5 border ${levelInfo.bgColor} ${levelInfo.borderColor}`}>
          <div className="flex items-center gap-4 mb-5">
            <div className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${levelInfo.gradientFrom} ${levelInfo.gradientTo} flex items-center justify-center flex-shrink-0 shadow-lg`}>
              {currentLevel === 'AUTHORITY' ? (
                <Crown className="w-8 h-8 text-white" fill="white" />
              ) : currentLevel === 'SENIOR' ? (
                <Crown className="w-8 h-8 text-white" />
              ) : (
                <BadgeCheck className="w-8 h-8 text-white" />
              )}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <p className={`text-xl font-bold ${levelInfo.color}`}>{levelInfo.name}</p>
                {currentLevel === 'AUTHORITY' && (
                  <span className="px-2 py-0.5 bg-yellow-400 text-yellow-900 text-xs font-bold rounded-full">权威</span>
                )}
              </div>
              <p className="text-sm text-gray-500 mt-1">恭喜你已成为认证创作者</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="bg-white/70 rounded-xl p-3 text-center">
              <FileText className="w-5 h-5 text-blue-500 mx-auto mb-1" />
              <p className="text-lg font-bold text-gray-900">{formatNumber(stats.totalPosts)}</p>
              <p className="text-xs text-gray-400">总发帖</p>
            </div>
            <div className="bg-white/70 rounded-xl p-3 text-center">
              <ThumbsUp className="w-5 h-5 text-red-500 mx-auto mb-1" />
              <p className="text-lg font-bold text-gray-900">{formatNumber(stats.totalLikes)}</p>
              <p className="text-xs text-gray-400">总获赞</p>
            </div>
            <div className="bg-white/70 rounded-xl p-3 text-center">
              <Eye className="w-5 h-5 text-green-500 mx-auto mb-1" />
              <p className="text-lg font-bold text-gray-900">{formatNumber(stats.totalViews)}</p>
              <p className="text-xs text-gray-400">总浏览</p>
            </div>
          </div>
        </div>

        {!isAuthority && stats.nextLevel && (
          <div className="bg-white rounded-2xl border border-gray-100 p-5">
            <div className="flex items-center gap-2 mb-4">
              <TrendingUp className="w-5 h-5 text-gray-600" />
              <h3 className="text-sm font-bold text-gray-900">升级进度</h3>
              <span className="text-xs text-gray-400 ml-auto">
                距{stats.nextLevelName}还差
              </span>
            </div>

            <div className="mb-4">
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs text-gray-500">综合进度</span>
                <span className={`text-xs font-medium ${levelInfo.color}`}>{stats.progressPercent}%</span>
              </div>
              <div className="w-full h-2.5 bg-gray-100 rounded-full overflow-hidden">
                <div
                  className={`h-full ${levelInfo.progressColor} rounded-full transition-all duration-500`}
                  style={{ width: `${Math.min(100, stats.progressPercent)}%` }}
                />
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-gray-400" />
                  <span className="text-sm text-gray-600">发帖数</span>
                </div>
                <span className="text-sm text-gray-500">
                  {stats.totalPosts}
                  {stats.postsToNext > 0 && (
                    <span className="text-orange-500 ml-1">还差 {stats.postsToNext}</span>
                  )}
                  {stats.postsToNext === 0 && <Check className="w-4 h-4 text-green-500 inline ml-1" />}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ThumbsUp className="w-4 h-4 text-gray-400" />
                  <span className="text-sm text-gray-600">获赞数</span>
                </div>
                <span className="text-sm text-gray-500">
                  {formatNumber(stats.totalLikes)}
                  {stats.likesToNext > 0 && (
                    <span className="text-orange-500 ml-1">还差 {formatNumber(stats.likesToNext)}</span>
                  )}
                  {stats.likesToNext === 0 && <Check className="w-4 h-4 text-green-500 inline ml-1" />}
                </span>
              </div>
              {stats.viewsToNext !== undefined && upgradeRequirements[stats.nextLevel]?.views && (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Eye className="w-4 h-4 text-gray-400" />
                    <span className="text-sm text-gray-600">浏览量</span>
                  </div>
                  <span className="text-sm text-gray-500">
                    {formatNumber(stats.totalViews)}
                    {stats.viewsToNext > 0 && (
                      <span className="text-orange-500 ml-1">还差 {formatNumber(stats.viewsToNext)}</span>
                    )}
                    {stats.viewsToNext === 0 && <Check className="w-4 h-4 text-green-500 inline ml-1" />}
                  </span>
                </div>
              )}
            </div>
          </div>
        )}

        {canApplyAuthority && (
          <div className="bg-gradient-to-r from-yellow-50 to-amber-50 rounded-2xl border border-yellow-200 p-5">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-12 h-12 bg-gradient-to-br from-yellow-400 to-amber-500 rounded-xl flex items-center justify-center">
                <Crown className="w-6 h-6 text-white" />
              </div>
              <div className="flex-1">
                <p className="text-base font-bold text-yellow-900">申请权威创作者</p>
                <p className="text-sm text-yellow-600 mt-0.5">你已达到高级创作者等级，可申请权威认证</p>
              </div>
            </div>
            <button
              onClick={handleApplyAuthority}
              disabled={submittingAuthority}
              className="w-full py-3 bg-gradient-to-r from-yellow-400 to-amber-500 text-white rounded-xl text-sm font-medium hover:from-yellow-500 hover:to-amber-600 transition-all disabled:opacity-50"
            >
              {submittingAuthority ? '提交中...' : '申请权威创作者'}
            </button>
            <p className="text-xs text-yellow-600 text-center mt-2">提交后七个工作日内完成审核，请耐心等待</p>
          </div>
        )}

        {isSenior && isPending && !isAuthority && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-2xl p-5 flex items-start gap-4">
            <div className="w-12 h-12 bg-yellow-100 rounded-xl flex items-center justify-center flex-shrink-0">
              <Clock className="w-6 h-6 text-yellow-600 animate-pulse" />
            </div>
            <div>
              <p className="text-base font-bold text-yellow-900">权威认证申请审核中</p>
              <p className="text-sm text-yellow-600 mt-1">我们将在七个工作日内完成审核，请耐心等待</p>
            </div>
          </div>
        )}

        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-5 h-5 text-amber-500" />
            <h3 className="text-sm font-bold text-gray-900">等级权益说明</h3>
          </div>
          <div className="space-y-3">
            {levelBenefits.map((benefit) => {
              const benefitLevelInfo = getCreatorLevelInfo(benefit.level)
              const isCurrentOrAbove =
                (currentLevel === 'AUTHORITY') ||
                (currentLevel === 'SENIOR' && benefit.level !== 'AUTHORITY') ||
                (currentLevel === 'INTERMEDIATE' && (benefit.level === 'JUNIOR' || benefit.level === 'INTERMEDIATE')) ||
                (currentLevel === 'JUNIOR' && benefit.level === 'JUNIOR')
              return (
                <div
                  key={benefit.level}
                  className={`rounded-xl p-4 border ${
                    currentLevel === benefit.level
                      ? `${benefit.bgColor} ${benefit.borderColor}`
                      : 'bg-gray-50 border-gray-100'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-2">
                    <benefit.icon className={`w-5 h-5 ${benefit.color}`} />
                    <span className={`text-sm font-semibold ${benefit.color}`}>{benefit.title}</span>
                    {currentLevel === benefit.level && (
                      <span className="px-1.5 py-0.5 bg-white text-xs font-medium rounded text-gray-500">当前等级</span>
                    )}
                    {isCurrentOrAbove && currentLevel !== benefit.level && (
                      <Check className="w-4 h-4 text-green-500 ml-auto" />
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {benefit.benefits.map((b) => (
                      <span
                        key={b}
                        className={`text-xs px-2 py-1 rounded-lg ${
                          currentLevel === benefit.level
                            ? 'bg-white text-gray-600'
                            : 'bg-white/60 text-gray-400'
                        }`}
                      >
                        {b}
                      </span>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    )
  }

  const renderPendingView = () => (
    <div className="bg-blue-50 border border-blue-200 rounded-2xl p-5 flex items-start gap-4">
      <div className="w-14 h-14 bg-blue-100 rounded-2xl flex items-center justify-center flex-shrink-0">
        <Clock className="w-7 h-7 text-blue-600 animate-pulse" />
      </div>
      <div>
        <p className="text-base font-bold text-blue-900">审核中</p>
        <p className="text-sm text-blue-600 mt-1">您的认证申请已提交，我们将在七个工作日内完成审核，请耐心等待</p>
        {status?.totalLikes !== undefined && status?.totalPosts !== undefined && (
          <p className="text-xs text-blue-400 mt-2">申请数据：获赞 {status.totalLikes?.toLocaleString()} · 帖子 {status.totalPosts}</p>
        )}
      </div>
    </div>
  )

  const renderRejectedView = () => (
    <div className="bg-red-50 border border-red-200 rounded-2xl p-5 flex items-start gap-4">
      <div className="w-14 h-14 bg-red-100 rounded-2xl flex items-center justify-center flex-shrink-0">
        <XCircle className="w-7 h-7 text-red-500" />
      </div>
      <div>
        <p className="text-base font-bold text-red-900">认证被驳回</p>
        <p className="text-sm text-red-600 mt-1">驳回原因：{status?.rejectReason || '资料不符合要求'}</p>
        <p className="text-xs text-red-400 mt-1">请修改资料后重新申请</p>
      </div>
    </div>
  )

  const canApply = stats?.meetsRequirements && (!isPending)

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
              <p className="text-lg font-bold">创作者中心</p>
              <p className="text-sm text-white/80">发布优质内容，享受专属权益</p>
            </div>
          </div>
          {isApproved && (
            <div className="flex items-center gap-2 mt-3 bg-white/15 rounded-xl p-3">
              <LevelIcon level={currentLevel} className="w-6 h-6" />
              <div>
                <p className="text-sm font-medium">{levelInfo.name}</p>
                <p className="text-xs text-white/70">当前等级</p>
              </div>
            </div>
          )}
          {!isApproved && (
            <div className="grid grid-cols-2 gap-2 mt-4">
              <div className="bg-white/15 rounded-xl p-3">
                <p className="text-xs text-white/70">专属标识</p>
                <p className="text-sm font-medium mt-0.5">等级徽章</p>
              </div>
              <div className="bg-white/15 rounded-xl p-3">
                <p className="text-xs text-white/70">流量扶持</p>
                <p className="text-sm font-medium mt-0.5">优先推荐</p>
              </div>
              <div className="bg-white/15 rounded-xl p-3">
                <p className="text-xs text-white/70">等级晋升</p>
                <p className="text-sm font-medium mt-0.5">逐级解锁权益</p>
              </div>
              <div className="bg-white/15 rounded-xl p-3">
                <p className="text-xs text-white/70">权威认证</p>
                <p className="text-sm font-medium mt-0.5">最高荣誉</p>
              </div>
            </div>
          )}
        </div>

        {isApproved ? (
          renderApprovedView()
        ) : (
          <>
            {isPending && renderPendingView()}
            {isRejected && renderRejectedView()}

            {stats && !isApproved && (
              <div className="bg-white rounded-2xl border border-gray-100 p-5">
                <h3 className="text-sm font-bold text-gray-900 mb-4">申请条件</h3>
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
                  <p className="text-xs text-gray-400 text-center">提交后七个工作日内完成审核，请耐心等待</p>
                </div>
              </div>
            )}

            {!stats?.meetsRequirements && !isPending && (
              <div className="bg-gray-50 rounded-2xl border border-gray-100 p-5 text-center">
                <Shield className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                <p className="text-sm text-gray-500">继续努力，达成条件后即可申请认证</p>
                <button
                  onClick={() => navigate('/home')}
                  className="mt-3 px-6 py-2 bg-blue-600 text-white rounded-full text-sm font-medium hover:bg-blue-700 transition-colors"
                >
                  去发布内容
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
