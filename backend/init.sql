-- =============================================
-- Burndown Management System - Complete Database Initialization
-- =============================================

-- Drop existing tables if they exist (in reverse order of dependencies)
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS burndown_points CASCADE;
DROP TABLE IF EXISTS work_logs CASCADE;
DROP TABLE IF EXISTS ai_task_generation_logs CASCADE;
DROP TABLE IF EXISTS tasks CASCADE;
DROP TABLE IF EXISTS sprints CASCADE;
DROP TABLE IF EXISTS projects CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- =============================================
-- 1. Core Tables
-- =============================================

-- Users table
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

-- Projects table
CREATE TABLE projects (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(200) NOT NULL,
                          description TEXT,
                          project_key VARCHAR(20) NOT NULL UNIQUE,
                          type VARCHAR(20) NOT NULL DEFAULT 'SCRUM',
                          visibility VARCHAR(20) DEFAULT 'PRIVATE',
                          owner_id BIGINT NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          start_date DATE,
                          end_date DATE,
                          settings JSONB DEFAULT '{}'::jsonb,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_project_key ON projects(project_key);

-- Sprints table
CREATE TABLE sprints (
                         id BIGSERIAL PRIMARY KEY,
                         project_id BIGINT NOT NULL,
                         name VARCHAR(200) NOT NULL,
                         goal TEXT,
                         start_date DATE NOT NULL,
                         end_date DATE NOT NULL,
                         status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
                         total_capacity DECIMAL(10,2),
                         committed_points DECIMAL(10,2),
                         completed_points DECIMAL(10,2) DEFAULT 0,
                         velocity DECIMAL(10,2),
                         started_at TIMESTAMP,
                         completed_at TIMESTAMP,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_sprints_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_sprints_project_id ON sprints(project_id);
CREATE INDEX idx_sprints_status ON sprints(status);

-- Tasks table
CREATE TABLE tasks (
                       id BIGSERIAL PRIMARY KEY,
                       project_id BIGINT NOT NULL,
                       sprint_id BIGINT,
                       parent_id BIGINT,
                       task_key VARCHAR(50) NOT NULL UNIQUE,
                       title VARCHAR(500) NOT NULL,
                       description TEXT,
                       type VARCHAR(20) NOT NULL DEFAULT 'TASK',
                       status VARCHAR(20) NOT NULL DEFAULT 'TODO',
                       priority VARCHAR(20) DEFAULT 'MEDIUM',
                       story_points NUMERIC(5,1),
                       original_estimate NUMERIC(10,2),
                       remaining_estimate NUMERIC(10,2),
                       time_spent NUMERIC(10,2) DEFAULT 0,
                       assignee_id BIGINT,
                       reporter_id BIGINT NOT NULL,
                       labels TEXT[],
                       custom_fields JSONB DEFAULT '{}'::jsonb,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       resolved_at TIMESTAMP,
                       due_date DATE,
                       CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_sprint FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL,
                       CONSTRAINT fk_tasks_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_parent FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id);
CREATE INDEX idx_tasks_sprint_id ON tasks(sprint_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_task_key ON tasks(task_key);

-- Work logs table
CREATE TABLE work_logs (
                           id BIGSERIAL PRIMARY KEY,
                           task_id BIGINT NOT NULL,
                           user_id BIGINT NOT NULL,
                           work_date DATE NOT NULL,
                           time_spent DECIMAL(10,2) NOT NULL,
                           remaining_estimate DECIMAL(10,2),
                           comment TEXT,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_work_logs_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
                           CONSTRAINT fk_work_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                           CONSTRAINT uk_work_logs_task_user_date UNIQUE (task_id, user_id, work_date)
);

CREATE INDEX idx_work_logs_task_id ON work_logs(task_id);
CREATE INDEX idx_work_logs_user_id ON work_logs(user_id);
CREATE INDEX idx_work_logs_work_date ON work_logs(work_date);

-- AI task generation logs table
CREATE TABLE ai_task_generation_logs (
                                       id BIGSERIAL PRIMARY KEY,
                                       project_id BIGINT NOT NULL,
                                       user_id BIGINT NOT NULL,
                                       title VARCHAR(500) NOT NULL,
                                       request_payload JSONB NOT NULL,
                                       response_description TEXT,
                                       similar_task_ids JSONB,
                                       is_accepted BOOLEAN,
                                       feedback_rating INTEGER CHECK (feedback_rating >= 1 AND feedback_rating <= 5),
                                       feedback_comment TEXT,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       CONSTRAINT fk_ai_task_generation_logs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_ai_task_generation_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_task_generation_logs_project_id ON ai_task_generation_logs(project_id);
CREATE INDEX idx_ai_task_generation_logs_user_id ON ai_task_generation_logs(user_id);
CREATE INDEX idx_ai_task_generation_logs_created_at ON ai_task_generation_logs(created_at);
CREATE INDEX idx_ai_task_generation_logs_is_accepted ON ai_task_generation_logs(is_accepted);

-- Burndown points table
CREATE TABLE burndown_points (
                                 id BIGSERIAL PRIMARY KEY,
                                 sprint_id BIGINT NOT NULL,
                                 point_date DATE NOT NULL,
                                 ideal_remaining DECIMAL(10,2) NOT NULL,
                                 actual_remaining DECIMAL(10,2) NOT NULL,
                                 completed_points DECIMAL(10,2) DEFAULT 0,
                                 scope_change DECIMAL(10,2) DEFAULT 0,
                                 total_tasks INTEGER DEFAULT 0,
                                 completed_tasks INTEGER DEFAULT 0,
                                 in_progress_tasks INTEGER DEFAULT 0,
                                 calculated_at TIMESTAMP,
                                 CONSTRAINT fk_burndown_points_sprint FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE CASCADE,
                                 CONSTRAINT uk_burndown_sprint_date UNIQUE (sprint_id, point_date)
);

CREATE INDEX idx_burndown_points_sprint_id ON burndown_points(sprint_id);
CREATE INDEX idx_burndown_points_date ON burndown_points(point_date);

-- =============================================
-- 2. RBAC Tables
-- =============================================

-- Roles table
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

-- Permissions table
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

-- User roles association table
CREATE TABLE user_roles (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            assigned_by BIGINT,
                            assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE SET NULL,
                            CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Role permissions association table
CREATE TABLE role_permissions (
                                  id BIGSERIAL PRIMARY KEY,
                                  role_id BIGINT NOT NULL,
                                  permission_id BIGINT NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
                                  CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- =============================================
-- 3. RBAC Data Initialization
-- =============================================

-- Insert system roles
INSERT INTO roles (name, code, description, is_system, is_active, created_at, updated_at) VALUES
                                                                                              ('系统管理员', 'ROLE_ADMIN', '拥有系统所有权限', true, true, NOW(), NOW()),
                                                                                              ('项目经理', 'ROLE_PROJECT_MANAGER', '负责项目管理、Sprint规划和团队协调', true, true, NOW(), NOW()),
                                                                                              ('开发人员', 'ROLE_DEVELOPER', '负责任务开发和工时记录', true, true, NOW(), NOW()),
                                                                                              ('测试人员', 'ROLE_TESTER', '负责测试任务和缺陷管理', true, true, NOW(), NOW()),
                                                                                              ('只读用户', 'ROLE_VIEWER', '只能查看项目和任务信息', true, true, NOW(), NOW());

-- Insert permissions - Project management
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('创建项目', 'PROJECT:CREATE', 'PROJECT', 'CREATE', '创建新项目', NOW()),
                                                                                    ('编辑项目', 'PROJECT:UPDATE', 'PROJECT', 'UPDATE', '修改项目信息', NOW()),
                                                                                    ('删除项目', 'PROJECT:DELETE', 'PROJECT', 'DELETE', '删除项目', NOW()),
                                                                                    ('查看项目', 'PROJECT:VIEW', 'PROJECT', 'VIEW', '查看项目详情', NOW()),
                                                                                    ('项目列表', 'PROJECT:LIST', 'PROJECT', 'LIST', '查看项目列表', NOW());

-- Insert permissions - Sprint management
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('创建Sprint', 'SPRINT:CREATE', 'SPRINT', 'CREATE', '创建新Sprint', NOW()),
                                                                                    ('编辑Sprint', 'SPRINT:UPDATE', 'SPRINT', 'UPDATE', '修改Sprint信息', NOW()),
                                                                                    ('删除Sprint', 'SPRINT:DELETE', 'SPRINT', 'DELETE', '删除Sprint', NOW()),
                                                                                    ('启动Sprint', 'SPRINT:START', 'SPRINT', 'START', '启动Sprint', NOW()),
                                                                                    ('完成Sprint', 'SPRINT:COMPLETE', 'SPRINT', 'COMPLETE', '完成Sprint', NOW()),
                                                                                    ('查看Sprint', 'SPRINT:VIEW', 'SPRINT', 'VIEW', '查看Sprint详情', NOW());

-- Insert permissions - Task management
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('创建任务', 'TASK:CREATE', 'TASK', 'CREATE', '创建新任务', NOW()),
                                                                                    ('编辑任务', 'TASK:UPDATE', 'TASK', 'UPDATE', '修改任务信息', NOW()),
                                                                                    ('删除任务', 'TASK:DELETE', 'TASK', 'DELETE', '删除任务', NOW()),
                                                                                    ('分配任务', 'TASK:ASSIGN', 'TASK', 'ASSIGN', '分配任务给用户', NOW()),
                                                                                    ('修改状态', 'TASK:STATUS_CHANGE', 'TASK', 'STATUS_CHANGE', '修改任务状态', NOW()),
                                                                                    ('查看任务', 'TASK:VIEW', 'TASK', 'VIEW', '查看任务详情', NOW());

-- Insert permissions - Work log management
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('记录工时', 'WORKLOG:CREATE', 'WORKLOG', 'CREATE', '记录工作时间', NOW()),
                                                                                    ('编辑工时', 'WORKLOG:UPDATE', 'WORKLOG', 'UPDATE', '修改工时记录', NOW()),
                                                                                    ('删除工时', 'WORKLOG:DELETE', 'WORKLOG', 'DELETE', '删除工时记录', NOW()),
                                                                                    ('查看自己工时', 'WORKLOG:VIEW_OWN', 'WORKLOG', 'VIEW_OWN', '查看自己的工时', NOW()),
                                                                                    ('查看所有工时', 'WORKLOG:VIEW_ALL', 'WORKLOG', 'VIEW_ALL', '查看所有人工时', NOW());

-- Insert permissions - Reports
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('燃尽图', 'REPORT:BURNDOWN', 'REPORT', 'BURNDOWN', '查看燃尽图', NOW()),
                                                                                    ('速度图', 'REPORT:VELOCITY', 'REPORT', 'VELOCITY', '查看速度图', NOW()),
                                                                                    ('导出报表', 'REPORT:EXPORT', 'REPORT', 'EXPORT', '导出报表数据', NOW());

-- Insert permissions - System management
INSERT INTO permissions (name, code, resource, action, description, created_at) VALUES
                                                                                    ('创建用户', 'USER:CREATE', 'USER', 'CREATE', '创建新用户', NOW()),
                                                                                    ('编辑用户', 'USER:UPDATE', 'USER', 'UPDATE', '修改用户信息', NOW()),
                                                                                    ('删除用户', 'USER:DELETE', 'USER', 'DELETE', '删除用户', NOW()),
                                                                                    ('查看用户', 'USER:VIEW', 'USER', 'VIEW', '查看用户信息', NOW()),
                                                                                    ('角色管理', 'ROLE:MANAGE', 'ROLE', 'MANAGE', '管理角色和权限', NOW()),
                                                                                    ('分配角色', 'ROLE:ASSIGN', 'ROLE', 'ASSIGN', '给用户分配角色', NOW());

-- Role-Permission associations - System Admin (all permissions)
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT
    (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'),
    id,
    NOW()
FROM permissions;

-- Role-Permission associations - Project Manager
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

-- Role-Permission associations - Developer
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

-- Role-Permission associations - Tester
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

-- Role-Permission associations - Viewer
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

-- =============================================
-- 4. Sample Data Initialization
-- =============================================

-- Insert sample users (password is 'password123' hashed with BCrypt)
-- BCrypt hash for 'password123': $2b$10$D5.nJjsSDvkN6V1j9PYN/.qFVl.IO1cJWLuHd58ImhgfdJro7nJK6
-- 注意：密码hash使用BCrypt强度4（用于测试/开发环境，生产环境建议使用强度6-8）
-- admin密码: password123
-- 其他用户密码: Test123456
INSERT INTO users (username, email, password_hash, full_name, is_active, created_at, updated_at) VALUES
                                                                                                     ('admin', 'admin@burndown.com', '$2b$04$.T808Qy4HKn49CfViZ/YqOH570gC1UFQYVNEQKuUux.l9SIyOrRey', '系统管理员', true, NOW(), NOW()),
                                                                                                     ('pm_zhang', 'zhang@burndown.com', '$2b$04$z9tG.1gEjWKxeaMxEqGtn.Q.0fF9DVYOIBTqtgK/WQdQZf6f.339S', '张项目经理', true, NOW(), NOW()),
                                                                                                     ('dev_li', 'li@burndown.com', '$2b$04$Q1ZjUdIWPn/CHyRnqtijMewobiXltns.DU/.biXedaSYLxSSZqGIG', '李开发', true, NOW(), NOW()),
                                                                                                     ('dev_wang', 'wang@burndown.com', '$2b$04$c/g.DicixahteVXQBX/Ce.WGhAKbBI6tZFuwi91Jzg6EjyhsWvMIe', '王开发', true, NOW(), NOW()),
                                                                                                     ('tester_zhao', 'zhao@burndown.com', '$2b$04$ShyNFxQQakT/6ji5bBFhMuD7/tq0j1n4vLRRB5Oapiz5jCPXa92rW', '赵测试', true, NOW(), NOW()),
                                                                                                     ('viewer_liu', 'liu@burndown.com', '$2b$04$VUMM47Qw3kJStAXs2VH4aOyvzU5.xmJV74Ae8KkAf9iEu805ozRCi', '刘观察员', true, NOW(), NOW());

-- Assign roles to users
INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at) VALUES
                                                                        ((SELECT id FROM users WHERE username = 'admin'), (SELECT id FROM roles WHERE code = 'ROLE_ADMIN'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
                                                                        ((SELECT id FROM users WHERE username = 'pm_zhang'), (SELECT id FROM roles WHERE code = 'ROLE_PROJECT_MANAGER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
                                                                        ((SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
                                                                        ((SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM roles WHERE code = 'ROLE_DEVELOPER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
                                                                        ((SELECT id FROM users WHERE username = 'tester_zhao'), (SELECT id FROM roles WHERE code = 'ROLE_TESTER'), (SELECT id FROM users WHERE username = 'admin'), NOW()),
                                                                        ((SELECT id FROM users WHERE username = 'viewer_liu'), (SELECT id FROM roles WHERE code = 'ROLE_VIEWER'), (SELECT id FROM users WHERE username = 'admin'), NOW());

-- Insert sample projects
INSERT INTO projects (name, description, project_key, type, visibility, owner_id, status, start_date, end_date, created_at, updated_at) VALUES
                                                                                                             ('电商平台重构', '对现有电商平台进行微服务架构重构，提升系统性能和可扩展性', 'ECOM', 'SCRUM', 'PRIVATE', (SELECT id FROM users WHERE username = 'pm_zhang'), 'ACTIVE', '2024-01-01', '2024-06-30', NOW(), NOW()),
                                                                                                             ('移动端App开发', '开发iOS和Android移动端应用，提供更好的用户体验', 'MOBILE', 'SCRUM', 'PRIVATE', (SELECT id FROM users WHERE username = 'pm_zhang'), 'ACTIVE', '2024-02-01', '2024-08-31', NOW(), NOW()),
                                                                                                             ('数据分析平台', '构建企业级数据分析平台，支持实时数据处理和可视化', 'DATA', 'KANBAN', 'PRIVATE', (SELECT id FROM users WHERE username = 'admin'), 'PLANNING', '2024-03-01', '2024-12-31', NOW(), NOW());

-- Insert sample sprints
INSERT INTO sprints (project_id, name, goal, start_date, end_date, status, created_at, updated_at) VALUES
                                                                                                       ((SELECT id FROM projects WHERE name = '电商平台重构'), 'Sprint 1 - 用户服务', '完成用户服务的微服务拆分和基础功能开发', '2024-01-01', '2024-01-14', 'COMPLETED', NOW(), NOW()),
                                                                                                       ((SELECT id FROM projects WHERE name = '电商平台重构'), 'Sprint 2 - 订单服务', '完成订单服务的开发和与用户服务的集成', '2024-01-15', '2024-01-28', 'ACTIVE', NOW(), NOW()),
                                                                                                       ((SELECT id FROM projects WHERE name = '移动端App开发'), 'Sprint 1 - 基础框架', '搭建移动端基础框架和通用组件', '2024-02-01', '2024-02-14', 'ACTIVE', NOW(), NOW());

-- Insert sample tasks for Sprint 1 (电商平台重构)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, assignee_id, reporter_id, status, priority, story_points, original_estimate, time_spent, created_at, updated_at) VALUES
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'TASK-1', '用户注册功能', '实现用户注册接口，包括邮箱验证和密码加密', 'TASK', (SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'DONE', 'HIGH', 5.0, 8.0, 7.5, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'TASK-2', '用户登录功能', '实现JWT token认证的登录功能', 'TASK', (SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'DONE', 'HIGH', 3.0, 5.0, 5.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'TASK-3', '用户信息管理', '实现用户信息的增删改查接口', 'TASK', (SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'DONE', 'MEDIUM', 5.0, 8.0, 9.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'TASK-4', '单元测试编写', '为用户服务编写单元测试，覆盖率达到80%', 'TASK', (SELECT id FROM users WHERE username = 'tester_zhao'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'DONE', 'MEDIUM', 3.0, 6.0, 6.0, NOW(), NOW());

-- Insert sample tasks for Sprint 2 (电商平台重构)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, assignee_id, reporter_id, status, priority, story_points, original_estimate, time_spent, created_at, updated_at) VALUES
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'TASK-5', '订单创建功能', '实现订单创建接口，包括库存检查和价格计算', 'TASK', (SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'IN_PROGRESS', 'HIGH', 8.0, 12.0, 6.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'TASK-6', '订单查询功能', '实现订单列表和详情查询接口', 'TASK', (SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'IN_PROGRESS', 'MEDIUM', 5.0, 8.0, 3.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'TASK-7', '订单状态管理', '实现订单状态流转和更新功能', 'TASK', (SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'TODO', 'HIGH', 5.0, 8.0, 0.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '电商平台重构'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'TASK-8', '集成测试', '编写订单服务与用户服务的集成测试', 'TASK', (SELECT id FROM users WHERE username = 'tester_zhao'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'TODO', 'MEDIUM', 5.0, 10.0, 0.0, NOW(), NOW());

-- Insert sample tasks for Sprint 1 (移动端App开发)
INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, assignee_id, reporter_id, status, priority, story_points, original_estimate, time_spent, created_at, updated_at) VALUES
((SELECT id FROM projects WHERE name = '移动端App开发'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'TASK-9', 'React Native环境搭建', '配置React Native开发环境和基础项目结构', 'TASK', (SELECT id FROM users WHERE username = 'dev_li'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'DONE', 'HIGH', 3.0, 4.0, 4.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '移动端App开发'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'TASK-10', '导航组件开发', '实现应用的路由导航和页面切换', 'TASK', (SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'IN_PROGRESS', 'HIGH', 5.0, 8.0, 4.0, NOW(), NOW()),
((SELECT id FROM projects WHERE name = '移动端App开发'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'TASK-11', '通用UI组件库', '开发按钮、输入框等通用UI组件', 'TASK', (SELECT id FROM users WHERE username = 'dev_wang'), (SELECT id FROM users WHERE username = 'pm_zhang'), 'TODO', 'MEDIUM', 8.0, 12.0, 0.0, NOW(), NOW());

-- Insert sample work logs
INSERT INTO work_logs (task_id, user_id, work_date, time_spent, remaining_estimate, comment, created_at) VALUES
-- Sprint 1 work logs
((SELECT id FROM tasks WHERE task_key = 'TASK-1'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-02', 4.0, 4.0, '完成用户注册接口设计和数据库表结构', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-1'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-03', 3.5, 0.5, '实现注册逻辑和邮箱验证功能', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-2'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-04', 3.0, 2.0, '实现JWT token生成和验证', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-2'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-05', 2.0, 0.0, '完成登录接口和异常处理', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-3'), (SELECT id FROM users WHERE username = 'dev_wang'), '2024-01-06', 5.0, 3.0, '实现用户信息CRUD接口', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-3'), (SELECT id FROM users WHERE username = 'dev_wang'), '2024-01-08', 4.0, 0.0, '添加权限验证和数据校验', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-4'), (SELECT id FROM users WHERE username = 'tester_zhao'), '2024-01-10', 6.0, 0.0, '编写用户服务单元测试用例', NOW()),
-- Sprint 2 work logs
((SELECT id FROM tasks WHERE task_key = 'TASK-5'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-16', 3.0, 9.0, '设计订单数据模型和接口', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-5'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-01-17', 3.0, 6.0, '实现订单创建逻辑', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-6'), (SELECT id FROM users WHERE username = 'dev_wang'), '2024-01-18', 3.0, 5.0, '实现订单列表查询接口', NOW()),
-- Mobile app work logs
((SELECT id FROM tasks WHERE task_key = 'TASK-9'), (SELECT id FROM users WHERE username = 'dev_li'), '2024-02-01', 4.0, 0.0, '配置开发环境和初始化项目', NOW()),
((SELECT id FROM tasks WHERE task_key = 'TASK-10'), (SELECT id FROM users WHERE username = 'dev_wang'), '2024-02-05', 4.0, 4.0, '实现基础导航结构', NOW());

-- Insert sample burndown points for Sprint 1 (completed sprint)
INSERT INTO burndown_points (sprint_id, point_date, actual_remaining, ideal_remaining, calculated_at) VALUES
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-01', 16, 16, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-02', 16, 14, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-03', 11, 13, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-04', 8, 11, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-05', 8, 10, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-08', 5, 8, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-09', 5, 6, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-10', 3, 5, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-11', 3, 3, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-12', 0, 2, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-14', 0, 0, NOW());

-- Insert sample burndown points for Sprint 2 (in progress)
INSERT INTO burndown_points (sprint_id, point_date, actual_remaining, ideal_remaining, calculated_at) VALUES
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-15', 23, 23, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-16', 23, 21, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-17', 20, 19, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-18', 18, 17, NOW()),
                                                                                              ((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-19', 18, 15, NOW());

-- =============================================
-- 5. Completion Summary
-- =============================================

SELECT 'Database initialization completed successfully!' AS status,
       (SELECT COUNT(*) FROM users) AS users_count,
       (SELECT COUNT(*) FROM roles) AS roles_count,
       (SELECT COUNT(*) FROM permissions) AS permissions_count,
       (SELECT COUNT(*) FROM role_permissions) AS role_permissions_count,
       (SELECT COUNT(*) FROM user_roles) AS user_roles_count,
       (SELECT COUNT(*) FROM projects) AS projects_count,
       (SELECT COUNT(*) FROM sprints) AS sprints_count,
       (SELECT COUNT(*) FROM tasks) AS tasks_count,
       (SELECT COUNT(*) FROM work_logs) AS work_logs_count,
       (SELECT COUNT(*) FROM burndown_points) AS burndown_points_count;
