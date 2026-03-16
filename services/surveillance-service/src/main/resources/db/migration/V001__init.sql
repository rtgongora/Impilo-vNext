-- ============================================================
-- Surveillance Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS surv;

-- ----------------------------------------------------------
-- 1. signals — threshold-based trigger definitions
-- ----------------------------------------------------------
CREATE TABLE surv.signals (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    event_type      VARCHAR(100)    NOT NULL,
    condition_field VARCHAR(255)    NOT NULL,
    threshold       INT             NOT NULL DEFAULT 1,
    window_hours    INT             NOT NULL DEFAULT 24,
    severity        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_signals_tenant ON surv.signals (tenant_id);
CREATE INDEX idx_signals_event_type ON surv.signals (tenant_id, event_type);

-- ----------------------------------------------------------
-- 2. signal_hits — recorded signal trigger occurrences
-- ----------------------------------------------------------
CREATE TABLE surv.signal_hits (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    signal_id       BIGINT          NOT NULL REFERENCES surv.signals(id),
    event_type      VARCHAR(100)    NOT NULL,
    event_payload   JSONB           NOT NULL DEFAULT '{}',
    hit_count       INT             NOT NULL DEFAULT 1,
    facility_id     UUID,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_signal_hits_tenant ON surv.signal_hits (tenant_id);
CREATE INDEX idx_signal_hits_signal ON surv.signal_hits (signal_id);
CREATE INDEX idx_signal_hits_recorded ON surv.signal_hits (tenant_id, recorded_at);

-- ----------------------------------------------------------
-- 3. cases — public health case registry
-- ----------------------------------------------------------
CREATE TABLE surv.cases (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    signal_hit_id   BIGINT          REFERENCES surv.signal_hits(id),
    case_type       VARCHAR(100)    NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    description     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    severity        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    facility_id     UUID,
    assigned_to     VARCHAR(255),
    opened_by       VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_cases_tenant ON surv.cases (tenant_id);
CREATE INDEX idx_cases_status ON surv.cases (tenant_id, status);
CREATE INDEX idx_cases_type ON surv.cases (tenant_id, case_type);

-- ----------------------------------------------------------
-- 4. event_outbox — transactional outbox for Kafka
-- ----------------------------------------------------------
CREATE TABLE surv.event_outbox (
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

CREATE INDEX idx_surv_outbox_unpublished
    ON surv.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 5. idempotency_keys — request dedup
-- ----------------------------------------------------------
CREATE TABLE surv.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_surv_idempotency_key ON surv.idempotency_keys (idempotency_key);
