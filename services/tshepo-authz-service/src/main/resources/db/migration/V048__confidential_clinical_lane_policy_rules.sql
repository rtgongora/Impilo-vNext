-- =====================================================================================
-- TSHEPO-authz :: V048 — Confidential clinical lane (SPECIALLY_PROTECTED enforcement)
--
-- The confidential lane is closed by default and needs no DENY seed: the PDP denies with
-- NO_ALLOW_RULE when nothing matches, and PolicyEngine Step 4.7 refuses protected content
-- to any requester without an explicit entitlement even when a rule DOES match. These
-- seeds are therefore purely additive — they say who may reach the lane at all, and which
-- of those additionally hold the protected-content entitlement.
--
-- HOW THE ENTITLEMENT IS GRANTED. A rule carrying
--     "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}
-- is the governance channel: VisibilityObligationComposer reads the overlay and the composed
-- tier survives, so the actor can receive protected content. A rule WITHOUT that overlay
-- reaches the lane but is clamped to FULL_IDENTIFIED_CLINICAL and the PEP suppresses the
-- protected records — which is what a clinician entering the adolescent service without a
-- confidentiality entitlement should see.
--
-- WHAT NO RULE CAN DO. Step 4.7 refuses a delegated act (guardian / caregiver acting for
-- another person) on this lane unconditionally, before any overlay is consulted. Adding a
-- rule here cannot grant a guardian access to a confidential adolescent record. That is
-- deliberate: if the governance channel could widen it, the hole this wave closed would
-- simply reopen through a seed.
--
-- ⚠ ROLE ASSIGNMENT REQUIRES RATIFICATION. Which cadres hold the confidentiality
-- entitlement is a national policy question, not an engineering one. The rows below are a
-- minimal, defensible seed: the person themselves, and the clinicians staffing the service
-- the record belongs to. MoHCC should ratify the list before go-live. Add a role here and
-- it gains access to adolescent sexual-health and safeguarding records — review accordingly.
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
 '{"path_contains": "/confidential/"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-self',
 'CITIZEN: contribute to one''s own specially-protected record (e.g. a self-reported concern).',
 NULL, 'CITIZEN', NULL, 'POST', 'TREATMENT', false, false, 'ALLOW', 40,
 '{"path_contains": "/confidential/"}', true),

-- ── Clinicians staffing the confidential service ─────────────────────────────────────
-- These DO carry the entitlement overlay. Facility-scoped: the entitlement is to the service
-- you are working in, not to protected records nationwide. min_loa 3 because a confidential
-- disclosure is not something a weakly-assured session should be able to read.
('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-read-clinician',
 'CLINICIAN (in facility scope): read specially-protected clinical content. Carries the governed SPECIALLY_PROTECTED_CLINICAL entitlement.',
 'PROVIDER', 'CLINICIAN', NULL, 'GET', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-clinician',
 'CLINICIAN (in facility scope): record specially-protected clinical content.',
 'PROVIDER', 'CLINICIAN', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-read-nurse',
 'NURSE (in facility scope): read specially-protected clinical content. Nurses staff most adolescent and sexual-health services, so excluding them would push the work outside the record.',
 'PROVIDER', 'NURSE', NULL, 'GET', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-write-nurse',
 'NURSE (in facility scope): record specially-protected clinical content.',
 'PROVIDER', 'NURSE', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/confidential/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true),

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
 '{"path_contains": "/safeguarding/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'confidential-lane-safeguarding-focal-write',
 'FACILITY_SAFETY_FOCAL (in facility scope): record the safeguarding protection response.',
 'PROVIDER', 'FACILITY_SAFETY_FOCAL', NULL, 'POST', 'TREATMENT', true, false, 'ALLOW', 50,
 '{"path_contains": "/safeguarding/", "min_loa": 3, "visibility": {"visibilityTier": "SPECIALLY_PROTECTED_CLINICAL"}}', true)

ON CONFLICT DO NOTHING;
