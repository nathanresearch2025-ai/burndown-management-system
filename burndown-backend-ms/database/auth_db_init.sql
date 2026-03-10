-- =============================================
-- Auth Database Initialization Script
-- =============================================

-- Drop existing tables if they exist
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- =============================================
-- 1. Users Table
-- =============================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    avatar_url VARCHAR(500),
    phone VARCHAR(20),
    timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',
    language VARCHAR(10) DEFAULT 'zh_CN',
    is_active BOOLEAN DEFAULT true,
    is_email_verified BOOLEAN DEFAULT false,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);

-- =============================================
-- 2. Roles Table
-- =============================================
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_system BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_roles_code ON roles(code);
CREATE INDEX idx_roles_is_active ON roles(is_active);

-- =============================================
-- 3. Permissions Table
-- =============================================
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_code ON permissions(code);
CREATE INDEX idx_permissions_resource ON permissions(resource);

-- =============================================
-- 4. User Roles Association Table
-- =============================================
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by BIGINT,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- =============================================
-- 5. Role Permissions Association Table
-- =============================================
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- =============================================
-- 6. Insert System Roles
-- =============================================
INSERT INTO roles (name, code, description, is_system, is_active, created_at, updated_at) VALUES
('系统管理员', 'ROLE_ADMIN', '拥有系统所有权限', true, true, NOW(), NOW()),
('项目经理', 'ROLE_PROJECT_MANAGER', '负责项目管理、Sprint规划和团队协调', true, true, NOW(), NOW()),
('开发人员', 'ROLE_DEVELOPER', '负责任务开发和工时记录', true, true, NOW(), NOW()),
('测试人员', 'ROLE_TESTER', '负责测试任务和缺陷管理', true, true, NOW(), NOW()),
('只读用户', 'ROLE_VIEWER', '只能查看项目和任务信息', true, true, NOW(), NOW());

-- =============================================
-- 7. Insert Permissions
-- =============================================
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
-- Project permissions
('创建项目', 'PROJECT:CREATE', 'PROJECT', 'CREATE', '创建新项目', NOW()),
('编辑项目', 'PROJECT:UPDATE', 'PROJECT', 'UPDATE', '修改项目信息', NOW()),
('删除项目', 'PROJECT:DELETE', 'PROJECT', 'DELETE', '删除项目', NOW()),
('查看项目', 'PROJECT:VIEW', 'PROJECT', 'VIEW', '查看项目详情', NOW()),
('项目列表', 'PROJECT:LIST', 'PROJECT', 'LIST', '查看项目列表', NOW()),
-- Sprint permissions
('创建Sprint', 'SPRINT:CREATE', 'SPRINT', 'CREATE', '创建新Sprint', NOW()),
('编辑Sprint', 'SPRINT:UPDATE', 'SPRINT', 'UPDATE', '修改Sprint信息', NOW()),
('删除Sprint', 'SPRINT:DELETE', 'SPRINT', 'DELETE', '删除Sprint', NOW()),
('启动Sprint', 'SPRINT:START', 'SPRINT', 'START', '启动Sprint', NOW()),
('完成Sprint', 'SPRINT:COMPLETE', 'SPRINT', 'COMPLETE', '完成Sprint', NOW()),
('查看Sprint', 'SPRINT:VIEW', 'SPRINT', 'VIEW', '查看Sprint详情', NOW()),
-- Task permissions
('创建任务', 'TASK:CREATE', 'TASK', 'CREATE', '创建新任务', NOW()),
('编辑任务', 'TASK:UPDATE', 'TASK', 'UPDATE', '修改任务信息', NOW()),
('删除任务', 'TASK:DELETE', 'TASK', 'DELETE', '删除任务', NOW()),
('分配任务', 'TASK:ASSIGN', 'TASK', 'ASSIGN', '分配任务给用户', NOW()),
('修改状态', 'TASK:STATUS_CHANGE', 'TASK', 'STATUS_CHANGE', '修改任务状态', NOW()),
('查看任务', 'TASK:VIEW', 'TASK', 'VIEW', '查看任务详情', NOW()),
-- WorkLog permissions
('记录工时', 'WORKLOG:CREATE', 'WORKLOG', 'CREATE', '记录工作时间', NOW()),
('编辑工时', 'WORKLOG:UPDATE', 'WORKLOG', 'UPDATE', '修改工时记录', NOW()),
('删除工时', 'WORKLOG:DELETE', 'WORKLOG', 'DELETE', '删除工时记录', NOW()),
('查看自己工时', 'WORKLOG:VIEW_OWN', 'WORKLOG', 'VIEW_OWN', '查看自己的工时', NOW()),
('查看所有工时', 'WORKLOG:VIEW_ALL', 'WORKLOG', 'VIEW_ALL', '查看所有人工时', NOW()),
-- Report permissions
('燃尽图', 'REPORT:BURNDOWN', 'REPORT', 'BURNDOWN', '查看燃尽图', NOW()),
('速度图', 'REPORT:VELOCITY', 'REPORT', 'VELOCITY', '查看速度图', NOW()),
('导出报表', 'REPORT:EXPORT', 'REPORT', 'EXPORT', '导出报表数据', NOW()),
-- System permissions
('创建用户', 'USER:CREATE', 'USER', 'CREATE', '创建新用户', NOW()),
('编辑用户', 'USER:UPDATE', 'USER', 'UPDATE', '修改用户信息', NOW()),
('删除用户', 'USER:DELETE', 'USER', 'DELETE', '删除用户', NOW()),
('查看用户', 'USER:VIEW', 'USER', 'VIEW', '查看用户信息', NOW()),
('角色管理', 'ROLE:MANAGE', 'ROLE', 'MANAGE', '管理角色和权限', NOW()),
('分配角色', 'ROLE:ASSIGN', 'ROLE', 'ASSIGN', '给用户分配角色', NOW());

