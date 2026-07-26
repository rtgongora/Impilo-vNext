-- =====================================================================================
-- TSHEPO-authz :: V049 — align seeded policy_rule purposes with the PurposeOfUse enum
--
-- policy_rule.purpose is a plain VARCHAR, so Postgres accepted purpose codes that the
-- runtime cannot produce or match. PolicyEngine.parsePurpose rejects anything outside
-- PurposeOfUse with INVALID_PURPOSE at Step 2 — before rule matching — so a rule seeded
-- with an unknown purpose is dead: the access it was written to grant never existed.
--
-- A sweep of V001..V047 found 11 such rows, in three codes:
--
--   CARE_COORDINATION  (V017, 4 rows)  — also a live call-site value in khuluma-service,
--                                        vito patient-share and the nhume write-back
--                                        gateway. Kept: PurposeOfUse now carries it
--                                        (HL7 ActReason COC, a specialization of TREAT).
--   REGULATORY_DUTY    (V045/46/47, 5 rows, all active=false SHADOW) — kept: PurposeOfUse
--                                        now carries it, with a no-clinical-access envelope.
--   PERSONAL_CARE      (V017, 2 rows)  — used nowhere else in the estate. The concept is
--                                        "a person acting in their own context", which the
--                                        TypeScript contract already named SELF_SERVICE.
--                                        Rewritten below to converge the two vocabularies.
--
-- Only the PERSONAL_CARE rows need SQL: the other two codes became valid when the enum
-- was extended. V017 itself is left untouched — it is an applied migration and editing it
-- would break Flyway checksum validation on every environment that has already run it.
--
-- SCOPE NOTE — this migration fixes the PURPOSE axis only. The four V017 khuluma rules are
-- independently unreachable on a second axis: they carry invented resource_type values
-- ('khuluma-conversation', 'khuluma-call', 'khuluma-patient-link') and no path_contains
-- condition, while AuthzInternalRequest.deriveResourceType returns the last path segment of
-- the request (so '/internal/v1/khuluma/conversations' yields 'conversations'). Repairing
-- that would newly ACTIVATE provider comms and patient-link access that has never been live,
-- which is a grant decision for the Khuluma lane — deliberately not made here. Until it is
-- made, the khuluma rules stay inert and khuluma traffic is denied NO_MATCHING_RULES.
-- Guarded going forward by PolicyRuleSeedVocabularyTest.
-- =====================================================================================

UPDATE tshepo_authz.policy_rule
   SET purpose    = 'SELF_SERVICE',
       updated_at = now()
 WHERE purpose = 'PERSONAL_CARE'
   AND name IN ('khuluma-comms-citizen', 'khuluma-call-citizen');
