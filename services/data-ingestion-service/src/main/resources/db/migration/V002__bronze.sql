-- ============================================================================
-- data-ingestion-service V002 — Bronze event store
-- Append-only table for raw ingested events. Governance applied at query time.
-- ============================================================================

CREATE TABLE din_bronze_event (
    receipt_id          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL,
    request_id          VARCHAR(255)    NOT NULL,
    correlation_id      VARCHAR(255)    NOT NULL,
    idempotency_key     VARCHAR(255)    NOT NULL,
    event_id            VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      INT             NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    emitted_at          TIMESTAMPTZ,
    subject_type        VARCHAR(64)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    partition_key       VARCHAR(255)    NOT NULL,
    envelope_json       TEXT            NOT NULL,
    stored_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_din_bronze_event_dedup
        UNIQUE (tenant_id, pod_id, event_id),

    CONSTRAINT uq_din_bronze_idempotency
        UNIQUE (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_din_bronze_event_type ON din_bronze_event (event_type);
CREATE INDEX idx_din_bronze_tenant ON din_bronze_event (tenant_id);
CREATE INDEX idx_din_bronze_stored_at ON din_bronze_event (stored_at);
CREATE INDEX idx_din_bronze_subject ON din_bronze_event (subject_type, subject_id);
