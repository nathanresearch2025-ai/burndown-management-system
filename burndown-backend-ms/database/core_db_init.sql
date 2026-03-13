-- =============================================
-- Core Database Initialization Script
-- =============================================

-- Drop existing tables if they exist
DROP TABLE IF EXISTS burndown_points CASCADE;
DROP TABLE IF EXISTS work_logs CASCADE;
DROP TABLE IF EXISTS tasks CASCADE;
DROP TABLE IF EXISTS sprints CASCADE;
DROP TABLE IF EXISTS projects CASCADE;

-- =============================================
-- 1. Projects Table
-- =============================================
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_project_key ON projects(project_key);

-- =============================================
-- 2. Sprints Table
-- =============================================
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sprints_project_id ON sprints(project_id);
CREATE INDEX idx_sprints_status ON sprints(status);

-- =============================================
-- 3. Tasks Table
-- =============================================
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
    due_date DATE
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id);
CREATE INDEX idx_tasks_sprint_id ON tasks(sprint_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_task_key ON tasks(task_key);

-- =============================================
-- 4. Work Logs Table
-- =============================================
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
    CONSTRAINT uk_work_logs_task_user_date UNIQUE (task_id, user_id, work_date)
);

CREATE INDEX idx_work_logs_task_id ON work_logs(task_id);
CREATE INDEX idx_work_logs_user_id ON work_logs(user_id);
CREATE INDEX idx_work_logs_work_date ON work_logs(work_date);

-- =============================================
-- 5. Burndown Points Table
-- =============================================
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
    CONSTRAINT uk_burndown_sprint_date UNIQUE (sprint_id, point_date)
);

CREATE INDEX idx_burndown_points_sprint_id ON burndown_points(sprint_id);
CREATE INDEX idx_burndown_points_date ON burndown_points(point_date);

-- =============================================
-- 6. Insert Sample Data
-- =============================================
-- Note: owner_id, assignee_id, reporter_id reference users in auth_db
-- These are logical foreign keys (no physical constraint)

INSERT INTO projects (name, description, project_key, type, visibility, owner_id, status, start_date, end_date, created_at, updated_at) VALUES
('电商平台重构', '对现有电商平台进行微服务架构重构，提升系统性能和可扩展性', 'ECOM', 'SCRUM', 'PRIVATE', 2, 'ACTIVE', '2024-01-01', '2024-06-30', NOW(), NOW()),
('移动端App开发', '开发iOS和Android移动端应用，提供更好的用户体验', 'MOBILE', 'SCRUM', 'PRIVATE', 2, 'ACTIVE', '2024-02-01', '2024-08-31', NOW(), NOW()),
('数据分析平台', '构建企业级数据分析平台，支持实时数据处理和可视化', 'DATA', 'KANBAN', 'PRIVATE', 1, 'PLANNING', '2024-03-01', '2024-12-31', NOW(), NOW());

INSERT INTO sprints (project_id, name, goal, start_date, end_date, status, created_at, updated_at) VALUES
((SELECT id FROM projects WHERE project_key = 'ECOM'), 'Sprint 1 - 用户服务', '完成用户服务的微服务拆分和基础功能开发', '2024-01-01', '2024-01-14', 'COMPLETED', NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), 'Sprint 2 - 订单服务', '完成订单服务的开发和与用户服务的集成', '2024-01-15', '2024-01-28', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'MOBILE'), 'Sprint 1 - 基础框架', '搭建移动端基础框架和通用组件', '2024-02-01', '2024-02-14', 'ACTIVE', NOW(), NOW());

