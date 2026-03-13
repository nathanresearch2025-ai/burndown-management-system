-- 为 userId=1 插入进行中的任务数据
-- 确保项目和 Sprint 存在

-- 1. 确保项目存在（如果不存在则插入）
INSERT INTO project (id, name, description, owner_id, created_at, updated_at)
VALUES (1, '燃尽图管理系统', 'Scrum 项目管理系统', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. 确保 Sprint 存在（如果不存在则插入）
INSERT INTO sprint (id, project_id, name, start_date, end_date, capacity, status, created_at, updated_at)
VALUES (1, 1, 'Sprint 1', CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE + INTERVAL '7 days', 100, 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. 插入进行中的任务（IN_PROGRESS 状态）
-- 任务 1：实现用户登录功能
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-101',
    '实现用户登录功能',
    '开发用户登录接口，包括 JWT Token 生成和验证',
    'FEATURE',
    'IN_PROGRESS',
    'HIGH',
    5,
    1,
    NOW() - INTERVAL '2 days',
    NOW()
);

-- 任务 2：优化数据库查询性能
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-102',
    '优化数据库查询性能',
    '为任务查询添加索引，优化 N+1 查询问题',
    'IMPROVEMENT',
    'IN_PROGRESS',
    'MEDIUM',
    3,
    1,
    NOW() - INTERVAL '1 day',
    NOW()
);

-- 任务 3：修复前端样式问题
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-103',
    '修复前端样式问题',
    '修复任务看板在移动端的显示问题',
    'BUG',
    'IN_PROGRESS',
    'LOW',
    2,
    1,
    NOW(),
    NOW()
);

-- 任务 4：编写 API 文档
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-104',
    '编写 API 文档',
    '使用 Swagger 编写 RESTful API 文档',
    'DOCUMENTATION',
    'IN_PROGRESS',
    'MEDIUM',
    3,
    1,
    NOW() - INTERVAL '3 hours',
    NOW()
);

-- 5. 插入一些其他状态的任务（用于对比）
-- TODO 状态的任务
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-105',
    '实现任务拖拽功能',
    '在任务看板中实现拖拽排序功能',
    'FEATURE',
    'TODO',
    'HIGH',
    5,
    1,
    NOW(),
    NOW()
);

-- DONE 状态的任务
INSERT INTO task (
    project_id,
    sprint_id,
    task_key,
    title,
    description,
    type,
    status,
    priority,
    story_points,
    assignee_id,
    created_at,
    updated_at
)
VALUES (
    1,
    1,
    'TASK-100',
    '搭建项目基础架构',
    '初始化 Spring Boot 项目，配置数据库连接',
    'FEATURE',
    'DONE',
    'HIGH',
    8,
    1,
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '3 days'
);

-- 查询验证：查看 userId=1 的进行中任务
SELECT
    task_key,
    title,
    type,
    status,
    priority,
    story_points,
    updated_at
FROM task
WHERE assignee_id = 1
  AND status = 'IN_PROGRESS'
  AND project_id = 1
ORDER BY updated_at DESC;
