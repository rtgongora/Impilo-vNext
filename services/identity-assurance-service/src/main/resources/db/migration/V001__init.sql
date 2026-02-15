-- ============================================================
-- Identity Assurance Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ia;

-- ----------------------------------------------------------
-- 1. attestations — device/identity attestation registry
-- ----------------------------------------------------------
CREATE TABLE ia.attestations (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    attestation_type VARCHAR(50)    NOT NULL,
    device_fingerprint VARCHAR(500),
    evidence        JSONB           NOT NULL DEFAULT '{}',
    outcome         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    confidence      NUMERIC(5,4)    NOT NULL DEFAULT 0.0,
    expires_at      TIMESTAMPTZ,
    recorded_by     VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_attestations_tenant ON ia.attestations (tenant_id);
CREATE INDEX idx_attestations_actor ON ia.attestations (tenant_id, actor_id);
CREATE INDEX idx_attestations_type ON ia.attestations (tenant_id, attestation_type);

-- ----------------------------------------------------------
-- 2. risk_assessments — risk scoring results
-- ----------------------------------------------------------
CREATE TABLE ia.risk_assessments (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    context_type    VARCHAR(100)    NOT NULL,
    context_id      VARCHAR(255),
    risk_score      NUMERIC(5,4)    NOT NULL DEFAULT 0.0,
    risk_level      VARCHAR(20)     NOT NULL DEFAULT 'LOW',
    factors         JSONB           NOT NULL DEFAULT '[]',
    recommendation  VARCHAR(50)     NOT NULL DEFAULT 'ALLOW',
    assessed_by     VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_assessments_tenant ON ia.risk_assessments (tenant_id);
CREATE INDEX idx_risk_assessments_actor ON ia.risk_assessments (tenant_id, actor_id);

-- ----------------------------------------------------------
-- 3. event_outbox
-- ----------------------------------------------------------
CREATE TABLE ia.event_outbox (
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

CREATE INDEX idx_ia_outbox_unpublished
    ON ia.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 4. idempotency_keys
-- ----------------------------------------------------------
CREATE TABLE ia.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ia_idempotency_key ON ia.idempotency_keys (idempotency_key);
