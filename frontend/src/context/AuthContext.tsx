import type { ReactNode } from 'react'
import { createContext, useContext, useState, useCallback } from 'react'
import { authApi } from '../services/api'

const TOKEN_KEY = 'campusshare_token'
const REFRESH_TOKEN_KEY = 'campusshare_refresh_token'
const USER_KEY = 'campusshare_user'

interface User {
  id: string
  username: string
  email?: string
  phone?: string
  avatarUrl?: string
  bio?: string
  schoolId?: string
  isCreator?: boolean
  isAdmin?: boolean
}

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  loading: boolean
  login: (account: string, password: string) => Promise<void>
  register: (data: {
    registerType: string
    account: string
    username: string
    password: string
    verifyCode: string
  }) => Promise<void>
  logout: () => void
  sendCode: (account: string, type?: string) => Promise<void>
  resetPassword: (account: string, verifyCode: string, newPassword: string) => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const savedUser = sessionStorage.getItem(USER_KEY)
    return savedUser ? JSON.parse(savedUser) : null
  })
  const [loading, setLoading] = useState(false)

  const isAuthenticated = user !== null

  const login = useCallback(async (account: string, password: string) => {
    setLoading(true)
    try {
      const res = await authApi.login(account, password)
      const { token, refreshToken, user: userData } = res.data
      sessionStorage.setItem(TOKEN_KEY, token)
      if (refreshToken) {
        sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
      }
      sessionStorage.setItem(USER_KEY, JSON.stringify(userData))
      setUser(userData)
    } finally {
      setLoading(false)
    }
  }, [])

  const register = useCallback(
    async (data: {
      registerType: string
      account: string
      username: string
      password: string
      verifyCode: string
    }) => {
      setLoading(true)
      try {
        const res = await authApi.register(data)
        const { token, refreshToken, user: userData } = res.data
        sessionStorage.setItem(TOKEN_KEY, token)
        if (refreshToken) {
          sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
        }
        sessionStorage.setItem(USER_KEY, JSON.stringify(userData))
        setUser(userData)
      } finally {
        setLoading(false)
      }
    },
    [],
  )

  const logout = useCallback(() => {
    setUser(null)
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(REFRESH_TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
  }, [])

  const sendCode = useCallback(async (account: string, type = 'phone') => {
    await authApi.sendCode(account, type)
  }, [])

  const resetPassword = useCallback(
    async (account: string, verifyCode: string, newPassword: string) => {
      await authApi.resetPassword(account, verifyCode, newPassword)
    },
    [],
  )

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        loading,
        login,
        register,
        logout,
        sendCode,
        resetPassword,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
