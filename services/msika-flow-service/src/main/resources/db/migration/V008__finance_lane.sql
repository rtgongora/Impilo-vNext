-- =============================================
-- Service : msika-flow-service (marketplace transaction plane)
-- Database: msika_flow
-- Flyway  : V008__finance_lane.sql
--
-- Wave OF-B finance lane (OF-B8/OF-B9/OF-B10) — §8.7/§8.8 Stage G/H seams:
--
--  * mf_marketplace_requests gains the funding-source election (§10.3) and the
--    Ruvimbo member-coverage reference for PAYER_COVERED flows. coverage_id is
--    request-row-only truth: it is NEVER published in the §11.8 snapshot,
--    vendor views or event payloads.
--
--  * mf_selections gains the step-7 financial snapshot (estimate-never-final,
--    §10.5), the OF-B8 PA posture (projection over the LIVE 14-status
--    cv_authorisations machine — Ruvimbo remains the SoR), and the OF-B10
--    payment linkage: one MusheX intent per selection payment obligation,
--    intent -> PAID, NO two-phase capture (settled not-build, §10.7).
--
--  * SelectionStatus gains AWAITING_PAYMENT (step 8 held; resumed by the
--    mushex.payment.status.changed callback). status stays VARCHAR(16) —
--    'AWAITING_PAYMENT' is 16 chars exactly.
-- =============================================

ALTER TABLE mf_marketplace_requests
    ADD COLUMN funding_mode VARCHAR(16),          -- SELF_PAY | PAYER_COVERED (NULL => SELF_PAY, fail-closed)
    ADD COLUMN coverage_id  UUID;                 -- Ruvimbo cv member coverage ref (payer flows only)

ALTER TABLE mf_selections
    ADD COLUMN payment_intent_id   VARCHAR(64),   -- real MusheX intent id (never fabricated locally)
    ADD COLUMN payment_status      VARCHAR(24),   -- projection of the MusheX IntentStatus
    ADD COLUMN shortfall_amount    NUMERIC(14,2), -- §10.4 due_now the intent was opened for
    ADD COLUMN shortfall_currency  VARCHAR(8),
    ADD COLUMN financial_json      JSONB,         -- step-7 financial block snapshot (§10.5 estimate label inside)
    ADD COLUMN pa_status           VARCHAR(24),   -- OF-B8 PA posture at step 7
    ADD COLUMN pa_reference        VARCHAR(64);   -- cv_authorisations id backing the posture

-- Payment-callback resume path: intent id -> held selection.
CREATE INDEX idx_mf_sel_payment_intent ON mf_selections(payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

-- AWAITING_PAYMENT timeout sweep.
CREATE INDEX idx_mf_sel_status ON mf_selections(status);
