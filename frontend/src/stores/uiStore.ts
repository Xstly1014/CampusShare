import { create } from 'zustand'

interface GlobalModal {
  type: 'create-post' | 'image-preview' | 'confirm' | null
  data?: any
}

interface UIState {
  sidebarOpen: boolean
  setSidebarOpen: (open: boolean) => void
  toggleSidebar: () => void

  globalLoading: boolean
  setGlobalLoading: (loading: boolean) => void

  modal: GlobalModal
  openModal: (type: GlobalModal['type'], data?: any) => void
  closeModal: () => void

  pageTitle: string
  setPageTitle: (title: string) => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: false,
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),

  globalLoading: false,
  setGlobalLoading: (loading) => set({ globalLoading: loading }),

  modal: { type: null },
  openModal: (type, data) => set({ modal: { type, data } }),
  closeModal: () => set({ modal: { type: null } }),

  pageTitle: '',
  setPageTitle: (title) => set({ pageTitle: title }),
}))
