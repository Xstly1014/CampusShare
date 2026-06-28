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

export const creatorApi = {
  getStats: () => api.get<CreatorStats>('/creator/stats'),

  getStatus: () => api.get<CreatorStatus>('/creator/status'),

  apply: (data: { realName: string; idCard: string; idCardFront?: string; idCardBack?: string }) =>
    api.post('/creator/apply', data),
}
