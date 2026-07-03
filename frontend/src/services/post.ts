import { api } from './http'

export interface WarehouseCategoryStat {
  categoryId: string
  categoryName: string
  color: string
  icon: string
  uploadCount: number
  downloadCount: number
}

export interface WarehouseStats {
  uploadCount: number
  downloadCount: number
  totalViews: number
  totalLikes: number
  totalStars: number
  totalDownloadsOfMyPosts: number
  categoryStats: WarehouseCategoryStat[]
}

export interface PostComment {
  id: string
  postId: string
  userId: string
  username: string
  avatarUrl: string
  content: string
  parentId?: string
  replyToUserId?: string
  replyToUsername?: string
  likeCount: number
  isLiked: boolean
  createTime: string
}

export const postApi = {
  create: (data: {
    schoolId?: string
    categoryId?: string
    subCategoryId?: string
    postType: string
    title: string
    content: string
    fileUrl?: string
    fileName?: string
    fileType?: string
    fileSize?: number
  }) => api.post('/posts', data),

  edit: (
    postId: string,
    data: {
      schoolId?: string
      categoryId?: string
      subCategoryId?: string
      title?: string
      content?: string
      fileUrl?: string
      fileName?: string
      fileType?: string
      fileSize?: number
    },
  ) => api.put(`/posts/${postId}`, data),

  delete: (postId: string) => api.delete(`/posts/${postId}`),

  getDetail: (postId: string) => api.get(`/posts/${postId}`),

  getStatus: (postId: string) =>
    api.get<{ starred: boolean; liked: boolean }>(`/posts/${postId}/status`),

  getBySchool: (
    schoolId: string,
    params: { postType?: string; sortType?: string; page?: number; size?: number } = {},
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

  getHistory: (page = 1, size = 50) => api.get(`/posts/history?page=${page}&size=${size}`),

  getStarred: (page = 1, size = 50) => api.get(`/posts/starred?page=${page}&size=${size}`),

  getLiked: (page = 1, size = 50) => api.get(`/posts/liked?page=${page}&size=${size}`),

  getMyPosts: (page = 1, size = 50, postType?: string, keyword?: string) => {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    if (postType) params.set('postType', postType)
    if (keyword) params.set('keyword', keyword)
    return api.get(`/posts/mine?${params.toString()}`)
  },

  getMyPostStats: () =>
    api.get<{ totalViews: number; totalLikes: number; totalStars: number; postCount: number }>(
      '/posts/my-stats',
    ),

  getWarehouseStats: () => api.get<WarehouseStats>('/posts/warehouse-stats'),

  recordDownload: (postId: string) => api.post(`/posts/${postId}/download`),

  getMyDownloads: (page = 1, size = 50, keyword?: string) => {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    if (keyword) params.set('keyword', keyword)
    return api.get(`/posts/my-downloads?${params.toString()}`)
  },

  deleteDownloadRecord: (recordId: number) => api.delete(`/posts/downloads/${recordId}`),

  getComments: (postId: string) => api.get<PostComment[]>(`/posts/${postId}/comments`),

  createComment: (postId: string, content: string, parentId?: string, replyToUserId?: string) =>
    api.post(`/posts/${postId}/comments`, { content, parentId, replyToUserId }),

  deleteComment: (commentId: string) => api.delete(`/posts/comments/${commentId}`),

  toggleCommentLike: (commentId: string) => api.post(`/posts/comments/${commentId}/like`),

  getMyComments: () => api.get('/posts/my-comments'),

  getSchoolPostCounts: () => api.get<Record<string, number>>('/posts/school-counts'),
}
