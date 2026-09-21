-- Project Service Schema
CREATE SCHEMA IF NOT EXISTS ms_project;

SET search_path TO ms_project;

CREATE TABLE IF NOT EXISTS projects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    project_key VARCHAR(20)  NOT NULL UNIQUE,
    description TEXT,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(20)  DEFAULT 'ACTIVE',
    type        VARCHAR(20)  DEFAULT 'SCRUM',
    visibility  VARCHAR(20)  DEFAULT 'PRIVATE',
    start_date  DATE,
    end_date    DATE,
    settings    TEXT         DEFAULT '{}',
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sprints (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    goal             TEXT,
    status           VARCHAR(20)  DEFAULT 'PLANNED',
    start_date       DATE,
    end_date         DATE,
    total_capacity   DECIMAL(10,2),
    committed_points DECIMAL(10,2),
    completed_points DECIMAL(10,2) DEFAULT 0,
    velocity         DECIMAL(10,2),
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_projects_owner      ON projects(owner_id);
CREATE INDEX IF NOT EXISTS idx_projects_key        ON projects(project_key);
CREATE INDEX IF NOT EXISTS idx_sprints_project     ON sprints(project_id);
CREATE INDEX IF NOT EXISTS idx_sprints_status      ON sprints(status);

CREATE TABLE IF NOT EXISTS saga_instances (
    id               VARCHAR(36)   PRIMARY KEY,
    saga_type        VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'STARTED',
    current_step     VARCHAR(100),
    sprint_id        BIGINT        NOT NULL,
    project_id       BIGINT        NOT NULL,
    next_sprint_id   BIGINT,
    context_json     TEXT,
    failure_reason   TEXT,
    created_at       TIMESTAMP     DEFAULT NOW(),
    updated_at       TIMESTAMP     DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_saga_sprint_active
    ON saga_instances (sprint_id)
    WHERE status IN ('STARTED', 'IN_PROGRESS');

CREATE TABLE IF NOT EXISTS saga_step_logs (
    id           BIGSERIAL     PRIMARY KEY,
    saga_id      VARCHAR(36)   NOT NULL REFERENCES saga_instances(id),
    step_name    VARCHAR(100)  NOT NULL,
    step_status  VARCHAR(20)   NOT NULL,
    error_msg    TEXT,
    executed_at  TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saga_step_logs_saga_id ON saga_step_logs(saga_id);

-- Seed data
INSERT INTO projects (name, project_key, description, owner_id, status) VALUES
    ('Burndown示例项目', 'BRN', '燃尽图管理系统示例项目', 1, 'ACTIVE')
ON CONFLICT (project_key) DO NOTHING;

INSERT INTO sprints (project_id, name, goal, status, start_date, end_date, capacity) VALUES
    (1, 'Sprint 1', '完成核心微服务改造', 'ACTIVE',
     CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE + INTERVAL '7 days', 40)
ON CONFLICT DO NOTHING;
