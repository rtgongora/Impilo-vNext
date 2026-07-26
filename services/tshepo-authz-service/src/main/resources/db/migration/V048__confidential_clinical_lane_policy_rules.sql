-- =====================================================================================
-- TSHEPO-authz :: V048 — Confidential clinical lane (SPECIALLY_PROTECTED entitlements)
--
-- ⚠ EVERY RULE BELOW SHIPS INACTIVE (active = false). This migration lands the shape of the
-- entitlement, not the entitlement itself. Two things must happen before any row is activated:
--   1. MoHCC ratifies the confidentiality policy pack
--      (services/tshepo-authz-service/src/main/resources/policy/adolescent-confidentiality-pack.json,
--      currently ENGINEERING_SEED) — the confidentiality ages and the confidential code list are
--      legal and policy questions, not engineering ones.
--   2. MoHCC ratifies WHICH CADRES hold each category. Activating a row here gives that role
--      access to adolescent sexual-health or safeguarding records.
-- Until then the mechanism runs in SHADOW (tshepo.authz.confidentiality-mode), audits what it
-- would have done, and changes nothing. That is deliberate: a record must never carry a protection
-- label that does not protect it, and a half-live control is exactly that label.
--
-- HOW THE ENTITLEMENT IS EXPRESSED. A rule grants
--     "visibility": {"confidentialCategories": ["SAFEGUARDING", ...]}
-- Confidentiality is a CATEGORY OBLIGATION, not a visibility tier. A tier is a total order, so a
-- "specially protected" tier above FULL_IDENTIFIED_CLINICAL would assert that a safeguarding grant
-- implies access to the whole clinical record. It must not: the safety focal reads the safeguarding
-- disclosure, and the sexual-health nurse does not thereby reach the mental-health notes. A legacy
-- overlay naming only visibilityTier grants no confidential category at all.
--
-- Grants are additionally narrowed at runtime by the ratified pack, so a rule naming a category the
-- pack does not carry grants nothing and fails visibly (PROTECTED_RECORD_PACK_INERT).
--
-- WHAT NO RULE CAN DO. Step 4.7 refuses a delegated act (guardian / caregiver acting for another
-- person) on this lane before any overlay is consulted. Adding a rule here cannot grant a guardian
-- access to a confidential adolescent record — if the governance channel could widen that, the hole
-- this wave closed would simply reopen through a seed.
--
-- WHAT ALWAYS WORKS. EMERGENCY and BREAK_GLASS purpose-of-use waive the category requirement
-- entirely, matching ClinicalAccessGuard in pct-service, and every waiver is audited. Over-
-- restricting kills people too: a teenager arriving unconscious whose HIV status explains the
-- presentation must not be invisible to the clinician treating them.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── The person themselves ────────────────────────────────────────────────────────────
-- Reaches the lane; the entitlement itself comes from Step 4.7 recognising subject == actor,
-- NOT from an overlay here. Deliberate: an overlay would hand the protected tier to every
-- CITIZEN matching this route, including one who arrived at someone else's record.
('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-read-self',
 'CITIZEN: read one''s own specially-protected record. The entitlement is established by Step 4.7 from subject == actor, not by this rule.',
 NULL, 'CITIZEN', NULL, 'GET', 'TREATMENT', false, false, 'ALLOW', 40,
 '{"path_contains": "/confidential/"}', false),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-self',
 'CITIZEN: contribute to one''s own specially-protected record (e.g. a self-reported concern).',
 NULL, 'CITIZEN', NULL, 'POST', 'TREATMENT', false, false, 'ALLOW', 40,
 '{"path_contains": "/confidential/"}', false),

-- ── Clinicians staffing the confidential service ─────────────────────────────────────
-- These DO carry the entitlement overlay. Facility-scoped: the entitlement is to the service
-- you are working in, not to protected records nationwide. min_loa 3 because a confidential
-- disclosure is not something a weakly-assured session should be able to read.
('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-read-clinician',
 'CLINICIAN (in facility scope): read specially-protected clinical content. Carries the governed SPECIALLY_PROTECTED_CLINICAL entitlement.',
 'PROVIDER', 'CLINICIAN', NULL, 'GET', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"confidentialCategories": ["SEXUAL_REPRODUCTIVE_HEALTH", "HIV", "MENTAL_HEALTH", "SUBSTANCE_USE"]}}', false),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-clinician',
 'CLINICIAN (in facility scope): record specially-protected clinical content.',
 'PROVIDER', 'CLINICIAN', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"confidentialCategories": ["SEXUAL_REPRODUCTIVE_HEALTH", "HIV", "MENTAL_HEALTH", "SUBSTANCE_USE"]}}', false),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-read-nurse',
 'NURSE (in facility scope): read specially-protected clinical content. Nurses staff most adolescent and sexual-health services, so excluding them would push the work outside the record.',
 'PROVIDER', 'NURSE', NULL, 'GET', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"confidentialCategories": ["SEXUAL_REPRODUCTIVE_HEALTH", "HIV", "MENTAL_HEALTH", "SUBSTANCE_USE"]}}', false),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-nurse',
 'NURSE (in facility scope): record specially-protected clinical content.',
 'PROVIDER', 'NURSE', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"confidentialCategories": ["SEXUAL_REPRODUCTIVE_HEALTH", "HIV", "MENTAL_HEALTH", "SUBSTANCE_USE"]}}', false),

-- ── Safeguarding ─────────────────────────────────────────────────────────────────────
-- A safeguarding concern is protected content whose whole purpose is to be acted on by the
-- protection lead. Purpose is TREATMENT because PurposeOfUse has no safeguarding or
-- care-coordination code: OPERATIONS would be more honest about the role but its obligations
-- set clinicalAccess=NONE, which would blank the very disclosure being acted on. Recorded as a
-- known imprecision in the audit trail rather than papered over — note that several existing
-- seeds (e.g. V017) declare a 'CARE_COORDINATION' purpose that is not in the enum at all, so
-- those rules can never match a live request.
('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-safeguarding-focal',
 'FACILITY_SAFETY_FOCAL (in facility scope): read safeguarding disclosures for the protection response.',
 'PROVIDER', 'FACILITY_SAFETY_FOCAL', NULL, 'GET', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/safeguarding/", "min_loa": 3, "visibility": {"confidentialCategories": ["SAFEGUARDING", "GENDER_BASED_VIOLENCE"]}}', false),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-safeguarding-focal-write',
 'FACILITY_SAFETY_FOCAL (in facility scope): record the safeguarding protection response.',
 'PROVIDER', 'FACILITY_SAFETY_FOCAL', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/safeguarding/", "min_loa": 3, "visibility": {"confidentialCategories": ["SAFEGUARDING", "GENDER_BASED_VIOLENCE"]}}', false)

ON CONFLICT DO NOTHING;
