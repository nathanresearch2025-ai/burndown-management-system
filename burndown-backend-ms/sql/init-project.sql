-- Project Service Schema
CREATE SCHEMA IF NOT EXISTS pg_project;

SET search_path TO pg_project;

CREATE TABLE IF NOT EXISTS projects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    project_key VARCHAR(20)  NOT NULL UNIQUE,
    description TEXT,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sprints (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    goal         TEXT,
    status       VARCHAR(20)  DEFAULT 'PLANNED',
    start_date   DATE,
    end_date     DATE,
    capacity     DECIMAL(10,2),
    velocity     DECIMAL(10,2),
    created_at   TIMESTAMP    DEFAULT NOW(),
    updated_at   TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_projects_owner      ON projects(owner_id);
CREATE INDEX IF NOT EXISTS idx_projects_key        ON projects(project_key);
CREATE INDEX IF NOT EXISTS idx_sprints_project     ON sprints(project_id);
CREATE INDEX IF NOT EXISTS idx_sprints_status      ON sprints(status);

-- Seed data
INSERT INTO projects (name, project_key, description, owner_id, status) VALUES
    ('Burndown示例项目', 'BRN', '燃尽图管理系统示例项目', 1, 'ACTIVE')
ON CONFLICT (project_key) DO NOTHING;

INSERT INTO sprints (project_id, name, goal, status, start_date, end_date, capacity) VALUES
    (1, 'Sprint 1', '完成核心微服务改造', 'ACTIVE',
     CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE + INTERVAL '7 days', 40)
ON CONFLICT DO NOTHING;
