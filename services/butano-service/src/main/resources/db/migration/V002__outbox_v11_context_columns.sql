-- =============================================================================
-- BUTANO — Add v1.1 event envelope context columns to outbox table.
-- Enables CompanionOutboxPublisher dual-emit (legacy + v1.1 EventEnvelope).
-- =============================================================================

ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS pod_id           VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS correlation_id   VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS causation_id     VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS idempotency_key  VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS schema_version   INT          DEFAULT 1;
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS producer         VARCHAR(64);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS subject_type     VARCHAR(64);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS subject_id       VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS partition_key    VARCHAR(128);
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS occurred_at      TIMESTAMPTZ;
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS publish_error    TEXT;
ALTER TABLE butano_event_outbox ADD COLUMN IF NOT EXISTS retry_count      INT          DEFAULT 0;
