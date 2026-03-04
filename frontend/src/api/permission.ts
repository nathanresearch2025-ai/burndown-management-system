import api from './axios'

export interface PermissionResponse {
  id: number
  name: string
  code: string
  resource: string
  action: string
  description?: string
}

export const permissionApi = {
  getAllPermissions: () => api.get<PermissionResponse[]>('/permissions'),
  getPermissionsByResource: (resource: string) => api.get<PermissionResponse[]>(`/permissions/resource/${resource}`),
}
