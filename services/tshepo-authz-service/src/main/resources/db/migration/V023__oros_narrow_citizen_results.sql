-- ============================================================================
-- V023 — Narrow the OROS citizen results rule (corrective on V022)
-- The V022 'oros-results-citizen' rule granted CITIZEN GET on the whole
-- /v1/results tree, but those routes are PROVIDER-facing (workspace results
-- inbox, report versions) — a citizen has no clean own-results route there
-- (patient result-view is the patient-lane feature, G-CT-01, not yet built).
-- A broad CITIZEN /v1/results grant would expose provider-facing result lists,
-- so it is deactivated here. The 'oros-qr-claim-citizen' rule (claim a QR/secure
-- code order) stays — it is a legitimate, scoped citizen action.
--
-- When the patient lane (G-CT-01) lands a narrow, subject-bound patient
-- result-view endpoint, its own rule + in-service binding will replace this.
-- ============================================================================

UPDATE tshepo_authz.policy_rule
   SET active = false
 WHERE name = 'oros-results-citizen';
