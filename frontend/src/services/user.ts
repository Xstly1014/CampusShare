import { api } from './http'

export interface PrivacySettings {
  publicPosts: boolean
  publicStars: boolean
  publicLikes: boolean
  publicHistory: boolean
  searchable: boolean
}

export interface NotificationSettings {
  notifyMessages: boolean
  notifyReplies: boolean
  notifyLikes: boolean
}

export const userApi = {
  getMe: () => api.get('/users/me'),

  updateProfile: (data: { username?: string; bio?: string; avatarUrl?: string }) =>
    api.put('/users/me', data),

  updatePrivacy: (data: Partial<PrivacySettings>) => api.put('/users/me/privacy', data),

  updateNotificationSettings: (data: Partial<NotificationSettings>) =>
    api.put('/users/me/notification-settings', data),

  changePassword: (data: { oldPassword: string; newPassword: string; confirmPassword: string }) =>
    api.put('/users/me/password', data),

  bindEmail: (data: {
    originalAccount?: string
    originalVerifyCode?: string
    newAccount: string
    newVerifyCode: string
    realNameVerify?: boolean
  }) => api.put('/users/me/email', data),

  bindPhone: (data: {
    originalAccount?: string
    originalVerifyCode?: string
    newAccount: string
    newVerifyCode: string
    realNameVerify?: boolean
  }) => api.put('/users/me/phone', data),

  realNameVerify: (data: { realName: string; idCard: string }) =>
    api.post('/users/me/real-name-verify', data),

  searchUsers: (keyword: string) => api.get(`/users/search?keyword=${encodeURIComponent(keyword)}`),

  getUserProfile: (userId: string) => api.get(`/users/${userId}/profile`),

  getUserPosts: (userId: string, page = 1, size = 50) =>
    api.get(`/users/${userId}/posts?page=${page}&size=${size}`),

  getUserStarred: (userId: string, page = 1, size = 50) =>
    api.get(`/users/${userId}/starred?page=${page}&size=${size}`),

  getUserLiked: (userId: string, page = 1, size = 50) =>
    api.get(`/users/${userId}/liked?page=${page}&size=${size}`),

  getUserHistory: (userId: string, page = 1, size = 50) =>
    api.get(`/users/${userId}/history?page=${page}&size=${size}`),

  toggleFollow: (userId: string) => api.post(`/users/${userId}/follow`),

  getFollowStats: () =>
    api.get<{ following: number; followers: number; mutual: number }>('/users/me/follow-stats'),

  getFollowingList: () => api.get('/users/me/following'),

  getFollowerList: () => api.get('/users/me/followers'),

  getMutualList: () => api.get('/users/me/mutual'),
}
