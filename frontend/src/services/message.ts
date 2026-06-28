import { api } from './http'

export const messageApi = {
  send: (receiverId: string, content: string) =>
    api.post('/messages/send', { receiverId, content }),

  getConversation: (otherUserId: string) =>
    api.get(`/messages/conversation/${otherUserId}`),

  getList: () => api.get('/messages/list'),

  canSend: (otherUserId: string) => api.get(`/messages/can-send/${otherUserId}`),

  hideConversation: (otherUserId: string) => api.delete(`/messages/conversation/${otherUserId}`),
}
