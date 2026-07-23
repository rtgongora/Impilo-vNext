-- =====================================================================================
-- Telemonitoring :: V002 — programme governance + plan-activation side-effects (OF-B21)
--
-- 1) Programme catalogue governance (§14.1): profiles are clinically governed and
--    versioned — additive columns for versioned metadata, effective windows and
--    RETIRE-not-DELETE lifecycle. The 20 seeded launch profiles keep working: every
--    column is additive with a safe default (version=1, retired=false, open window).
-- 2) MVUMO consent pointers on the plan (§14.2 capture item 9): the plan stores
--    POINTERS ONLY (consent_reference / consent_status) — the consent journey and
--    consent content stay in MVUMO. Activation fails closed only on an explicit
--    refusal (REFUSED / REVOKED); an absent consent record is honest UNVERIFIED and
--    activation proceeds with that recorded.
-- 3) Review-cadence timer groundwork (§14.2 capture item 6): last_review_at feeds the
--    scheduled sweep that emits telemonitoring.plan.review_due.v1 when
--    now > last_review + cadence; review_due_notified_at de-duplicates the emission
--    (one review_due signal per overdue review period, re-armed by a recorded review).
-- =====================================================================================

-- ── Programme governance (retire never deletes) ──────────────────────────────────────
ALTER TABLE telemonitoring.tm_monitoring_programmes
    ADD COLUMN version         INT         NOT NULL DEFAULT 1,
    ADD COLUMN metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN effective_from  TIMESTAMPTZ,
    ADD COLUMN effective_to    TIMESTAMPTZ,
    ADD COLUMN retired         BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN retired_at      TIMESTAMPTZ,
    ADD COLUMN retired_by      VARCHAR(128),
    ADD COLUMN retired_reason  VARCHAR(512),
    ADD COLUMN updated_by      VARCHAR(128);

COMMENT ON COLUMN telemonitoring.tm_monitoring_programmes.version IS
    'Governance version — bumped on every governed update; plans reference code + version';
COMMENT ON COLUMN telemonitoring.tm_monitoring_programmes.retired IS
    'RETIRE never DELETE: a retired profile stays for referential/audit truth; new plans are refused';

-- ── Plan: MVUMO consent pointers + review-cadence groundwork ─────────────────────────
ALTER TABLE telemonitoring.tm_monitoring_plans
    ADD COLUMN consent_reference        VARCHAR(128),
    ADD COLUMN consent_status           VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    -- UNVERIFIED / PENDING / GRANTED / REFUSED / REVOKED (pointer to the MVUMO journey state)
    ADD COLUMN consent_updated_at       TIMESTAMPTZ,
    ADD COLUMN last_review_at           TIMESTAMPTZ,
    ADD COLUMN review_due_notified_at   TIMESTAMPTZ;

-- Sweep support: ACTIVE plans that declare a cadence.
CREATE INDEX idx_tm_plan_review_sweep
    ON telemonitoring.tm_monitoring_plans (status)
    WHERE review_cadence IS NOT NULL;
