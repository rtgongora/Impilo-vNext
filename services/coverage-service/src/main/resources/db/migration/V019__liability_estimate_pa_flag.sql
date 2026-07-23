-- =============================================
-- Service : coverage-service (Ruvimbo)
-- Flyway  : V019__liability_estimate_pa_flag.sql
--
-- OF-B8 (Vol II §8.7/§10.6) — surface the benefit's prior-authorisation
-- requirement on the liability estimate itself, so offer/checkout callers
-- (msika-flow step 7) learn the PA posture from the SAME call that prices the
-- benefit, and the audited estimate row records the PA assumption it was
-- computed under (§10.5 assumptions contract).
--
-- Additive only: no new PA machine — cv_authorisations (14-status, LIVE)
-- remains the sole authorisation truth; this is a recorded flag copied from
-- cv_benefit_definitions.requires_authorisation at estimate time.
-- =============================================

ALTER TABLE cv_liability_estimates
    ADD COLUMN requires_authorisation BOOLEAN NOT NULL DEFAULT FALSE;
