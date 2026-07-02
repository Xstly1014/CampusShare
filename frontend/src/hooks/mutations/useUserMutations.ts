import { useMutation, useQueryClient } from '@tanstack/react-query'
import { userApi } from '../../services/user'
import { USERS_KEYS } from '../queries/useUsers'

export function useUpdateProfile() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: userApi.updateProfile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.me() })
    },
  })
}

export function useUpdatePrivacy() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: userApi.updatePrivacy,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.me() })
    },
  })
}

export function useUpdateNotificationSettings() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: userApi.updateNotificationSettings,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.me() })
    },
  })
}

export function useToggleFollow() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (userId: string) => userApi.toggleFollow(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.followStats() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.following() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.followers() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.mutual() })
    },
  })
}
