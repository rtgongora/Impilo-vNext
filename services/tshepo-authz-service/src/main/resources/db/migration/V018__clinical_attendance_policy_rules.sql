-- ============================================================================
-- V018 — Clinical write + workforce attendance policy rules
-- Enables the fine-grained ext_authz ALLOW rules that the pct-service and
-- vashandi-workforce-service controllers reference ("authz enforced at ext_authz;
-- rule lives in track P"). Before this, these endpoints had NO matching policy
-- rule, so the PDP fail-closed (NO_MATCHING_RULES -> DENY).
--
-- COLLISION SAFETY: the PDP derives resource_type as the last path segment, which
-- collides across services for generic words (e.g. `decision` is the last segment
-- of 15 endpoints). Each rule below therefore carries a `path_contains` condition
-- that pins it to its specific endpoint, so a same-segment endpoint in another
-- service is NOT granted by these clinical rules. (See PolicyEngine.evaluateConditions.)
--
-- Scope per rule: actor_type=PROVIDER, a single clinical role, action=POST,
-- facility_scope=true (a facility context must be present). Clinical writes run
-- under purpose TREATMENT (the experience-shell default); attendance check-in is
-- operational, so its purpose is left wildcard but kept tight via role + path.
-- DENY-wins + first-matching-ALLOW semantics are unchanged.
--
-- CONSENT SCOPE: these are POST-to-collection creates (no subject UUID in the path),
-- so ext_authz cannot resolve the patient to evaluate subject-level consent — the
-- consent step keys on a path resource id. These rules therefore enforce the RBAC
-- dimensions (clinical role + actor type + facility + purpose) only; subject-level
-- consent / legal basis for the created clinical record remains the responsibility
-- of the clinical workflow in the owning service (the clinician acts under TREATMENT
-- with facility context). Adding these resource types to CLINICAL_RESOURCE_TYPES
-- would not gate them correctly here (no subject id to evaluate) — a future
-- enhancement could pass the subject via X-Subject-ID for consent at create time.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Cadre decision (POST /v1/cadre/decision) — resource_type `decision` COLLIDES; pinned by path ──
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-cadre-decision-clinician',
    'CLINICIAN: resolve a cadre-gated clinical decision (pct cadre engine).',
    'PROVIDER', 'CLINICIAN', 'decision', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 55, '{"path_contains": "/cadre/decision"}', true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-cadre-decision-nurse',
    'NURSE: resolve a cadre-gated clinical decision (pct cadre engine).',
    'PROVIDER', 'NURSE', 'decision', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 56, '{"path_contains": "/cadre/decision"}', true
),
-- ── Care plan write (POST /v1/care-plans) — resource_type `care-plans` ──
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-care-plan-write-clinician',
    'CLINICIAN: create a care plan.',
    'PROVIDER', 'CLINICIAN', 'care-plans', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 55, '{"path_contains": "/care-plans"}', true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-care-plan-write-nurse',
    'NURSE: create a care plan.',
    'PROVIDER', 'NURSE', 'care-plans', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 56, '{"path_contains": "/care-plans"}', true
),
-- ── Problem write (POST /v1/problems) — resource_type `problems` ──
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-problem-write-clinician',
    'CLINICIAN: add a problem-list entry.',
    'PROVIDER', 'CLINICIAN', 'problems', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 55, '{"path_contains": "/problems"}', true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-problem-write-nurse',
    'NURSE: add a problem-list entry.',
    'PROVIDER', 'NURSE', 'problems', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 56, '{"path_contains": "/problems"}', true
),
-- ── Triage sort (POST /v1/sorting-desk/journeys/{id}/sort) — resource_type `sort` ──
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-triage-sort-clinician',
    'CLINICIAN: sort/triage a patient journey at the sorting desk.',
    'PROVIDER', 'CLINICIAN', 'sort', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 55, '{"path_contains": "/sorting-desk/journeys"}', true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'clinical-triage-sort-nurse',
    'NURSE: sort/triage a patient journey at the sorting desk.',
    'PROVIDER', 'NURSE', 'sort', 'POST', 'TREATMENT',
    true, false, 'ALLOW', 56, '{"path_contains": "/sorting-desk/journeys"}', true
),
-- ── Workforce adhoc attendance check-in (POST /v1/internal/vashandi/attendance/adhoc-check-in) ──
-- resource_type `adhoc-check-in`; operational action so purpose is wildcard, kept tight by
-- role + facility_scope + path. (The work-requires-assignment rule remains enforced in-service.)
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'workforce-adhoc-checkin-nurse',
    'NURSE: record an adhoc attendance check-in.',
    'PROVIDER', 'NURSE', 'adhoc-check-in', 'POST', NULL,
    true, false, 'ALLOW', 60, '{"path_contains": "/vashandi/attendance/adhoc-check-in"}', true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'workforce-adhoc-checkin-clinician',
    'CLINICIAN: record an adhoc attendance check-in.',
    'PROVIDER', 'CLINICIAN', 'adhoc-check-in', 'POST', NULL,
    true, false, 'ALLOW', 61, '{"path_contains": "/vashandi/attendance/adhoc-check-in"}', true
);
