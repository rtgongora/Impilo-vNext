-- ============================================================================
-- V025 — Reactivate the Rito citizen/caregiver case-read rules (G-RT-01 complete)
-- V024 deactivated rito-case-read-citizen / rito-case-read-caregiver because the
-- service had no own-subject binding (an IDOR risk). That binding now exists in
-- CaseService (requireReadable / isOwnCase: a CITIZEN/CAREGIVER may only read a case
-- they reported or are the subject of; all case sub-reads funnel through get(), so
-- timeline/parties/links/messages inherit it; list() returns own-only). With the
-- in-service binding in place, client case-tracking ("client.viewer own-subject only")
-- is restored safely.
-- ============================================================================

UPDATE tshepo_authz.policy_rule
   SET active = true
 WHERE name IN ('rito-case-read-citizen', 'rito-case-read-caregiver');
