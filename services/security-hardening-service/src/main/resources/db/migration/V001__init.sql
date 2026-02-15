-- ============================================================
-- Security Hardening Service  (v1.1-native stub)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS secharden;

-- ----------------------------------------------------------
-- 1. policy_packs — security policy pack definitions
-- ----------------------------------------------------------
CREATE TABLE secharden.policy_packs (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    pack_type       VARCHAR(50)     NOT NULL DEFAULT 'BASELINE',
    rules           JSONB           NOT NULL DEFAULT '[]',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_packs_tenant ON secharden.policy_packs (tenant_id);

-- ----------------------------------------------------------
-- 2. scan_results — compliance scan results
-- ----------------------------------------------------------
CREATE TABLE secharden.scan_results (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pack_id         BIGINT          NOT NULL REFERENCES secharden.policy_packs(id),
    target          VARCHAR(255)    NOT NULL,
    passed          INT             NOT NULL DEFAULT 0,
    failed          INT             NOT NULL DEFAULT 0,
    skipped         INT             NOT NULL DEFAULT 0,
    details         JSONB           NOT NULL DEFAULT '[]',
    scanned_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_scan_results_tenant ON secharden.scan_results (tenant_id);
CREATE INDEX idx_scan_results_pack ON secharden.scan_results (pack_id);

-- ----------------------------------------------------------
-- 3. event_outbox
-- ----------------------------------------------------------
CREATE TABLE secharden.event_outbox (
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

CREATE INDEX idx_secharden_outbox_unpublished
    ON secharden.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 4. idempotency_keys
-- ----------------------------------------------------------
CREATE TABLE secharden.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
