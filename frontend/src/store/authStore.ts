import { create } from 'zustand'

interface User {
  id: number
  username: string
  email: string
  fullName: string
}

interface AuthState {
  user: User | null
  token: string | null
  permissions: string[]
  setAuth: (user: User, token: string, permissions?: string[]) => void
  logout: () => void
}

// 从JWT Token中解析权限
const parsePermissionsFromToken = (token: string): string[] => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.permissions || []
  } catch (error) {
    return []
  }
}

export const useAuthStore = create<AuthState>((set) => {
  const storedToken = localStorage.getItem('token')
  const initialPermissions = storedToken ? parsePermissionsFromToken(storedToken) : []

  return {
    user: null,
    token: storedToken,
    permissions: initialPermissions,
    setAuth: (user, token, permissions) => {
      localStorage.setItem('token', token)
      const perms = permissions || parsePermissionsFromToken(token)
      set({ user, token, permissions: perms })
    },
    logout: () => {
      localStorage.removeItem('token')
      set({ user: null, token: null, permissions: [] })
    },
  }
})
