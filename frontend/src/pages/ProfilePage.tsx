import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/common/NavBar'
import { postApi, fileApi, userApi, creatorApi, CreatorStatus } from '../services/api'
import { toast } from '../stores/toastStore'
import {
  User,
  Mail,
  Phone,
  LogOut,
  ChevronRight,
  Camera,
  Edit3,
  Award,
  Clock,
  Star,
  ThumbsUp,
  MessageSquare,
  Settings,
  HelpCircle,
  Shield,
  FileText,
  ZoomIn,
  ZoomOut,
  Move,
} from 'lucide-react'

const CROP_SIZE = 280
const OUTPUT_SIZE = 400

function AvatarCropper({ src, onConfirm, onCancel }: { src: string; onConfirm: (blob: Blob) => void; onCancel: () => void }) {
  const [scale, setScale] = useState(1)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  const [imgLoaded, setImgLoaded] = useState(false)
  const [imgSize, setImgSize] = useState({ w: 0, h: 0 })
  const imgRef = useRef<HTMLImageElement>(null)
  const dragStart = useRef({ x: 0, y: 0, ox: 0, oy: 0 })

  const handleImageLoad = () => {
    if (!imgRef.current) return
    const { naturalWidth, naturalHeight } = imgRef.current
    setImgSize({ w: naturalWidth, h: naturalHeight })
    const minScale = Math.max(CROP_SIZE / naturalWidth, CROP_SIZE / naturalHeight)
    setScale(minScale)
    const displayW = naturalWidth * minScale
    const displayH = naturalHeight * minScale
    setOffset({ x: (CROP_SIZE - displayW) / 2, y: (CROP_SIZE - displayH) / 2 })
    setImgLoaded(true)
  }

  const handleMouseDown = (e: React.MouseEvent | React.TouchEvent) => {
    setDragging(true)
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
    dragStart.current = { x: clientX, y: clientY, ox: offset.x, oy: offset.y }
  }

  const handleMouseMove = useCallback((e: MouseEvent | TouchEvent) => {
    if (!dragging) return
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
    const dx = clientX - dragStart.current.x
    const dy = clientY - dragStart.current.y
    setOffset({ x: dragStart.current.ox + dx, y: dragStart.current.oy + dy })
  }, [dragging])

  const handleMouseUp = useCallback(() => {
    setDragging(false)
  }, [])

  useEffect(() => {
    if (dragging) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
      window.addEventListener('touchmove', handleMouseMove)
      window.addEventListener('touchend', handleMouseUp)
      return () => {
        window.removeEventListener('mousemove', handleMouseMove)
        window.removeEventListener('mouseup', handleMouseUp)
        window.removeEventListener('touchmove', handleMouseMove)
        window.removeEventListener('touchend', handleMouseUp)
      }
    }
  }, [dragging, handleMouseMove, handleMouseUp])

  const clampOffset = (newScale: number) => {
    if (!imgRef.current) return offset
    const displayW = imgSize.w * newScale
    const displayH = imgSize.h * newScale
    const minX = CROP_SIZE - displayW
    const minY = CROP_SIZE - displayH
    return {
      x: Math.min(0, Math.max(minX, offset.x)),
      y: Math.min(0, Math.max(minY, offset.y)),
    }
  }

  const handleZoom = (delta: number) => {
    const minScale = Math.max(CROP_SIZE / imgSize.w, CROP_SIZE / imgSize.h)
    const maxScale = Math.max(minScale * 4, minScale + 1)
    const newScale = Math.max(minScale, Math.min(maxScale, scale + delta))
    setScale(newScale)
    setOffset(clampOffset(newScale))
  }

  const handleConfirm = () => {
    if (!imgRef.current) return
    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.drawImage(
      imgRef.current,
      -offset.x / scale,
      -offset.y / scale,
      CROP_SIZE / scale,
      CROP_SIZE / scale,
      0,
      0,
      OUTPUT_SIZE,
      OUTPUT_SIZE
    )
    canvas.toBlob((blob) => {
      if (blob) onConfirm(blob)
    }, 'image/jpeg', 0.92)
  }

  return (
    <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center" onClick={onCancel}>
      <div className="bg-gray-900 w-full max-w-sm rounded-2xl p-5" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-white text-center text-base font-semibold mb-4">移动和缩放</h3>
        <div
          className="relative mx-auto overflow-hidden"
          style={{ width: CROP_SIZE, height: CROP_SIZE }}
          onMouseDown={handleMouseDown}
          onTouchStart={handleMouseDown}
        >
          <div
            className="absolute inset-0 cursor-move"
            style={{ touchAction: 'none' }}
          >
            <img
              ref={imgRef}
              src={src}
              alt="裁剪预览"
              onLoad={handleImageLoad}
              style={{
                position: 'absolute',
                left: offset.x,
                top: offset.y,
                width: imgSize.w * scale,
                height: imgSize.h * scale,
                userSelect: 'none',
                pointerEvents: 'none',
              }}
              draggable={false}
            />
          </div>
          {/* Circular mask overlay */}
          <div className="absolute inset-0 pointer-events-none">
            <svg width={CROP_SIZE} height={CROP_SIZE}>
              <defs>
                <mask id="circleMask">
                  <rect width="100%" height="100%" fill="white" />
                  <circle cx={CROP_SIZE / 2} cy={CROP_SIZE / 2} r={CROP_SIZE / 2 - 2} fill="black" />
                </mask>
              </defs>
              <rect width="100%" height="100%" fill="rgba(0,0,0,0.6)" mask="url(#circleMask)" />
              <circle cx={CROP_SIZE / 2} cy={CROP_SIZE / 2} r={CROP_SIZE / 2 - 2} fill="none" stroke="white" strokeWidth="2" />
            </svg>
          </div>
        </div>

        {imgLoaded && (
          <div className="flex items-center justify-center gap-4 mt-4">
            <button onClick={() => handleZoom(-0.1)} className="w-9 h-9 bg-white/10 rounded-full flex items-center justify-center text-white hover:bg-white/20 transition-colors">
              <ZoomOut className="w-4 h-4" />
            </button>
            <div className="flex items-center gap-2 text-white/60 text-xs">
              <Move className="w-3.5 h-3.5" />
              <span>拖动调整</span>
            </div>
            <button onClick={() => handleZoom(0.1)} className="w-9 h-9 bg-white/10 rounded-full flex items-center justify-center text-white hover:bg-white/20 transition-colors">
              <ZoomIn className="w-4 h-4" />
            </button>
          </div>
        )}

        <div className="flex gap-3 mt-5">
          <button onClick={onCancel} className="flex-1 py-2.5 bg-white/10 text-white rounded-xl text-sm font-medium hover:bg-white/20 transition-colors">取消</button>
          <button onClick={handleConfirm} className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors">确定</button>
        </div>
      </div>
    </div>
  )
}

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [showEditModal, setShowEditModal] = useState(false)
  const [editUsername, setEditUsername] = useState(user?.username || '')
  const [editBio, setEditBio] = useState(user?.bio || '这个人很懒，什么都没留下...')
  const [bio, setBio] = useState(user?.bio || '这个人很懒，什么都没留下...')
  const [avatar, setAvatar] = useState<string | null>(user?.avatarUrl || null)
  const [counts, setCounts] = useState({ browse: 0, starred: 0, liked: 0 })
  const [stats, setStats] = useState({ totalViews: 0, totalLikes: 0, totalStars: 0, postCount: 0 })
  const [commentCount, setCommentCount] = useState(0)
  const [followStats, setFollowStats] = useState({ following: 0, followers: 0, mutual: 0 })
  const [statsModal, setStatsModal] = useState<{ title: string; value: number; suffix: string } | null>(null)
  const [cropperSrc, setCropperSrc] = useState<string | null>(null)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [creatorStatus, setCreatorStatus] = useState<CreatorStatus | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await userApi.getMe()
        const u = res.data
        if (u.avatarUrl) setAvatar(u.avatarUrl)
        if (u.bio) {
          setBio(u.bio)
          setEditBio(u.bio)
        }
        if (u.username) setEditUsername(u.username)
        sessionStorage.setItem('campusshare_user', JSON.stringify(u))
      } catch { /* ignore */ }
    }
    fetchUser()
  }, [])

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [historyRes, starredRes, likedRes, statsRes, commentsRes, followRes, creatorRes] = await Promise.all([
          postApi.getHistory(1, 1),
          postApi.getStarred(1, 1),
          postApi.getLiked(1, 1),
          postApi.getMyPostStats(),
          postApi.getMyComments(),
          userApi.getFollowStats(),
          creatorApi.getStatus(),
        ])
        setCounts({
          browse: (historyRes.data || []).length,
          starred: (starredRes.data || []).length,
          liked: (likedRes.data || []).length,
        })
        setStats(statsRes.data || { totalViews: 0, totalLikes: 0, totalStars: 0, postCount: 0 })
        setCommentCount((commentsRes.data || []).length)
        setFollowStats(followRes.data || { following: 0, followers: 0, mutual: 0 })
        setCreatorStatus(creatorRes.data || null)
      } catch (err) { /* ignore */ }
    }
    fetchData()
  }, [])

  const listEntries = [
    { key: 'mine', label: '我的帖子', icon: <FileText className="w-5 h-5" />, count: stats.postCount, color: 'bg-indigo-50 text-indigo-600' },
    { key: 'comments', label: '我的回复', icon: <MessageSquare className="w-5 h-5" />, count: commentCount, color: 'bg-teal-50 text-teal-600' },
    { key: 'history', label: '浏览历史', icon: <Clock className="w-5 h-5" />, count: counts.browse, color: 'bg-blue-50 text-blue-600' },
    { key: 'starred', label: '我的收藏', icon: <Star className="w-5 h-5" />, count: counts.starred, color: 'bg-orange-50 text-orange-600' },
    { key: 'liked', label: '我的点赞', icon: <ThumbsUp className="w-5 h-5" />, count: counts.liked, color: 'bg-red-50 text-red-600' },
  ]

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      toast.error('请选择图片文件')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error('图片大小不能超过10MB')
      return
    }
    const reader = new FileReader()
    reader.onload = (ev) => {
      setCropperSrc(ev.target?.result as string)
    }
    reader.readAsDataURL(file)
    e.target.value = ''
  }

  const handleCropConfirm = async (blob: Blob) => {
    setUploadingAvatar(true)
    try {
      const croppedFile = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
      const uploadRes = await fileApi.upload(croppedFile)
      const avatarUrl = uploadRes.url
      await userApi.updateProfile({ avatarUrl })
      const savedUser = sessionStorage.getItem('campusshare_user')
      if (savedUser) {
        const userData = JSON.parse(savedUser)
        userData.avatarUrl = avatarUrl
        sessionStorage.setItem('campusshare_user', JSON.stringify(userData))
      }
      setAvatar(avatarUrl)
      setCropperSrc(null)
      setShowEditModal(false)
      toast.success('头像修改成功')
    } catch (err) {
      toast.error((err as Error).message || '头像上传失败')
    } finally {
      setUploadingAvatar(false)
    }
  }

  const handleSaveProfile = async () => {
    try {
      const res = await userApi.updateProfile({
        username: editUsername.trim() || undefined,
        bio: editBio,
      })
      const savedUser = sessionStorage.getItem('campusshare_user')
      if (savedUser) {
        const userData = JSON.parse(savedUser)
        userData.username = editUsername.trim() || userData.username
        userData.bio = editBio
        sessionStorage.setItem('campusshare_user', JSON.stringify(userData))
      }
      setBio(editBio)
      setShowEditModal(false)
      toast.success('资料保存成功')
      window.location.reload()
    } catch (err) {
      toast.error((err as Error).message || '保存失败')
    }
  }

  const openFilePicker = () => {
    fileInputRef.current?.click()
  }

  const getAvatarSrc = (url: string | null) => {
    if (!url) return null
    return url.startsWith('/files/') ? `/api${url}` : url
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16">
      <div className="bg-white border-b border-gray-100">
        <div className="max-w-5xl mx-auto px-4 py-6">
          <div className="flex items-start gap-4">
            <div className="relative group">
              <div
                className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden cursor-pointer"
                onClick={openFilePicker}
              >
                {avatar ? (
                  <img src={getAvatarSrc(avatar)} alt="头像" className="w-full h-full object-cover" />
                ) : (
                  <span className="text-white text-xl font-bold">{user?.username?.substring(0, 1).toUpperCase() || 'U'}</span>
                )}
              </div>
              <button
                onClick={openFilePicker}
                className="absolute bottom-0 right-0 w-6 h-6 bg-gray-900 rounded-full flex items-center justify-center cursor-pointer opacity-0 group-hover:opacity-100 transition-opacity hover:bg-gray-800"
              >
                <Camera className="w-3 h-3 text-white" />
              </button>
              <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={handleFileSelect} />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold text-gray-900">{user?.username || '用户'}</h1>
                {creatorStatus?.status === 'APPROVED' && (
                  <span className="inline-flex items-center justify-center w-5 h-5 bg-gradient-to-br from-amber-400 to-orange-500 rounded-full flex-shrink-0" title="认证创作者">
                    <span className="text-white text-[10px] font-bold leading-none">V</span>
                  </span>
                )}
                <button onClick={() => setShowEditModal(true)} className="p-1 hover:bg-gray-100 rounded-full transition-colors">
                  <Edit3 className="w-3.5 h-3.5 text-gray-400" />
                </button>
              </div>
              <p className="text-xs text-gray-400 mt-0.5 line-clamp-1">{bio}</p>
              <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                <span className="flex items-center gap-1">
                  <Mail className="w-3 h-3" />
                  {user?.email || '未绑定邮箱'}
                </span>
                {user?.phone && (
                  <span className="flex items-center gap-1">
                    <Phone className="w-3 h-3" />
                    {user.phone}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2 mt-5">
            <button onClick={() => setStatsModal({ title: '总浏览', value: stats.totalViews, suffix: '个总浏览' })} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{stats.totalViews}</p>
              <p className="text-xs text-gray-400 mt-0.5">总浏览</p>
            </button>
            <button onClick={() => setStatsModal({ title: '获赞', value: stats.totalLikes, suffix: '个赞' })} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{stats.totalLikes}</p>
              <p className="text-xs text-gray-400 mt-0.5">获赞</p>
            </button>
            <button onClick={() => setStatsModal({ title: '被收藏', value: stats.totalStars, suffix: '次收藏' })} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{stats.totalStars}</p>
              <p className="text-xs text-gray-400 mt-0.5">被收藏</p>
            </button>
            <button onClick={() => navigate('/profile/following')} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{followStats.following}</p>
              <p className="text-xs text-gray-400 mt-0.5">关注</p>
            </button>
            <button onClick={() => navigate('/profile/followers')} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{followStats.followers}</p>
              <p className="text-xs text-gray-400 mt-0.5">粉丝</p>
            </button>
            <button onClick={() => navigate('/profile/mutual')} className="text-center hover:bg-gray-50 rounded-lg py-2 transition-colors">
              <p className="text-base font-bold text-gray-900">{followStats.mutual}</p>
              <p className="text-xs text-gray-400 mt-0.5">互关</p>
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-gradient-to-r from-amber-50 to-orange-50 rounded-2xl border border-amber-100 p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-amber-400 to-orange-500 rounded-xl flex items-center justify-center">
              <Award className="w-5 h-5 text-white" />
            </div>
            <div>
              <p className="text-sm font-semibold text-amber-900">
                {creatorStatus?.status === 'APPROVED' ? '已认证创作者' : creatorStatus?.status === 'PENDING' ? '认证审核中' : '认证创作者'}
              </p>
              <p className="text-xs text-amber-600">
                {creatorStatus?.status === 'APPROVED' ? '你已享受专属激励奖励' : creatorStatus?.status === 'PENDING' ? '申请已提交，请耐心等待' : '申请认证，享受专属激励奖励'}
              </p>
            </div>
          </div>
          {creatorStatus?.status !== 'APPROVED' && (
            <button
              onClick={() => navigate('/creator-verification')}
              className="px-3 py-1.5 bg-amber-500 text-white text-xs font-medium rounded-full hover:bg-amber-600 transition-colors"
            >
              {creatorStatus?.status === 'PENDING' ? '查看详情' : creatorStatus?.status === 'REJECTED' ? '重新申请' : '立即申请'}
            </button>
          )}
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          {listEntries.map((entry, idx) => (
            <button
              key={entry.key}
              onClick={() => navigate(`/profile/${entry.key}`)}
              className={`w-full flex items-center gap-3 px-4 py-4 hover:bg-gray-50 transition-colors ${idx < listEntries.length - 1 ? 'border-b border-gray-50' : ''
                }`}
            >
              <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${entry.color}`}>{entry.icon}</div>
              <span className="flex-1 text-left text-sm text-gray-700 font-medium">{entry.label}</span>
              {entry.count > 0 && <span className="text-xs text-gray-400">{entry.count}</span>}
              <ChevronRight className="w-4 h-4 text-gray-300" />
            </button>
          ))}
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 mt-3">
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          {user?.isAdmin && (
            <button onClick={() => navigate('/admin/creator')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
              <div className="w-8 h-8 bg-red-50 rounded-lg flex items-center justify-center">
                <Shield className="w-4 h-4 text-red-600" />
              </div>
              <span className="flex-1 text-left text-sm text-gray-700">创作者审核管理</span>
              <ChevronRight className="w-4 h-4 text-gray-300" />
            </button>
          )}
          <button onClick={() => navigate('/notifications')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-cyan-50 rounded-lg flex items-center justify-center">
              <MessageSquare className="w-4 h-4 text-cyan-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">通知与私信</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/account')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-blue-50 rounded-lg flex items-center justify-center">
              <User className="w-4 h-4 text-blue-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">账号与安全</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/privacy')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-green-50 rounded-lg flex items-center justify-center">
              <Shield className="w-4 h-4 text-green-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">隐私设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/general')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors border-b border-gray-50">
            <div className="w-8 h-8 bg-purple-50 rounded-lg flex items-center justify-center">
              <Settings className="w-4 h-4 text-purple-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">通用设置</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
          <button onClick={() => navigate('/settings/help')} className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors">
            <div className="w-8 h-8 bg-gray-50 rounded-lg flex items-center justify-center">
              <HelpCircle className="w-4 h-4 text-gray-600" />
            </div>
            <span className="flex-1 text-left text-sm text-gray-700">帮助与反馈</span>
            <ChevronRight className="w-4 h-4 text-gray-300" />
          </button>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 mt-3 mb-6">
        <button onClick={logout} className="w-full bg-white rounded-2xl border border-gray-100 py-3.5 flex items-center justify-center gap-2 text-red-500 text-sm font-medium hover:bg-red-50 transition-colors">
          <LogOut className="w-4 h-4" />
          退出登录
        </button>
      </div>

      {statsModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" onClick={() => setStatsModal(null)}>
          <div className="bg-gradient-to-br from-blue-500 to-purple-600 w-64 rounded-3xl p-8 text-center" onClick={(e) => e.stopPropagation()}>
            <p className="text-white/80 text-sm mb-2">{statsModal.title}</p>
            <p className="text-white text-5xl font-bold mb-3">{statsModal.value}</p>
            <p className="text-white/70 text-sm">你迄今为止获得了{statsModal.value}{statsModal.suffix}哦~</p>
            <button onClick={() => setStatsModal(null)} className="mt-5 px-6 py-2 bg-white/20 text-white rounded-full text-sm font-medium hover:bg-white/30 transition-colors">关闭</button>
          </div>
        </div>
      )}

      {showEditModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center" onClick={() => !uploadingAvatar && setShowEditModal(false)}>
          <div className="bg-white w-full sm:max-w-md sm:rounded-2xl rounded-t-3xl p-6 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-gray-900 mb-5">编辑资料</h2>

            <div className="flex justify-center mb-5">
              <div className="relative group">
                <div
                  className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center overflow-hidden cursor-pointer"
                  onClick={openFilePicker}
                >
                  {avatar ? (
                    <img src={getAvatarSrc(avatar)} alt="头像" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-white text-2xl font-bold">{user?.username?.substring(0, 1).toUpperCase() || 'U'}</span>
                  )}
                </div>
                <button onClick={openFilePicker} className="absolute bottom-0 right-0 w-7 h-7 bg-gray-900 rounded-full flex items-center justify-center cursor-pointer hover:bg-gray-800">
                  <Camera className="w-3.5 h-3.5 text-white" />
                </button>
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">用户名</label>
              <input
                type="text"
                value={editUsername}
                onChange={(e) => setEditUsername(e.target.value)}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all"
              />
            </div>

            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">个人简介</label>
              <textarea
                value={editBio}
                onChange={(e) => setEditBio(e.target.value)}
                rows={3}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white focus:border-transparent transition-all resize-none"
              />
            </div>

            <div className="flex gap-3">
              <button onClick={() => setShowEditModal(false)} disabled={uploadingAvatar} className="flex-1 py-3 bg-gray-100 text-gray-700 rounded-xl text-sm font-medium hover:bg-gray-200 transition-colors disabled:opacity-40">取消</button>
              <button onClick={handleSaveProfile} disabled={uploadingAvatar} className="flex-1 py-3 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-40">{uploadingAvatar ? '上传中...' : '保存'}</button>
            </div>
          </div>
        </div>
      )}

      {cropperSrc && (
        <AvatarCropper src={cropperSrc} onConfirm={handleCropConfirm} onCancel={() => setCropperSrc(null)} />
      )}

      <NavBar />
    </div>
  )
}
