-- ============================================================================
-- V027 — Inpatient Suite (WS#6) policy rules
-- Adds ext_authz ALLOW rules for the NEW inpatient-suite endpoints that V019 did
-- not cover: deterioration-escalation respond/acknowledge, discharge-summary
-- save/finalise, and bed assignment. V019 already covers the original clinical
-- writes (observations/ews/mar/administer/fluid-balance/ward-charts/...).
--
-- NOTE: V026 is reserved by a concurrent gap-closure workstream; this WS#6 seed
-- therefore lands at V027 (next free ≥ V027 on the base, latest was V025).
--
-- COLLISION SAFETY (same lesson as V019): the PDP derives resource_type as the
-- last path segment, which collides for generic words (`respond`, `acknowledge`,
-- `finalise`, `assign`, `discharge-summary`). Each rule pins a `path_contains`
-- to its specific inpatient endpoint so a same-segment endpoint in another
-- service is NOT granted here.
--
-- Scope: actor_type=PROVIDER, a clinical role, action=POST, facility_scope=true,
-- purpose TREATMENT. Senior-gated actions (discharge finalise) are restricted to
-- senior roles (DOCTOR/WARD_MANAGER); NURSE is intentionally NOT granted finalise.
-- The in-service guards (requireActiveCareContext, clearance gating) bind subject
-- + workflow state; these gateway rules make the routes reachable. DENY-wins +
-- first-matching-ALLOW semantics unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Bed assignment (POST /internal/v1/beds/{id}/assign) — segment `assign` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-bed-assign-nurse',
 'NURSE: assign a patient to a bed (bed-safety gate enforced in-service).',
 'PROVIDER', 'NURSE', 'assign', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/beds/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-bed-assign-clinician',
 'CLINICIAN: assign a patient to a bed.',
 'PROVIDER', 'CLINICIAN', 'assign', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/beds/"}', true),

-- ── Escalation acknowledge (POST /internal/v1/escalations/{id}/acknowledge) — `acknowledge` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-escalation-ack-nurse',
 'NURSE: acknowledge a deterioration escalation.',
 'PROVIDER', 'NURSE', 'acknowledge', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/escalations/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-escalation-ack-clinician',
 'CLINICIAN: acknowledge a deterioration escalation.',
 'PROVIDER', 'CLINICIAN', 'acknowledge', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/escalations/"}', true),

-- ── Escalation respond (POST /internal/v1/escalations/{id}/respond) — `respond` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-escalation-respond-nurse',
 'NURSE: respond to a deterioration escalation.',
 'PROVIDER', 'NURSE', 'respond', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/escalations/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-escalation-respond-clinician',
 'CLINICIAN: respond to a deterioration escalation.',
 'PROVIDER', 'CLINICIAN', 'respond', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/escalations/"}', true),

-- ── Discharge summary draft (POST /internal/v1/discharge-summary) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-summary-clinician',
 'CLINICIAN: create/update a discharge summary draft.',
 'PROVIDER', 'CLINICIAN', 'discharge-summary', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/discharge-summary"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-summary-nurse',
 'NURSE: create/update a discharge summary draft.',
 'PROVIDER', 'NURSE', 'discharge-summary', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/discharge-summary"}', true),

-- ── Discharge summary FINALISE (POST /internal/v1/discharge-summary/{id}/finalise) — senior only ──
-- `finalise` COLLIDES; pinned. NURSE is intentionally NOT granted (senior decision; OPA + in-service mirror).
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-finalise-doctor',
 'DOCTOR: finalise a discharge summary (clearance-gated in-service).',
 'PROVIDER', 'DOCTOR', 'finalise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/discharge-summary/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-finalise-ward-manager',
 'WARD_MANAGER: finalise a discharge summary.',
 'PROVIDER', 'WARD_MANAGER', 'finalise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/discharge-summary/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-finalise-clinician',
 'CLINICIAN: finalise a discharge summary.',
 'PROVIDER', 'CLINICIAN', 'finalise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/discharge-summary/"}', true);
