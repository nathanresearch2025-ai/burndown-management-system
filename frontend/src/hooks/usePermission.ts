import { useAuthStore } from '../store/authStore';

/**
 * 检查用户是否拥有指定权限
 * @param permission 权限编码，如 'PROJECT:CREATE'
 * @returns 是否拥有该权限
 */
export const usePermission = (permission: string): boolean => {
  const permissions = useAuthStore(state => state.permissions);
  return permissions?.includes(permission) || false;
};

/**
 * 检查用户是否拥有任意一个权限
 * @param permissions 权限编码数组
 * @returns 是否拥有任意一个权限
 */
export const useHasAnyPermission = (permissions: string[]): boolean => {
  const userPermissions = useAuthStore(state => state.permissions);
  if (!userPermissions || userPermissions.length === 0) return false;
  return permissions.some(p => userPermissions.includes(p));
};

/**
 * 检查用户是否拥有所有权限
 * @param permissions 权限编码数组
 * @returns 是否拥有所有权限
 */
export const useHasAllPermissions = (permissions: string[]): boolean => {
  const userPermissions = useAuthStore(state => state.permissions);
  if (!userPermissions || userPermissions.length === 0) return false;
  return permissions.every(p => userPermissions.includes(p));
};
