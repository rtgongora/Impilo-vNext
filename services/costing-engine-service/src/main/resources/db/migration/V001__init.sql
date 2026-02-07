-- =============================================
-- Service : costing-engine-service
-- Schema  : costing
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS costing;

CREATE TABLE costing.cost_model (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    model_code      VARCHAR(100)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    methodology     VARCHAR(50)     NOT NULL,
    rules           JSONB,
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (tenant_id, model_code)
);
