import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query'
import {
  notificationApi,
  type NotificationItem,
  type NotificationDetail,
} from '../../services/notification'

export const NOTIFICATIONS_KEYS = {
  all: ['notifications'] as const,
  feed: () => [...NOTIFICATIONS_KEYS.all, 'feed'] as const,
  detail: (type: string) => [...NOTIFICATIONS_KEYS.all, 'detail', type] as const,
  unreadCount: () => [...NOTIFICATIONS_KEYS.all, 'unreadCount'] as const,
}

export function useNotificationFeed() {
  return useQuery<NotificationItem[]>({
    queryKey: NOTIFICATIONS_KEYS.feed(),
    queryFn: async () => {
      const res = await notificationApi.getFeed()
      return res.data || []
    },
    staleTime: 30 * 1000,
  })
}

export function useNotificationDetail(type: string) {
  return useQuery<NotificationDetail[]>({
    queryKey: NOTIFICATIONS_KEYS.detail(type),
    queryFn: async () => {
      const res = await notificationApi.getDetail(type)
      return res.data || []
    },
    enabled: !!type,
  })
}

export function useUnreadNotificationCount(enabled = true) {
  return useQuery<number>({
    queryKey: NOTIFICATIONS_KEYS.unreadCount(),
    queryFn: async () => {
      const res = await notificationApi.getUnreadCount()
      return res.data || 0
    },
    enabled,
    refetchInterval: 30 * 1000,
    refetchIntervalInBackground: false,
    staleTime: 10 * 1000,
  })
}

export function useMarkNotificationsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (type: string) => notificationApi.markAsRead(type),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEYS.unreadCount() })
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEYS.feed() })
    },
  })
}

export function useToggleNotificationPin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ itemType, targetId }: { itemType: string; targetId?: string }) =>
      notificationApi.togglePin(itemType, targetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEYS.feed() })
    },
  })
}
