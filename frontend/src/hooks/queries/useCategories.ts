import { useQuery, useQueryClient } from '@tanstack/react-query'
import { categoryApi, type Category } from '../../services/category'

export const CATEGORIES_KEYS = {
  all: ['categories'] as const,
  lists: () => [...CATEGORIES_KEYS.all, 'list'] as const,
  list: () => [...CATEGORIES_KEYS.lists()] as const,
  details: () => [...CATEGORIES_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...CATEGORIES_KEYS.details(), id] as const,
  counts: () => [...CATEGORIES_KEYS.all, 'counts'] as const,
}

export function useCategories() {
  return useQuery<Category[]>({
    queryKey: CATEGORIES_KEYS.list(),
    queryFn: async () => {
      const res = await categoryApi.getAll()
      return res.data
    },
    staleTime: 10 * 60 * 1000,
  })
}

export function useCategoryDetail(categoryId: string) {
  return useQuery<Category>({
    queryKey: CATEGORIES_KEYS.detail(categoryId),
    queryFn: async () => {
      const res = await categoryApi.getDetail(categoryId)
      return res.data
    },
    enabled: !!categoryId,
  })
}

export function useCategoryCounts() {
  return useQuery<Record<string, number>>({
    queryKey: CATEGORIES_KEYS.counts(),
    queryFn: async () => {
      const res = await categoryApi.getCounts()
      return res.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useInvalidateCategories() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: CATEGORIES_KEYS.all })
  }
}
