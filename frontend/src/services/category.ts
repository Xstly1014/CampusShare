import { api } from './http'

export interface SubCategory {
  id: string
  categoryId: string
  name: string
  icon: string
  sortOrder: number
  postCount: number
}

export interface Category {
  id: string
  name: string
  icon: string
  color: string
  type: 'school' | 'category'
  description: string
  sortOrder: number
  postCount: number
  subCategories: SubCategory[]
}

export const categoryApi = {
  getAll: () => api.get<Category[]>('/categories'),

  getDetail: (categoryId: string) => api.get<Category>(`/categories/${categoryId}`),

  getSubCategoryPosts: (
    subCategoryId: string,
    params: {
      postType?: string
      sortType?: string
      page?: number
      size?: number
      keyword?: string
    } = {},
  ) => {
    const query = new URLSearchParams()
    if (params.postType) query.set('postType', params.postType)
    if (params.sortType) query.set('sortType', params.sortType)
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    if (params.keyword) query.set('keyword', params.keyword)
    const queryStr = query.toString()
    return api.get(`/categories/sub/${subCategoryId}/posts${queryStr ? `?${queryStr}` : ''}`)
  },

  getCategoryPosts: (
    categoryId: string,
    params: { postType?: string; sortType?: string; page?: number; size?: number } = {},
  ) => {
    const query = new URLSearchParams()
    if (params.postType) query.set('postType', params.postType)
    if (params.sortType) query.set('sortType', params.sortType)
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    const queryStr = query.toString()
    return api.get(`/categories/${categoryId}/posts${queryStr ? `?${queryStr}` : ''}`)
  },

  getCounts: () => api.get<Record<string, number>>('/categories/counts'),
}
