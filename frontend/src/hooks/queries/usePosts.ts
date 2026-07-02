import { useQuery, useQueryClient } from '@tanstack/react-query'
import { postApi, type PostComment } from '../../services/post'

export const POSTS_KEYS = {
  all: ['posts'] as const,
  lists: () => [...POSTS_KEYS.all, 'list'] as const,
  list: (filters: Record<string, unknown>) => [...POSTS_KEYS.lists(), filters] as const,
  schoolList: (schoolId: string, filters: Record<string, unknown>) => 
    [...POSTS_KEYS.lists(), 'school', schoolId, filters] as const,
  details: () => [...POSTS_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...POSTS_KEYS.details(), id] as const,
  status: (id: string) => [...POSTS_KEYS.detail(id), 'status'] as const,
  comments: (id: string) => [...POSTS_KEYS.detail(id), 'comments'] as const,
  myPosts: () => [...POSTS_KEYS.all, 'my'] as const,
  myHistory: () => [...POSTS_KEYS.all, 'my', 'history'] as const,
  myStarred: () => [...POSTS_KEYS.all, 'my', 'starred'] as const,
  myLiked: () => [...POSTS_KEYS.all, 'my', 'liked'] as const,
  myComments: () => [...POSTS_KEYS.all, 'my', 'comments'] as const,
  myStats: () => [...POSTS_KEYS.all, 'my', 'stats'] as const,
  warehouseStats: () => [...POSTS_KEYS.all, 'warehouse', 'stats'] as const,
  schoolCounts: () => [...POSTS_KEYS.all, 'school', 'counts'] as const,
}

export function usePostDetail(postId: string) {
  return useQuery({
    queryKey: POSTS_KEYS.detail(postId),
    queryFn: async () => {
      const res = await postApi.getDetail(postId)
      return res.data
    },
    enabled: !!postId,
  })
}

export function usePostStatus(postId: string) {
  return useQuery<{ starred: boolean; liked: boolean }>({
    queryKey: POSTS_KEYS.status(postId),
    queryFn: async () => {
      const res = await postApi.getStatus(postId)
      return res.data
    },
    enabled: !!postId,
  })
}

export function usePostComments(postId: string) {
  return useQuery<PostComment[]>({
    queryKey: POSTS_KEYS.comments(postId),
    queryFn: async () => {
      const res = await postApi.getComments(postId)
      return res.data || []
    },
    enabled: !!postId,
  })
}

export function useMyPostStats() {
  return useQuery<{ totalViews: number; totalLikes: number; totalStars: number; postCount: number }>({
    queryKey: POSTS_KEYS.myStats(),
    queryFn: async () => {
      const res = await postApi.getMyPostStats()
      return res.data
    },
    staleTime: 2 * 60 * 1000,
  })
}

export function useWarehouseStats() {
  return useQuery({
    queryKey: POSTS_KEYS.warehouseStats(),
    queryFn: async () => {
      const res = await postApi.getWarehouseStats()
      return res.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useSchoolPostCounts() {
  return useQuery<Record<string, number>>({
    queryKey: POSTS_KEYS.schoolCounts(),
    queryFn: async () => {
      const res = await postApi.getSchoolPostCounts()
      return res.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useInvalidatePosts() {
  const queryClient = useQueryClient()
  return {
    invalidateAll: () => queryClient.invalidateQueries({ queryKey: POSTS_KEYS.all }),
    invalidatePost: (postId: string) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.detail(postId) })
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.comments(postId) })
    },
    invalidateLists: () => queryClient.invalidateQueries({ queryKey: POSTS_KEYS.lists() }),
    invalidateMyPosts: () => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.myPosts() })
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.myStats() })
    },
  }
}
