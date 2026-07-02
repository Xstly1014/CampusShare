import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postApi } from '../../services/post'
import { POSTS_KEYS } from '../queries/usePosts'

export function useCreateComment() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ postId, content, parentId, replyToUserId }: {
      postId: string
      content: string
      parentId?: string
      replyToUserId?: string
    }) => postApi.createComment(postId, content, parentId, replyToUserId),
    onSuccess: (_, { postId }) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.comments(postId) })
    },
  })
}

export function useDeleteComment() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ commentId, postId }: { commentId: string; postId: string }) =>
      postApi.deleteComment(commentId),
    onSuccess: (_, { postId }) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.comments(postId) })
    },
  })
}

export function useToggleCommentLike() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ commentId, postId }: { commentId: string; postId: string }) =>
      postApi.toggleCommentLike(commentId),
    onSuccess: (_, { postId }) => {
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.comments(postId) })
    },
  })
}
