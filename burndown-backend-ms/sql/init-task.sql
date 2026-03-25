-- Task Service Schema
CREATE SCHEMA IF NOT EXISTS ms_task;

SET search_path TO ms_task;

CREATE TABLE IF NOT EXISTS tasks (
    id                 BIGSERIAL PRIMARY KEY,
    task_key           VARCHAR(30)  UNIQUE,
    project_id         BIGINT       NOT NULL,
    sprint_id          BIGINT,
    title              VARCHAR(500) NOT NULL,
    description        TEXT,
    status             VARCHAR(20)  DEFAULT 'TODO',
    priority           VARCHAR(20)  DEFAULT 'MEDIUM',
    type               VARCHAR(30)  DEFAULT 'TASK',
    assignee_id        BIGINT,
    reporter_id        BIGINT,
    story_points       DECIMAL(5,1),
    original_estimate  DECIMAL(10,2),
    remaining_estimate DECIMAL(10,2),
    time_spent         DECIMAL(10,2) DEFAULT 0,
    due_date           DATE,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP    DEFAULT NOW(),
    updated_at         TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS work_logs (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT       NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL,
    time_spent   DECIMAL(10,2) NOT NULL,
    description  TEXT,
    log_date     DATE          DEFAULT CURRENT_DATE,
    created_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tasks_project  ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_sprint   ON tasks(sprint_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status   ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX IF NOT EXISTS idx_worklogs_task  ON work_logs(task_id);

-- Seed tasks
INSERT INTO tasks (task_key, project_id, sprint_id, title, status, priority, type, story_points) VALUES
    ('BRN-1', 1, 1, '搭建微服务基础架构', 'DONE',     'HIGH',   'TASK', 8),
    ('BRN-2', 1, 1, '实现API Gateway JWT验证', 'DONE', 'HIGH',   'TASK', 5),
    ('BRN-3', 1, 1, '完成auth-service', 'IN_PROGRESS','HIGH',   'TASK', 8),
    ('BRN-4', 1, 1, '完成project-service', 'TODO',    'MEDIUM', 'TASK', 5),
    ('BRN-5', 1, 1, '完成task-service', 'TODO',       'MEDIUM', 'TASK', 5),
    ('BRN-6', 1, 1, '完成burndown-service', 'TODO',   'MEDIUM', 'TASK', 5),
    ('BRN-7', 1, 1, 'AI任务描述生成', 'TODO',          'LOW',    'FEATURE', 3)
ON CONFLICT (task_key) DO NOTHING;
