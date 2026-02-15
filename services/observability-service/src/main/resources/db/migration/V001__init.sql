-- ============================================================
-- Observability Service  (v1.1-native stub)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS obs;

-- ----------------------------------------------------------
-- 1. dashboards — dashboard definition registry
-- ----------------------------------------------------------
CREATE TABLE obs.dashboards (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    dashboard_type  VARCHAR(50)     NOT NULL DEFAULT 'GRAFANA',
    config          JSONB           NOT NULL DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dashboards_tenant ON obs.dashboards (tenant_id);

-- ----------------------------------------------------------
-- 2. alert_rules — alert rule registry
-- ----------------------------------------------------------
CREATE TABLE obs.alert_rules (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    metric_name     VARCHAR(255)    NOT NULL,
    condition       VARCHAR(50)     NOT NULL DEFAULT 'GREATER_THAN',
    threshold       NUMERIC(14,2)   NOT NULL DEFAULT 0,
    severity        VARCHAR(20)     NOT NULL DEFAULT 'WARNING',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_rules_tenant ON obs.alert_rules (tenant_id);

-- ----------------------------------------------------------
-- 3. event_outbox
-- ----------------------------------------------------------
CREATE TABLE obs.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       VARCHAR(255),
    pod_id          VARCHAR(255),
    correlation_id  VARCHAR(255),
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_obs_outbox_unpublished
    ON obs.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 4. idempotency_keys
-- ----------------------------------------------------------
CREATE TABLE obs.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
