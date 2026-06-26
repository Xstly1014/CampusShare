import { createContext, useContext, useState, ReactNode } from 'react'

interface User {
  id: string
  username: string
  email?: string
  phone?: string
  avatar?: string
}

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  login: (user: User) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    // 从session storage读取用户信息
    const savedUser = sessionStorage.getItem('campusshare_user')
    return savedUser ? JSON.parse(savedUser) : null
  })

  const isAuthenticated = user !== null

  const login = (userData: User) => {
    setUser(userData)
    sessionStorage.setItem('campusshare_user', JSON.stringify(userData))
  }

  const logout = () => {
    setUser(null)
    sessionStorage.removeItem('campusshare_user')
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, logout }}>
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