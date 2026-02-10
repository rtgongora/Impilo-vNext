-- V017: Add v1.1 context columns to event_outbox for EventEnvelope construction.
-- These are nullable to maintain backward compatibility with existing outbox rows.

ALTER TABLE vito.event_outbox
    ADD COLUMN IF NOT EXISTS tenant_id       TEXT            NULL,
    ADD COLUMN IF NOT EXISTS pod_id          TEXT            NULL,
    ADD COLUMN IF NOT EXISTS correlation_id  TEXT            NULL,
    ADD COLUMN IF NOT EXISTS idempotency_key TEXT            NULL;
