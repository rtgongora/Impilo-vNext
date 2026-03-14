-- V018: Add remaining v1.1 EventEnvelope columns to vito.event_outbox.
-- V017 already added tenant_id, pod_id, correlation_id, idempotency_key.
-- This adds: causation_id, schema_version, producer, subject_type, subject_id,
-- partition_key, occurred_at, publish_error.

ALTER TABLE vito.event_outbox
    ADD COLUMN IF NOT EXISTS causation_id     TEXT,
    ADD COLUMN IF NOT EXISTS schema_version   INT             DEFAULT 1,
    ADD COLUMN IF NOT EXISTS producer         VARCHAR(64)     DEFAULT 'vito',
    ADD COLUMN IF NOT EXISTS subject_type     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS subject_id       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS partition_key    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS occurred_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error    TEXT;
