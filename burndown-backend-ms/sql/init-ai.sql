-- AI Agent Service Schema
CREATE SCHEMA IF NOT EXISTS ms_ai;

SET search_path TO ms_ai;

CREATE TABLE IF NOT EXISTS agent_chat_sessions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    project_id  BIGINT,
    session_key VARCHAR(64)  NOT NULL UNIQUE,
    status      VARCHAR(20)  DEFAULT 'ACTIVE',
    title       TEXT,
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT    NOT NULL REFERENCES agent_chat_sessions(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT        NOT NULL,
    token_count INTEGER,
    created_at  TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_tool_call_logs (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT      NOT NULL REFERENCES agent_chat_sessions(id) ON DELETE CASCADE,
    tool_name   VARCHAR(100),
    input       TEXT,
    output      TEXT,
    duration_ms BIGINT,
    status      VARCHAR(20),
    created_at  TIMESTAMP   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sessions_user     ON agent_chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_key      ON agent_chat_sessions(session_key);
CREATE INDEX IF NOT EXISTS idx_messages_session  ON agent_chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_logs_session ON agent_tool_call_logs(session_id);
