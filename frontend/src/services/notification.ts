import { api } from './http'

export interface SenderInfo {
  userId: string
  username: string
  avatarUrl?: string
}

export interface NotificationListItem {
  id: string
  type: string
  senderId?: string
  senderName?: string
  senderAvatar?: string
  aggregatedSenders?: SenderInfo[]
  aggregatedCount: number
  targetId?: string
  targetTitle?: string
  commentId?: string
  schoolId?: string
  categoryId?: string
  content?: string
  isRead: number
  createTime: string
}

export interface NotificationDetail {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  type: string
  targetId?: string
  targetTitle?: string
  schoolId?: string
  categoryId?: string
  commentId?: string
  isRead: number
  createTime: string
}

export const notificationApi = {
  getList: () => api.get<NotificationListItem[]>('/notifications/list'),

  markSingleAsRead: (id: number | string) => api.post(`/notifications/read-single/${id}`),

  markAggregatedAsRead: (type: string, targetId: string) =>
    api.post('/notifications/read-aggregated', { type, targetId }),

  markAllAsRead: () => api.post('/notifications/read-all'),

  getUnreadCount: () => api.get<number>('/notifications/unread-count'),
}
