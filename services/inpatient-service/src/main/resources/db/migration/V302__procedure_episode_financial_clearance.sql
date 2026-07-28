-- inpatient-service V302 — Clinical Procedures Pipeline P12 (§23 financial).
--
-- The audit rates §23 PARTIAL: "costa surgical bundle real; estimate/authorisation/denial/
-- appeal absent; emergency-rules invariant unproven." Estimate/authorisation/denial/appeal are
-- NOT actually absent — coverage-service (Ruvimbo) has a full authorisation state machine and
-- appeals, and COSTA has ServiceAccessDecisionEntity/Controller with exactly the vocabulary this
-- gate needs (ALLOWED_WITHOUT_PAYMENT, PAYMENT_REQUIRED_BEFORE_SERVICE, DEPOSIT_REQUIRED,
-- AUTHORISATION_REQUIRED, COVERED_BY_PAYER, EXEMPT, WAIVER_REQUIRED, DEFERRED_PAYMENT_ALLOWED,
-- BLOCKED_PENDING_PAYMENT, BLOCKED_PENDING_AUTHORISATION, EMERGENCY_OVERRIDE) — none of it was
-- ever wired to the clinical execution path. TheatreService/ProcedureEpisodeService never
-- referenced COSTA, payment or authorisation at all before this migration. That is the real gap:
-- not missing financial machinery, but a missing CONNECTION between it and theatre start.
--
-- ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES is explicit that procedures-service/
-- surgery-service must never become "a second payment truth" — so this stays in
-- inpatient-service (the execution owner, same P8 boundary as specimens/implants) and is a
-- PROJECTION of COSTA's decision at check time, not a second source of truth: COSTA is still
-- asked fresh on every start attempt (see ProcedureEpisodeService.requireFinancialClearance);
-- these columns exist for the audit trail (who/when this episode was checked and what COSTA
-- said), the same shape as site_side_confirmed_by/at and consent_verified.
--
-- THE EMERGENCY-RULES INVARIANT, now enforced not just asserted: an EMERGENCY or IMMEDIATE
-- triage_priority episode ALWAYS bypasses this gate — proven by
-- ProcedureFinancialClearanceTest and the runtime-proof rig, not left as a prose guardrail.
--
-- THE OTHER HALF OF THE INVARIANT — "payment state never masquerades as clinical cancellation":
-- a financial block REFUSES the start action (409, episode status unchanged) exactly like the
-- site-and-side gate. Nothing about this migration or the gate it supports ever transitions an
-- episode to CANCELLED for a financial reason — there is no code path that could, so the
-- invariant holds by the absence of one, not by a check that could be bypassed.

ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS financial_clearance_status      VARCHAR(48),
    ADD COLUMN IF NOT EXISTS financial_clearance_checked_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS financial_clearance_checked_by  VARCHAR(128);

-- Actor+timestamp pairing — the same law every other confirmation in this schema follows.
ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_financial_clearance_pair CHECK (
        (financial_clearance_checked_at IS NULL) = (financial_clearance_checked_by IS NULL));

-- The status projection is one of COSTA's own ServiceAccessStatus values — kept in sync with
-- costa.domain.enums.ServiceAccessStatus by hand (a cross-service Java enum, not something a
-- DB-level FK can reference). A value drifting out of sync here would be a deserialisation
-- error in ProcedureEpisodeService long before it reached this constraint.
-- 'NOT_YET_EVALUATED' is NOT one of COSTA's values — it is what this service records when the
-- gate ran and asked COSTA but no decision existed for the encounter at all, distinct from a
-- NULL column (the gate was never invoked, which should not happen once this is wired).
ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_financial_clearance_status CHECK (
        financial_clearance_status IS NULL OR financial_clearance_status IN (
            'ALLOWED_WITHOUT_PAYMENT', 'PAYMENT_REQUIRED_BEFORE_SERVICE', 'DEPOSIT_REQUIRED',
            'AUTHORISATION_REQUIRED', 'COVERED_BY_PAYER', 'EXEMPT', 'WAIVER_REQUIRED',
            'DEFERRED_PAYMENT_ALLOWED', 'BLOCKED_PENDING_PAYMENT', 'BLOCKED_PENDING_AUTHORISATION',
            'EMERGENCY_OVERRIDE', 'NOT_YET_EVALUATED'));
