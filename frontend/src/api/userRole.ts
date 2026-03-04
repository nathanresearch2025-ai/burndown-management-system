import api from './axios'
import { RoleResponse } from './role'
import { PermissionResponse } from './permission'

export interface AssignRolesRequest {
  roleIds: number[]
}

export const userRoleApi = {
  getUserRoles: (userId: number) => api.get<RoleResponse[]>(`/users/${userId}/roles`),
  getUserPermissions: (userId: number) => api.get<PermissionResponse[]>(`/users/${userId}/permissions`),
  assignRoles: (userId: number, data: AssignRolesRequest) => api.post(`/users/${userId}/roles`, data),
  removeRole: (userId: number, roleId: number) => api.delete(`/users/${userId}/roles/${roleId}`),
}
