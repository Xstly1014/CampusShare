import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: string
  type: ToastType
  message: string
  duration: number
}

const MAX_TOASTS = 5

interface ToastState {
  toasts: ToastItem[]
  timers: Map<string, ReturnType<typeof setTimeout>>
  showToast: (message: string, type?: ToastType, duration?: number) => void
  removeToast: (id: string) => void
  clearAll: () => void
}

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  timers: new Map(),

  showToast: (message, type = 'info', duration = 3000) => {
    const id = Math.random().toString(36).slice(2)
    const toast: ToastItem = { id, type, message, duration }

    set((state) => {
      let toasts = [...state.toasts, toast]
      if (toasts.length > MAX_TOASTS) {
        const removed = toasts.slice(0, toasts.length - MAX_TOASTS)
        toasts = toasts.slice(toasts.length - MAX_TOASTS)
        const timers = new Map(state.timers)
        removed.forEach((t) => {
          const timer = timers.get(t.id)
          if (timer) {
            clearTimeout(timer)
            timers.delete(t.id)
          }
        })
        return { toasts, timers }
      }
      return { toasts }
    })

    const timer = setTimeout(() => {
      get().removeToast(id)
    }, duration)

    set((state) => {
      const timers = new Map(state.timers)
      timers.set(id, timer)
      return { timers }
    })
  },

  removeToast: (id) => {
    set((state) => {
      const timers = new Map(state.timers)
      const timer = timers.get(id)
      if (timer) {
        clearTimeout(timer)
        timers.delete(id)
      }
      return {
        toasts: state.toasts.filter((t) => t.id !== id),
        timers,
      }
    })
  },

  clearAll: () => {
    const { timers } = get()
    timers.forEach((timer) => clearTimeout(timer))
    set({ toasts: [], timers: new Map() })
  },
}))

export const toast = {
  success: (message: string, duration?: number) =>
    useToastStore.getState().showToast(message, 'success', duration),
  error: (message: string, duration?: number) =>
    useToastStore.getState().showToast(message, 'error', duration),
  info: (message: string, duration?: number) =>
    useToastStore.getState().showToast(message, 'info', duration),
  warning: (message: string, duration?: number) =>
    useToastStore.getState().showToast(message, 'warning', duration),
  dismiss: (id: string) => useToastStore.getState().removeToast(id),
  dismissAll: () => useToastStore.getState().clearAll(),
}
