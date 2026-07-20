-- W1a — UBOMI outbox publisher enablement.
--
-- ubomi has written rows to ubomi.event_outbox since V001 but never had a
-- publisher/relay, so nothing was ever emitted. UbomiOutboxPublisher (mirroring
-- ButanoOutboxPublisher on the shared CompanionOutboxPublisher base) now drains
-- unpublished rows. That base needs:
--   * v1.1 EventEnvelope context columns (tenant/pod/correlation/... ) to emit
--     honest v1.1 events alongside the legacy topics (DUAL emit), and
--   * poison-row bookkeeping (retry_count / publish_error) so a single bad row
--     is marked failed after MAX_RETRIES instead of head-of-line-blocking the
--     drain.
-- All columns are nullable / defaulted so existing rows remain valid under
-- ddl-auto=validate.

ALTER TABLE ubomi.event_outbox
    ADD COLUMN IF NOT EXISTS tenant_id       UUID,
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(128),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(128),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER,
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS partition_key   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER;
