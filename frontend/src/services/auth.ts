import { api } from './http'

export const authApi = {
  login: (account: string, password: string) =>
    api.post<{
      token: string
      refreshToken: string
      expiresIn: number
      user: {
        id: string
        username: string
        email?: string
        phone?: string
        avatarUrl?: string
        bio?: string
        schoolId?: string
        createTime?: string
      }
    }>('/auth/login', { account, password }),

  register: (data: {
    registerType: string
    account: string
    username: string
    password: string
    verifyCode: string
  }) => api.post('/auth/register', data),

  sendCode: (account: string, type: string = 'phone') =>
    api.post(`/auth/send-code?account=${encodeURIComponent(account)}&type=${type}`),

  resetPassword: (account: string, verifyCode: string, newPassword: string) =>
    api.post('/auth/reset-password', { account, verifyCode, newPassword }),

  getCurrentUser: () => api.get('/users/me'),
}
