-- ============================================================================
-- V024 — Narrow the Rito citizen/caregiver case-read rules (corrective on V021)
-- The V021 rito-case-read-citizen / rito-case-read-caregiver rules granted broad
-- GET on /rito/cases, but rito case-read is not yet actor/subject-bound in-service,
-- so a citizen could read ANY case (an IDOR — rito cases include sensitive
-- complaints/safety concerns). The spec intends "client.viewer own-subject only",
-- which requires an in-service binding (reporterActorId / subjectHealthId == actor)
-- that does not exist yet.
--
-- These read rules are deactivated to close the exposure immediately. Bound
-- citizen case-tracking returns once the in-service own-subject guard is added to
-- the rito case get/list path (mirroring patient-safety requireOwnReportForCitizen).
-- Case CREATE (rito-case-create-any) and all staff rules are unaffected.
-- ============================================================================

UPDATE tshepo_authz.policy_rule
   SET active = false
 WHERE name IN ('rito-case-read-citizen', 'rito-case-read-caregiver');
