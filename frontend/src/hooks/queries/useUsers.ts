import { useQuery, useQueryClient } from '@tanstack/react-query'
import { userApi } from '../../services/user'

export const USERS_KEYS = {
  all: ['users'] as const,
  me: () => [...USERS_KEYS.all, 'me'] as const,
  profiles: () => [...USERS_KEYS.all, 'profile'] as const,
  profile: (userId: string) => [...USERS_KEYS.profiles(), userId] as const,
  followStats: () => [...USERS_KEYS.all, 'me', 'follow-stats'] as const,
  following: () => [...USERS_KEYS.all, 'me', 'following'] as const,
  followers: () => [...USERS_KEYS.all, 'me', 'followers'] as const,
  mutual: () => [...USERS_KEYS.all, 'me', 'mutual'] as const,
}

export function useCurrentUser() {
  return useQuery({
    queryKey: USERS_KEYS.me(),
    queryFn: async () => {
      const res = await userApi.getMe()
      return res.data
    },
    staleTime: 10 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: (failureCount, error: any) => {
      if (error?.message?.includes('401')) {
        return false
      }
      return failureCount < 1
    },
  })
}

export function useUserProfile(userId: string) {
  return useQuery({
    queryKey: USERS_KEYS.profile(userId),
    queryFn: async () => {
      const res = await userApi.getUserProfile(userId)
      return res.data
    },
    enabled: !!userId,
  })
}

export function useFollowStats() {
  return useQuery<{ following: number; followers: number; mutual: number }>({
    queryKey: USERS_KEYS.followStats(),
    queryFn: async () => {
      const res = await userApi.getFollowStats()
      return res.data
    },
    staleTime: 2 * 60 * 1000,
  })
}

export function useFollowingList() {
  return useQuery({
    queryKey: USERS_KEYS.following(),
    queryFn: async () => {
      const res = await userApi.getFollowingList()
      return res.data
    },
  })
}

export function useFollowerList() {
  return useQuery({
    queryKey: USERS_KEYS.followers(),
    queryFn: async () => {
      const res = await userApi.getFollowerList()
      return res.data
    },
  })
}

export function useMutualList() {
  return useQuery({
    queryKey: USERS_KEYS.mutual(),
    queryFn: async () => {
      const res = await userApi.getMutualList()
      return res.data
    },
  })
}

export function useInvalidateUsers() {
  const queryClient = useQueryClient()
  return {
    invalidateCurrentUser: () => queryClient.invalidateQueries({ queryKey: USERS_KEYS.me() }),
    invalidateProfile: (userId: string) => 
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.profile(userId) }),
    invalidateFollowData: () => {
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.followStats() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.following() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.followers() })
      queryClient.invalidateQueries({ queryKey: USERS_KEYS.mutual() })
    },
    invalidateAll: () => queryClient.invalidateQueries({ queryKey: USERS_KEYS.all }),
  }
}
