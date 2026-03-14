-- V002: Add v1.1 EventEnvelope context columns to zibo_event_outbox.
-- Nullable for backward compatibility with existing outbox rows.
-- tenant_id already exists as UUID — add remaining columns.

ALTER TABLE zibo_event_outbox
    ADD COLUMN IF NOT EXISTS pod_id           TEXT,
    ADD COLUMN IF NOT EXISTS correlation_id   TEXT,
    ADD COLUMN IF NOT EXISTS causation_id     TEXT,
    ADD COLUMN IF NOT EXISTS idempotency_key  TEXT,
    ADD COLUMN IF NOT EXISTS schema_version   INT             DEFAULT 1,
    ADD COLUMN IF NOT EXISTS producer         VARCHAR(64)     DEFAULT 'zibo',
    ADD COLUMN IF NOT EXISTS subject_type     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS subject_id       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS partition_key    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS occurred_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error    TEXT;
