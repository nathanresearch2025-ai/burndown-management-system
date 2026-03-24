-- Burndown Service Schema
CREATE SCHEMA IF NOT EXISTS pg_burndown;

SET search_path TO pg_burndown;

CREATE TABLE IF NOT EXISTS burndown_points (
    id                BIGSERIAL PRIMARY KEY,
    sprint_id         BIGINT        NOT NULL,
    record_date       DATE          NOT NULL,
    remaining_points  DECIMAL(10,2),
    completed_points  DECIMAL(10,2),
    total_points      DECIMAL(10,2),
    ideal_remaining   DECIMAL(10,2),
    created_at        TIMESTAMP     DEFAULT NOW(),
    UNIQUE (sprint_id, record_date)
);

CREATE TABLE IF NOT EXISTS sprint_predictions (
    id                        BIGSERIAL PRIMARY KEY,
    sprint_id                 BIGINT         NOT NULL UNIQUE,
    predicted_completion_rate DECIMAL(5,2),
    risk_level                VARCHAR(20),
    model_version             VARCHAR(50),
    ml_model                  VARCHAR(100),
    notes                     TEXT,
    created_at                TIMESTAMP      DEFAULT NOW(),
    updated_at                TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_burndown_sprint      ON burndown_points(sprint_id);
CREATE INDEX IF NOT EXISTS idx_burndown_date        ON burndown_points(record_date);
CREATE INDEX IF NOT EXISTS idx_prediction_sprint    ON sprint_predictions(sprint_id);
