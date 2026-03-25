-- Auth Service Schema
CREATE SCHEMA IF NOT EXISTS ms_auth;

SET search_path TO ms_auth;

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(200) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url   VARCHAR(500),
    is_active    BOOLEAN     DEFAULT TRUE,
    created_at   TIMESTAMP   DEFAULT NOW(),
    updated_at   TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_users_username  ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email     ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);

-- Seed data
INSERT INTO permissions (code, name) VALUES
    ('PROJECT_VIEW',   '查看项目'),
    ('PROJECT_CREATE', '创建项目'),
    ('PROJECT_EDIT',   '编辑项目'),
    ('PROJECT_DELETE', '删除项目'),
    ('SPRINT_MANAGE',  '管理Sprint'),
    ('TASK_VIEW',      '查看任务'),
    ('TASK_CREATE',    '创建任务'),
    ('TASK_EDIT',      '编辑任务'),
    ('TASK_DELETE',    '删除任务'),
    ('USER_MANAGE',    '管理用户')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code, name) VALUES
    ('ADMIN',      '管理员'),
    ('DEVELOPER',  '开发者'),
    ('VIEWER',     '观察者')
ON CONFLICT (code) DO NOTHING;

-- Admin gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Developer permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'DEVELOPER'
  AND p.code IN ('PROJECT_VIEW','SPRINT_MANAGE','TASK_VIEW','TASK_CREATE','TASK_EDIT')
ON CONFLICT DO NOTHING;

-- Viewer permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'VIEWER'
  AND p.code IN ('PROJECT_VIEW','TASK_VIEW')
ON CONFLICT DO NOTHING;

-- Default admin user (password: admin123)
INSERT INTO users (username, email, password, display_name, is_active) VALUES
    ('admin', 'admin@burndown.com', '$2a$04$oFCexP1rFTJqpVRLpH5XjOBt.yMFpPtb2P4hT9JvivKpSv8brkHqy', '系统管理员', true)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
