-- Phase 3 — attempt-time rail enforcement.
-- See docs/design/phase-3-attempt-time-rail-enforcement-implementation.md.
--
-- Two additive nullable columns on mushex_payment_attempts:
--   * idempotency_key    : per-attempt idempotency, enforced via a partial unique index
--                          on (intent_id, idempotency_key). Nullable so legacy attempts
--                          (created via webhooks before the attempt-creation flow shipped)
--                          remain valid.
--   * enforcement_metadata : JSONB audit trail recording the rail-enforcement decision at
--                          attempt time (effective_rail, preferred_rail, reason, safety_gate, …).
--
-- Both columns are nullable; existing rows continue to load. No data is rewritten.

ALTER TABLE mushex_payment_attempts
    ADD COLUMN idempotency_key      VARCHAR(120),
    ADD COLUMN enforcement_metadata JSONB;

COMMENT ON COLUMN mushex_payment_attempts.idempotency_key IS
    'Caller-supplied idempotency key for the attempt-creation flow (Phase 3). NULL for legacy attempts created via webhooks.';

COMMENT ON COLUMN mushex_payment_attempts.enforcement_metadata IS
    'JSONB audit record of the rail-enforcement decision at attempt time: effective_rail, preferred_rail, reason, legacy_intent, safety_gate, enforcement_version. See docs/design/phase-3-attempt-time-rail-enforcement-implementation.md.';

CREATE UNIQUE INDEX idx_mushex_attempts_intent_idempotency
    ON mushex_payment_attempts (intent_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
