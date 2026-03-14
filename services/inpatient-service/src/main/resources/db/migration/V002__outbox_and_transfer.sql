-- =============================================
-- Service : inpatient-service
-- Schema  : inpatient
-- Flyway  : V002__outbox_and_transfer.sql
-- Purpose : Transfer table, event outbox (v1.1), idempotency keys
-- =============================================

-- ═══════════════════════════════════════════════════════════════════════
-- 1. Transfer table — records ward/bed transfers for an admission
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE inpatient.transfer (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    admission_ref   UUID            NOT NULL REFERENCES inpatient.admission(admission_ref),
    from_ward_id    UUID,
    to_ward_id      UUID            NOT NULL,
    to_bed_id       UUID,
    transferred_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_transfer_admission_ref ON inpatient.transfer (admission_ref);
CREATE INDEX idx_transfer_tenant ON inpatient.transfer (tenant_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 2. Event Outbox (v1.1 columns) — transactional outbox for Kafka
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE inpatient.event_outbox (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    correlation_id  VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(128),
    event_type      VARCHAR(256)    NOT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    payload_json    TEXT            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_inpatient_outbox_unpublished ON inpatient.event_outbox (created_at)
    WHERE published_at IS NULL;

-- ═══════════════════════════════════════════════════════════════════════
-- 3. Idempotency Keys — Tech Companion auto-config uses this table
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE inpatient.idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_inpatient_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
