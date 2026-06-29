import { api } from './http'

export interface CreatorStats {
  totalLikes: number
  totalPosts: number
  requiredLikes: number
  requiredPosts: number
  meetsRequirements: boolean
}

export interface CreatorStatus {
  status: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason?: string
  applyTime?: string
  reviewTime?: string
  totalLikes?: number
  totalPosts?: number
}

export interface CreatorApplication {
  id: number
  userId: string
  username: string
  avatarUrl?: string
  realName: string
  idCard: string
  totalLikes: number
  totalPosts: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason?: string
  applyTime: string
  reviewTime?: string
}

export interface ApplicationListResponse {
  records: CreatorApplication[]
  total: number
  size: number
  current: number
  pages: number
}

export const creatorApi = {
  getStats: () => api.get<CreatorStats>('/creator/stats'),

  getStatus: () => api.get<CreatorStatus>('/creator/status'),

  apply: (data: { realName: string; idCard: string; idCardFront?: string; idCardBack?: string }) =>
    api.post('/creator/apply', data),

  getApplications: (params: { status?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams()
    if (params.status) query.set('status', params.status)
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    const queryStr = query.toString()
    return api.get<ApplicationListResponse>(`/creator/admin/applications${queryStr ? `?${queryStr}` : ''}`)
  },

  verifyApplication: (id: number, data: { approved: boolean; rejectReason?: string }) =>
    api.post(`/creator/admin/applications/${id}/verify`, data),
}