-- =============================================
-- 8. Assign Permissions to Roles
-- =============================================
-- Admin gets all permissions
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'), id, NOW() FROM permissions;

-- Project Manager permissions
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER'), id, NOW()
FROM permissions
WHERE code IN (
    'PROJECT:CREATE', 'PROJECT:UPDATE', 'PROJECT:DELETE', 'PROJECT:VIEW', 'PROJECT:LIST',
    'SPRINT:CREATE', 'SPRINT:UPDATE', 'SPRINT:DELETE', 'SPRINT:START', 'SPRINT:COMPLETE', 'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:DELETE', 'TASK:ASSIGN', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:VIEW_ALL', 'REPORT:BURNDOWN', 'REPORT:VELOCITY', 'REPORT:EXPORT', 'USER:VIEW'
);

-- Developer permissions
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'), id, NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST', 'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:CREATE', 'WORKLOG:UPDATE', 'WORKLOG:DELETE', 'WORKLOG:VIEW_OWN',
    'REPORT:BURNDOWN', 'REPORT:VELOCITY'
);

-- Tester permissions
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT (SELECT id FROM roles WHERE code = 'ROLE_TESTER'), id, NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST', 'SPRINT:VIEW',
    'TASK:CREATE', 'TASK:UPDATE', 'TASK:STATUS_CHANGE', 'TASK:VIEW',
    'WORKLOG:CREATE', 'WORKLOG:UPDATE', 'WORKLOG:DELETE', 'WORKLOG:VIEW_OWN',
    'REPORT:BURNDOWN'
);

-- Viewer permissions
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT (SELECT id FROM roles WHERE code = 'ROLE_VIEWER'), id, NOW()
FROM permissions
WHERE code IN (
    'PROJECT:VIEW', 'PROJECT:LIST', 'SPRINT:VIEW', 'TASK:VIEW',
    'WORKLOG:VIEW_OWN', 'REPORT:BURNDOWN'
);

-- =============================================
-- 9. Insert Sample Users
-- =============================================
-- Password: password123 (BCrypt strength 4)
INSERT INTO users (username, email, password_hash, full_name, is_active, created_at, updated_at) VALUES
('admin', 'admin@burndown.com', '$2b$04$.T808Qy4HKn49CfViZ/YqOH570gC1UFQYVNEQKuUux.l9SIyOrRey', '系统管理员', true, NOW(), NOW()),
('pm_zhang', 'zhang@burndown.com', '$2b$04$z9tG.1gEjWKxeaMxEqGtn.Q.0fF9DVYOIBTqtgK/WQdQZf6f.339S', '张项目经理', true, NOW(), NOW()),
('dev_li', 'li@burndown.com', '$2b$04$Q1ZjUdIWPn/CHyRnqtijMewobiXltns.DU/.biXedaSYLxSSZqGIG', '李开发', true, NOW(), NOW()),
('dev_wang', 'wang@burndown.com', '$2b$04$c/g.DicixahteVXQBX/Ce.WGhAKbBI6tZFuwi91Jzg6EjyhsWvMIe', '王开发', true, NOW(), NOW()),
('tester_zhao', 'zhao@burndown.com', '$2b$04$ShyNFxQQakT/6ji5bBFhMuD7/tq0j1n4vLRRB5Oapiz5jCPXa92rW', '赵测试', true, NOW(), NOW()),
('viewer_liu', 'liu@burndown.com', '$2b$04$VUMM47Qw3kJStAXs2VH4aOyvzU5.xmJV74Ae8KkAf9iEu805ozRCi', '刘观察员', true, NOW(), NOW());

-- =============================================
-- 10. Assign Roles to Users
-- =============================================
INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at) VALUES
((SELECT id FROM users WHERE username = 'admin'), (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
((SELECT id FROM users WHERE username = 'pm_zhang'), (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
((SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
((SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
((SELECT id FROM users WHERE username = 'tester_zhao'), (SELECT id FROM roles WHERE code = 'ROLE_TESTER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
((SELECT id FROM users WHERE username = 'viewer_liu'), (SELECT id FROM roles WHERE code = 'ROLE_VIEWER'), (SELECT id FROM users WHERE username = 'admin'), NOW());

-- =============================================
-- 11. Summary
-- =============================================
SELECT 'Auth database initialization completed!' AS status,
       (SELECT COUNT(*) FROM users) AS users_count,
       (SELECT COUNT(*) FROM roles) AS roles_count,
       (SELECT COUNT(*) FROM permissions) AS permissions_count,
       (SELECT COUNT(*) FROM role_permissions) AS role_permissions_count,
       (SELECT COUNT(*) FROM user_roles) AS user_roles_count;
