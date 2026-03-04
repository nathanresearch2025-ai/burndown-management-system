import api from './axios'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  fullName?: string
  roleId?: number
}

export interface AuthResponse {
  token: string
  user: {
    id: number
    username: string
    email: string
    fullName: string
  }
}

export const authApi = {
  login: (data: LoginRequest) => api.post<AuthResponse>('/auth/login', data),
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data),
}
