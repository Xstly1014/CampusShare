import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postApi } from '../../services/post'
import { POSTS_KEYS } from '../queries/usePosts'

export function useTogglePostLike() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (postId: string) => postApi.toggleLike(postId),
    onMutate: async (postId) => {
      await queryClient.cancelQueries({ queryKey: POSTS_KEYS.status(postId) })
      const previousStatus = queryClient.getQueryData<{ starred: boolean; liked: boolean }>(
        POSTS_KEYS.status(postId),
      )
      queryClient.setQueryData(POSTS_KEYS.status(postId), (old: any) => ({
        ...old,
        liked: !old?.liked,
      }))
      return { previousStatus }
    },
    onError: (_, postId, context: any) => {
      if (context?.previousStatus) {
        queryClient.setQueryData(POSTS_KEYS.status(postId), context.previousStatus)
      }
    },
    onSettled: (_, __, postId) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.status(postId) })
    },
  })
}

export function useTogglePostStar() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (postId: string) => postApi.toggleStar(postId),
    onMutate: async (postId) => {
      await queryClient.cancelQueries({ queryKey: POSTS_KEYS.status(postId) })
      const previousStatus = queryClient.getQueryData<{ starred: boolean; liked: boolean }>(
        POSTS_KEYS.status(postId),
      )
      queryClient.setQueryData(POSTS_KEYS.status(postId), (old: any) => ({
        ...old,
        starred: !old?.starred,
      }))
      return { previousStatus }
    },
    onError: (_, postId, context: any) => {
      if (context?.previousStatus) {
        queryClient.setQueryData(POSTS_KEYS.status(postId), context.previousStatus)
      }
    },
    onSettled: (_, __, postId) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.status(postId) })
    },
  })
}

export function useDeletePost() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (postId: string) => postApi.delete(postId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.myPosts() })
    },
  })
}

export function useRecordDownload() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (postId: string) => postApi.recordDownload(postId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.warehouseStats() })
    },
  })
}
