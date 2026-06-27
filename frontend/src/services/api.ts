const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

async function request<T = any>(
  url: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = sessionStorage.getItem('campusshare_token')

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}${url}`, {
    ...options,
    headers,
  })

  const data = await response.json()

  if (!response.ok || data.code !== 200) {
    throw new Error(data.message || '请求失败')
  }

  return data
}

export const api = {
  get: <T = any>(url: string) => request<T>(url, { method: 'GET' }),
  post: <T = any>(url: string, body?: any) =>
    request<T>(url, { method: 'POST', body: JSON.stringify(body) }),
  put: <T = any>(url: string, body?: any) =>
    request<T>(url, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T = any>(url: string) => request<T>(url, { method: 'DELETE' }),
}

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

export const fileApi = {
  upload: async (file: File) => {
    const token = sessionStorage.getItem('campusshare_token')
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetch(`${API_BASE_URL}/files/upload`, {
      method: 'POST',
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: formData,
    })

    const data = await response.json()
    if (!response.ok || data.code !== 200) {
      throw new Error(data.message || '上传失败')
    }
    return data.data
  },
}

export const postApi = {
  create: (data: {
    schoolId: string
    postType: string
    title: string
    content: string
    fileUrl?: string
    fileName?: string
    fileType?: string
    fileSize?: number
  }) => api.post('/posts', data),

  edit: (postId: string, data: {
    title?: string
    content?: string
    fileUrl?: string
    fileName?: string
    fileType?: string
    fileSize?: number
  }) => api.put(`/posts/${postId}`, data),

  delete: (postId: string) => api.delete(`/posts/${postId}`),

  getDetail: (postId: string) => api.get(`/posts/${postId}`),

  getStatus: (postId: string) => api.get<{ starred: boolean; liked: boolean }>(`/posts/${postId}/status`),

  getBySchool: (
    schoolId: string,
    params: { postType?: string; sortType?: string; page?: number; size?: number } = {}
  ) => {
    const query = new URLSearchParams()
    if (params.postType) query.set('postType', params.postType)
    if (params.sortType) query.set('sortType', params.sortType)
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    const queryStr = query.toString()
    return api.get(`/posts/school/${schoolId}${queryStr ? `?${queryStr}` : ''}`)
  },

  toggleStar: (postId: string) => api.post(`/posts/${postId}/star`),

  toggleLike: (postId: string) => api.post(`/posts/${postId}/like`),

  getHistory: (page: number = 1, size: number = 50) =>
    api.get(`/posts/history?page=${page}&size=${size}`),

  getStarred: (page: number = 1, size: number = 50) =>
    api.get(`/posts/starred?page=${page}&size=${size}`),

  getLiked: (page: number = 1, size: number = 50) =>
    api.get(`/posts/liked?page=${page}&size=${size}`),

  getMyPosts: (page: number = 1, size: number = 50) =>
    api.get(`/posts/mine?page=${page}&size=${size}`),

  getMyPostStats: () => api.get<{ totalViews: number; totalLikes: number; totalStars: number; postCount: number }>('/posts/my-stats'),

  getComments: (postId: string) => api.get(`/posts/${postId}/comments`),

  createComment: (postId: string, content: string, parentId?: string, replyToUserId?: string) =>
    api.post(`/posts/${postId}/comments`, { content, parentId, replyToUserId }),

  deleteComment: (commentId: string) => api.delete(`/posts/comments/${commentId}`),

  toggleCommentLike: (commentId: string) => api.post(`/posts/comments/${commentId}/like`),

  getMyComments: () => api.get('/posts/my-comments'),

  getSchoolPostCounts: () => api.get<Record<string, number>>('/posts/school-counts'),
}

export const userApi = {
  getMe: () => api.get('/users/me'),

  updateProfile: (data: { username?: string; bio?: string; avatarUrl?: string }) =>
    api.put('/users/me', data),

  changePassword: (data: { oldPassword: string; newPassword: string; confirmPassword: string }) =>
    api.put('/users/me/password', data),

  bindEmail: (data: { originalAccount?: string; originalVerifyCode?: string; newAccount: string; newVerifyCode: string; realNameVerify?: boolean }) =>
    api.put('/users/me/email', data),

  bindPhone: (data: { originalAccount?: string; originalVerifyCode?: string; newAccount: string; newVerifyCode: string; realNameVerify?: boolean }) =>
    api.put('/users/me/phone', data),

  realNameVerify: (data: { realName: string; idCard: string }) =>
    api.post('/users/me/real-name-verify', data),
}
