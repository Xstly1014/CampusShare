import { api } from './http'

export interface NotificationItem {
  itemType: string
  title: string
  preview: string
  unreadCount: number
  totalCount: number
  latestTime: string
  isPinned: boolean
  otherUserId?: string
  otherUserName?: string
  otherUserAvatar?: string
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
  commentId?: string
  isRead: number
  createTime: string
}

export const notificationApi = {
  getFeed: () => api.get<NotificationItem[]>('/notifications/feed'),

  getDetail: (type: string) => api.get<NotificationDetail[]>(`/notifications/detail/${type}`),

  markAsRead: (type: string) => api.post(`/notifications/read/${type}`),

  togglePin: (itemType: string, targetId?: string) =>
    api.post('/notifications/pin', { itemType, targetId }),

  getUnreadCount: () => api.get<number>('/notifications/unread-count'),
}
