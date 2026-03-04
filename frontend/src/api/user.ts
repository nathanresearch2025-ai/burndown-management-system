import api from './axios'

export interface User {
  id: number
  username: string
  email: string
  fullName: string
  avatarUrl: string
  createdAt: string
}

export const userApi = {
  getAll: () => api.get<User[]>('/users'),
  getById: (id: number) => api.get<User>(`/users/${id}`),
}

export const getUsers = async () => {
  const response = await userApi.getAll()
  return response.data
}
