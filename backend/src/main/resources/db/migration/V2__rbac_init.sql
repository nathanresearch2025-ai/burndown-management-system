-- =============================================
-- RBAC 数据初始化脚本
-- =============================================

-- 1. 插入系统角色
INSERT INTO roles (name, code, description, is_system, is_active, created_at, updated_at) VALUES
('系统管理员', 'ROLE_ADMIN', '拥有系统所有权限', true, true, NOW(), NOW()),
('项目经理', 'ROLE_PROJECT_MANAGER', '负责项目管理、Sprint规划和团队协调', true, true, NOW(), NOW()),
('开发人员', 'ROLE_DEVELOPER', '负责任务开发和工时记录', true, true, NOW(), NOW()),
('测试人员', 'ROLE_TESTER', '负责测试任务和缺陷管理', true, true, NOW(), NOW()),
('只读用户', 'ROLE_VIEWER', '只能查看项目和任务信息', true, true, NOW(), NOW());

-- 2. 插入权限 - 项目管理
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('创建项目', 'PROJECT:CREATE', 'PROJECT', 'CREATE', '创建新项目', NOW()),
('编辑项目', 'PROJECT:UPDATE', 'PROJECT', 'UPDATE', '修改项目信息', NOW()),
('删除项目', 'PROJECT:DELETE', 'PROJECT', 'DELETE', '删除项目', NOW()),
('查看项目', 'PROJECT:VIEW', 'PROJECT', 'VIEW', '查看项目详情', NOW()),
('项目列表', 'PROJECT:LIST', 'PROJECT', 'LIST', '查看项目列表', NOW());

-- 3. 插入权限 - Sprint管理
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('创建Sprint', 'SPRINT:CREATE', 'SPRINT', 'CREATE', '创建新Sprint', NOW()),
('编辑Sprint', 'SPRINT:UPDATE', 'SPRINT', 'UPDATE', '修改Sprint信息', NOW()),
('删除Sprint', 'SPRINT:DELETE', 'SPRINT', 'DELETE', '删除Sprint', NOW()),
('启动Sprint', 'SPRINT:START', 'SPRINT', 'START', '启动Sprint', NOW()),
('完成Sprint', 'SPRINT:COMPLETE', 'SPRINT', 'COMPLETE', '完成Sprint', NOW()),
('查看Sprint', 'SPRINT:VIEW', 'SPRINT', 'VIEW', '查看Sprint详情', NOW());

-- 4. 插入权限 - 任务管理
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('创建任务', 'TASK:CREATE', 'TASK', 'CREATE', '创建新任务', NOW()),
('编辑任务', 'TASK:UPDATE', 'TASK', 'UPDATE', '修改任务信息', NOW()),
('删除任务', 'TASK:DELETE', 'TASK', 'DELETE', '删除任务', NOW()),
('分配任务', 'TASK:ASSIGN', 'TASK', 'ASSIGN', '分配任务给用户', NOW()),
('修改状态', 'TASK:STATUS_CHANGE', 'TASK', 'STATUS_CHANGE', '修改任务状态', NOW()),
('查看任务', 'TASK:VIEW', 'TASK', 'VIEW', '查看任务详情', NOW());

-- 5. 插入权限 - 工时管理
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('记录工时', 'WORKLOG:CREATE', 'WORKLOG', 'CREATE', '记录工作时间', NOW()),
('编辑工时', 'WORKLOG:UPDATE', 'WORKLOG', 'UPDATE', '修改工时记录', NOW()),
('删除工时', 'WORKLOG:DELETE', 'WORKLOG', 'DELETE', '删除工时记录', NOW()),
('查看自己工时', 'WORKLOG:VIEW_OWN', 'WORKLOG', 'VIEW_OWN', '查看自己的工时', NOW()),
('查看所有工时', 'WORKLOG:VIEW_ALL', 'WORKLOG', 'VIEW_ALL', '查看所有人工时', NOW());

-- 6. 插入权限 - 报表
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('燃尽图', 'REPORT:BURNDOWN', 'REPORT', 'BURNDOWN', '查看燃尽图', NOW()),
('速度图', 'REPORT:VELOCITY', 'REPORT', 'VELOCITY', '查看速度图', NOW()),
('导出报表', 'REPORT:EXPORT', 'REPORT', 'EXPORT', '导出报表数据', NOW());

-- 7. 插入权限 - 系统管理
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
('创建用户', 'USER:CREATE', 'USER', 'CREATE', '创建新用户', NOW()),
('编辑用户', 'USER:UPDATE', 'USER', 'UPDATE', '修改用户信息', NOW()),
('删除用户', 'USER:DELETE', 'USER', 'DELETE', '删除用户', NOW()),
('查看用户', 'USER:VIEW', 'USER', 'VIEW', '查看用户信息', NOW()),
('角色管理', 'ROLE:MANAGE', 'ROLE', 'MANAGE', '管理角色和权限', NOW()),
('分配角色', 'ROLE:ASSIGN', 'ROLE', 'ASSIGN', '给用户分配角色', NOW());

-- 8. 角色权限关联 - 系统管理员（所有权限）
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'),
    id,
    NOW()
FROM permissions;

-- 9. 角色权限关联 - 项目经理
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER'),
    id,
    NOW()
FROM permissions
WHERE code IN (
    'PROJECT:CREATE', 'PROJECT:UPDATE', 'PROJECT:DELETE', 'PROJECT:VIEW', 'PROJECT:LIST',
    'SPRINT:CREATE', 'SPRINT:UPDATE', 'SPRINT:DELETE', 'SPRINT:START', 'SPRINT:COMPLETE', 'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:DELETE', 'TASK:ASSIGN', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:VIEW_ALL',
    'REPORT:BURNDOWN', 'REPORT:VELOCITY', 'REPORT:EXPORT',
    'USER:VIEW'
);

-- 10. 角色权限关联 - 开发人员
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'),
    id,
    NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST',
    'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:CREATE', 'WORKLOG:UPDATE', 'WORKLOG:DELETE', 'WORKLOG:VIEW_OWN',
    'REPORT:BURNDOWN', 'REPORT:VELOCITY'
);

-- 11. 角色权限关联 - 测试人员
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_TESTER'),
    id,
    NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST',
    'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:CREATE', 'WORKLOG:UPDATE', 'WORKLOG:DELETE', 'WORKLOG:VIEW_OWN',
    'REPORT:BURNDOWN'
);

-- 12. 角色权限关联 - 只读用户
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_VIEWER'),
    id,
    NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST',
    'SPRINT:VIEW',
    'TASK:VIEW',
    'WORKLOG:VIEW_OWN',
    'REPORT:BURNDOWN'
);

-- 13. 为现有用户分配默认角色（可选）
-- 如果有默认管理员用户，为其分配管理员角色
-- INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
-- SELECT
--     (SELECT id FROM users WHERE username = 'admin'),
--     (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'),
--     (SELECT id FROM users WHERE username = 'admin'),
--     NOW()
-- WHERE EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- 完成
SELECT 'RBAC初始化完成' AS status,
       (SELECT COUNT(*) FROM roles) AS roles_count,
       (SELECT COUNT(*) FROM permissions) AS permissions_count,
       (SELECT COUNT(*) FROM role_permissions) AS role_permissions_count;