INSERT INTO tasks (project_id, sprint_id, task_key, title, description, type, assignee_id, reporter_id, status, priority, story_points, original_estimate, time_spent, created_at, updated_at) VALUES
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'ECOM-1', '用户注册功能', '实现用户注册接口，包括邮箱验证和密码加密', 'TASK', 3, 2, 'DONE', 'HIGH', 5.0, 8.0, 7.5, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'ECOM-2', '用户登录功能', '实现JWT token认证的登录功能', 'TASK', 3, 2, 'DONE', 'HIGH', 3.0, 5.0, 5.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'ECOM-3', '用户信息管理', '实现用户信息的增删改查接口', 'TASK', 4, 2, 'DONE', 'MEDIUM', 5.0, 8.0, 9.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), 'ECOM-4', '单元测试编写', '为用户服务编写单元测试，覆盖率达到80%', 'TASK', 5, 2, 'DONE', 'MEDIUM', 3.0, 6.0, 6.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'ECOM-5', '订单创建功能', '实现订单创建接口，包括库存检查和价格计算', 'TASK', 3, 2, 'IN_PROGRESS', 'HIGH', 8.0, 12.0, 6.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'ECOM-6', '订单查询功能', '实现订单列表和详情查询接口', 'TASK', 4, 2, 'IN_PROGRESS', 'MEDIUM', 5.0, 8.0, 3.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'ECOM-7', '订单状态管理', '实现订单状态流转和更新功能', 'TASK', 4, 2, 'TODO', 'HIGH', 5.0, 8.0, 0.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'ECOM'), (SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), 'ECOM-8', '集成测试', '编写订单服务与用户服务的集成测试', 'TASK', 5, 2, 'TODO', 'MEDIUM', 5.0, 10.0, 0.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'MOBILE'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'MOBILE-1', 'React Native环境搭建', '配置React Native开发环境和基础项目结构', 'TASK', 3, 2, 'DONE', 'HIGH', 3.0, 4.0, 4.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'MOBILE'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'MOBILE-2', '导航组件开发', '实现应用的路由导航和页面切换', 'TASK', 4, 2, 'IN_PROGRESS', 'HIGH', 5.0, 8.0, 4.0, NOW(), NOW()),
((SELECT id FROM projects WHERE project_key = 'MOBILE'), (SELECT id FROM sprints WHERE name = 'Sprint 1 - 基础框架'), 'MOBILE-3', '通用UI组件库', '开发按钮、输入框等通用UI组件', 'TASK', 4, 2, 'TODO', 'MEDIUM', 8.0, 12.0, 0.0, NOW(), NOW());

INSERT INTO work_logs (task_id, user_id, work_date, time_spent, remaining_estimate, comment, created_at) VALUES
((SELECT id FROM tasks WHERE task_key = 'ECOM-1'), 3, '2024-01-02', 4.0, 4.0, '完成用户注册接口设计和数据库表结构', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-1'), 3, '2024-01-03', 3.5, 0.5, '实现注册逻辑和邮箱验证功能', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-2'), 3, '2024-01-04', 3.0, 2.0, '实现JWT token生成和验证', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-2'), 3, '2024-01-05', 2.0, 0.0, '完成登录接口和异常处理', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-3'), 4, '2024-01-06', 5.0, 3.0, '实现用户信息CRUD接口', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-3'), 4, '2024-01-08', 4.0, 0.0, '添加权限验证和数据校验', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-4'), 5, '2024-01-10', 6.0, 0.0, '编写用户服务单元测试用例', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-5'), 3, '2024-01-16', 3.0, 9.0, '设计订单数据模型和接口', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-5'), 3, '2024-01-17', 3.0, 6.0, '实现订单创建逻辑', NOW()),
((SELECT id FROM tasks WHERE task_key = 'ECOM-6'), 4, '2024-01-18', 3.0, 5.0, '实现订单列表查询接口', NOW()),
((SELECT id FROM tasks WHERE task_key = 'MOBILE-1'), 3, '2024-02-01', 4.0, 0.0, '配置开发环境和初始化项目', NOW()),
((SELECT id FROM tasks WHERE task_key = 'MOBILE-2'), 4, '2024-02-05', 4.0, 4.0, '实现基础导航结构', NOW());

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
((SELECT id FROM sprints WHERE name = 'Sprint 1 - 用户服务'), '2024-01-14', 0, 0, NOW()),
((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-15', 23, 23, NOW()),
((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-16', 23, 21, NOW()),
((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-17', 20, 19, NOW()),
((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-18', 18, 17, NOW()),
((SELECT id FROM sprints WHERE name = 'Sprint 2 - 订单服务'), '2024-01-19', 18, 15, NOW());

-- =============================================
-- 7. Summary
-- =============================================
SELECT 'Core database initialization completed!' AS status,
       (SELECT COUNT(*) FROM projects) AS projects_count,
       (SELECT COUNT(*) FROM sprints) AS sprints_count,
       (SELECT COUNT(*) FROM tasks) AS tasks_count,
       (SELECT COUNT(*) FROM work_logs) AS work_logs_count,
       (SELECT COUNT(*) FROM burndown_points) AS burndown_points_count;
