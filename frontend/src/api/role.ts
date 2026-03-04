import api from './axios'

export interface PermissionResponse {
  id: number
  name: string
  code: string
  resource: string
  action: string
  description?: string
}

export interface RoleResponse {
  id: number
  name: string
  code: string
  description?: string
  isSystem: boolean
  isActive: boolean
  permissions?: PermissionResponse[]
}

export interface CreateRoleRequest {
  name: string
  code: string
  description?: string
  isActive: boolean
  permissionIds?: number[]
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
  isActive?: boolean
  permissionIds?: number[]
}

export const roleApi = {
  getAvailableRoles: () => api.get<RoleResponse[]>('/roles/available'),
  getAllRoles: () => api.get<RoleResponse[]>('/roles'),
  getRoleById: (id: number) => api.get<RoleResponse>(`/roles/${id}`),
  createRole: (data: CreateRoleRequest) => api.post<RoleResponse>('/roles', data),
  updateRole: (id: number, data: UpdateRoleRequest) => api.put<RoleResponse>(`/roles/${id}`, data),
  deleteRole: (id: number) => api.delete(`/roles/${id}`),
}
