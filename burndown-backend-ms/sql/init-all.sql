-- ============================================================
-- Burndown Microservices - 一键初始化所有 Schema
-- 执行顺序：ms_auth -> ms_project -> ms_task -> ms_burndown -> ms_ai
-- 用法: psql -U postgres -d burndown_db -f init-all.sql
-- ============================================================

\i init-auth.sql
\i init-project.sql
\i init-task.sql
\i init-burndown.sql
\i init-ai.sql
