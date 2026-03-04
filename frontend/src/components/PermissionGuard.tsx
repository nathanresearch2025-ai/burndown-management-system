import React from 'react';
import { usePermission } from '../hooks/usePermission';

interface PermissionGuardProps {
  permission: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

/**
 * 权限守卫组件
 * 根据用户权限决定是否渲染子组件
 */
export const PermissionGuard: React.FC<PermissionGuardProps> = ({
  permission,
  children,
  fallback = null
}) => {
  const hasPermission = usePermission(permission);
  return hasPermission ? <>{children}</> : <>{fallback}</>;
};

export default PermissionGuard;
