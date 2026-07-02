import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query'
import { messageApi } from '../../services/message'
import { userApi } from '../../services/user'

export interface MessageItem {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  receiverId: string
  receiverName?: string
  receiverAvatar?: string
  content: string
  isRead: number
  isMine?: boolean
  createTime: string
}

export interface ConversationItem {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  receiverId: string
  receiverName?: string
  receiverAvatar?: string
  content: string
  isRead: number
  createTime: string
}

export const MESSAGES_KEYS = {
  all: ['messages'] as const,
  list: () => [...MESSAGES_KEYS.all, 'list'] as const,
  conversation: (userId: string) => [...MESSAGES_KEYS.all, 'conversation', userId] as const,
  canSend: (userId: string) => [...MESSAGES_KEYS.all, 'canSend', userId] as const,
}

export function useConversationList() {
  return useQuery<ConversationItem[]>({
    queryKey: MESSAGES_KEYS.list(),
    queryFn: async () => {
      const res = await messageApi.getList()
      return res.data || []
    },
  })
}

export function useConversationMessages(otherUserId: string | undefined) {
  return useQuery<MessageItem[]>({
    queryKey: MESSAGES_KEYS.conversation(otherUserId || ''),
    queryFn: async () => {
      if (!otherUserId) return []
      const res = await messageApi.getConversation(otherUserId)
      return res.data || []
    },
    enabled: !!otherUserId,
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
  })
}

export function useCanSendMessage(otherUserId: string | undefined) {
  return useQuery<boolean>({
    queryKey: MESSAGES_KEYS.canSend(otherUserId || ''),
    queryFn: async () => {
      if (!otherUserId) return true
      const res = await messageApi.canSend(otherUserId)
      return res.data
    },
    enabled: !!otherUserId,
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
  })
}

export function useOtherUserProfile(otherUserId: string | undefined) {
  return useQuery<{ id: string; username: string; avatarUrl?: string } | null>({
    queryKey: ['users', 'profile', otherUserId],
    queryFn: async () => {
      if (!otherUserId) return null
      const res = await userApi.getUserProfile(otherUserId)
      return res.data || null
    },
    enabled: !!otherUserId,
    staleTime: 5 * 60 * 1000,
  })
}

export function useSendMessage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ receiverId, content }: { receiverId: string; content: string }) =>
      messageApi.send(receiverId, content),
    onSuccess: (_, { receiverId }) => {
      queryClient.invalidateQueries({ queryKey: MESSAGES_KEYS.conversation(receiverId) })
      queryClient.invalidateQueries({ queryKey: MESSAGES_KEYS.canSend(receiverId) })
      queryClient.invalidateQueries({ queryKey: MESSAGES_KEYS.list() })
    },
  })
}

export function useHideConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (otherUserId: string) => messageApi.hideConversation(otherUserId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MESSAGES_KEYS.list() })
    },
  })
}
