-- 给项目经理角色添加角色管理权限
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER'),
    (SELECT id FROM permissions WHERE code = 'ROLE:MANAGE'),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions 
    WHERE role_id = (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER')
    AND permission_id = (SELECT id FROM permissions WHERE code = 'ROLE:MANAGE')
);
