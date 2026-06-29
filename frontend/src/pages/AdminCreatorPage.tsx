import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, Check, X, Clock, CheckCircle, XCircle, Shield, User } from 'lucide-react'
import { creatorApi, CreatorApplication } from '../services/api'
import { toast } from '../stores/toastStore'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/common/NavBar'

type TabType = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL'

export default function AdminCreatorPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [applications, setApplications] = useState<CreatorApplication[]>([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<TabType>('PENDING')
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [verifying, setVerifying] = useState<number | null>(null)
  const [rejectModal, setRejectModal] = useState<{ id: number; visible: boolean }>({ id: 0, visible: false })
  const [rejectReason, setRejectReason] = useState('')

  useEffect(() => {
    if (user && !user.isAdmin) {
      toast.error('无权限访问')
      navigate('/home')
      return
    }
    fetchApplications()
  }, [activeTab, page])

  const fetchApplications = async () => {
    setLoading(true)
    try {
      const params: any = { page, size: 10 }
      if (activeTab !== 'ALL') {
        params.status = activeTab
      }
      const res = await creatorApi.getApplications(params)
      setApplications(res.data.records || [])
      setTotal(res.data.total || 0)
    } catch {
      toast.error('加载申请列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleApprove = async (id: number) => {
    setVerifying(id)
    try {
      await creatorApi.verifyApplication(id, { approved: true })
      toast.success('已通过认证申请')
      fetchApplications()
    } catch (err) {
      toast.error((err as Error).message || '操作失败')
    } finally {
      setVerifying(null)
    }
  }

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      toast.error('请输入驳回原因')
      return
    }
    setVerifying(rejectModal.id)
    try {
      await creatorApi.verifyApplication(rejectModal.id, { approved: false, rejectReason: rejectReason.trim() })
      toast.success('已驳回申请')
      setRejectModal({ id: 0, visible: false })
      setRejectReason('')
      fetchApplications()
    } catch (err) {
      toast.error((err as Error).message || '操作失败')
    } finally {
      setVerifying(null)
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full text-xs font-medium">审核中</span>
      case 'APPROVED':
        return <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded-full text-xs font-medium">已通过</span>
      case 'REJECTED':
        return <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded-full text-xs font-medium">已驳回</span>
      default:
        return null
    }
  }

  const maskIdCard = (idCard: string) => {
    if (!idCard || idCard.length < 10) return idCard
    return idCard.slice(0, 6) + '********' + idCard.slice(-4)
  }

  const totalPages = Math.ceil(total / 10)

  const tabs: { key: TabType; label: string }[] = [
    { key: 'PENDING', label: '待审核' },
    { key: 'APPROVED', label: '已通过' },
    { key: 'REJECTED', label: '已驳回' },
    { key: 'ALL', label: '全部' },
  ]

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <div className="bg-white border-b border-gray-100 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-1.5 -ml-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-amber-500" />
            <span className="text-sm font-medium text-gray-900">创作者认证审核</span>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-4">
        <div className="bg-white rounded-2xl border border-gray-100 mb-4 overflow-hidden">
          <div className="flex border-b border-gray-100">
            {tabs.map(tab => (
              <button
                key={tab.key}
                onClick={() => { setActiveTab(tab.key); setPage(1) }}
                className={`flex-1 py-3 text-sm font-medium transition-colors relative ${
                  activeTab === tab.key
                    ? 'text-blue-600'
                    : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                {tab.label}
                {activeTab === tab.key && (
                  <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-0.5 bg-blue-600 rounded-full" />
                )}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
          </div>
        ) : applications.length === 0 ? (
          <div className="bg-white rounded-2xl border border-gray-100 p-12 text-center">
            <Clock className="w-12 h-12 text-gray-300 mx-auto mb-3" />
            <p className="text-sm text-gray-500">暂无{activeTab === 'PENDING' ? '待审核' : ''}申请</p>
          </div>
        ) : (
          <div className="space-y-3">
            {applications.map(app => (
              <div key={app.id} className="bg-white rounded-2xl border border-gray-100 p-4">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center flex-shrink-0 overflow-hidden">
                      {app.avatarUrl ? (
                        <img src={app.avatarUrl.startsWith('/files/') ? `/api${app.avatarUrl}` : app.avatarUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        <span className="text-white font-medium">{app.username?.substring(0, 1).toUpperCase()}</span>
                      )}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">{app.username}</span>
                        {getStatusBadge(app.status)}
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5">申请时间：{app.applyTime ? new Date(app.applyTime).toLocaleString('zh-CN') : '-'}</p>
                    </div>
                  </div>
                </div>

                <div className="bg-gray-50 rounded-xl p-3 space-y-2 mb-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">真实姓名</span>
                    <span className="text-gray-900 font-medium">{app.realName}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">身份证号</span>
                    <span className="text-gray-900 font-mono text-xs">{maskIdCard(app.idCard)}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">总获赞数</span>
                    <span className="text-gray-900 font-medium">{app.totalLikes?.toLocaleString()}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">发帖数</span>
                    <span className="text-gray-900 font-medium">{app.totalPosts} 篇</span>
                  </div>
                  {app.status === 'REJECTED' && app.rejectReason && (
                    <div className="flex items-start justify-between text-sm pt-2 border-t border-gray-200">
                      <span className="text-gray-500">驳回原因</span>
                      <span className="text-red-600 text-right max-w-[60%]">{app.rejectReason}</span>
                    </div>
                  )}
                  {app.status === 'APPROVED' && app.reviewTime && (
                    <div className="flex items-center justify-between text-sm pt-2 border-t border-gray-200">
                      <span className="text-gray-500">审核时间</span>
                      <span className="text-green-600">{new Date(app.reviewTime).toLocaleString('zh-CN')}</span>
                    </div>
                  )}
                </div>

                {app.status === 'PENDING' && (
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApprove(app.id)}
                      disabled={verifying === app.id}
                      className="flex-1 flex items-center justify-center gap-1.5 py-2.5 bg-green-500 hover:bg-green-600 text-white rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
                    >
                      <Check className="w-4 h-4" />
                      通过
                    </button>
                    <button
                      onClick={() => { setRejectModal({ id: app.id, visible: true }); setRejectReason('') }}
                      disabled={verifying === app.id}
                      className="flex-1 flex items-center justify-center gap-1.5 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
                    >
                      <X className="w-4 h-4" />
                      驳回
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 mt-6">
            <button
              onClick={() => setPage(p => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-4 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
            >
              上一页
            </button>
            <span className="text-sm text-gray-500">
              {page} / {totalPages}
            </span>
            <button
              onClick={() => setPage(p => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
              className="px-4 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
            >
              下一页
            </button>
          </div>
        )}
      </div>

      {rejectModal.visible && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl w-full max-w-md p-5">
            <h3 className="text-base font-bold text-gray-900 mb-3">驳回认证申请</h3>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              placeholder="请输入驳回原因..."
              rows={3}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-500 focus:bg-white resize-none"
            />
            <div className="flex gap-2 mt-4">
              <button
                onClick={() => setRejectModal({ id: 0, visible: false })}
                className="flex-1 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleReject}
                disabled={verifying === rejectModal.id}
                className="flex-1 py-2.5 bg-red-500 text-white rounded-xl text-sm font-medium hover:bg-red-600 transition-colors disabled:opacity-50"
              >
                确认驳回
              </button>
            </div>
          </div>
        </div>
      )}

      <NavBar />
    </div>
  )
}
