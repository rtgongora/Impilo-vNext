-- ============================================================================
-- V019 — Inpatient clinical-write policy rules
-- Enables the fine-grained ext_authz ALLOW rules for the inpatient-service
-- single-subject clinical writes. Before this, these endpoints had NO matching
-- policy rule, so the PDP fail-closed (NO_MATCHING_RULES -> DENY) — documented as
-- residual #4 of docs/security/clinical-write-subject-relationship-control.md.
--
-- Mirrors V018 (clinical write + attendance). care-plans are already covered by
-- the V018 "/care-plans" path_contains rule (inpatient and pct share that segment),
-- so they are NOT re-seeded here.
--
-- COLLISION SAFETY: the PDP derives resource_type as the last path segment, which
-- collides for generic words (`entries`, `sync`, `administer`, `activate`, `init`).
-- Each rule carries a `path_contains` condition pinning it to its specific inpatient
-- endpoint, so a same-segment endpoint in another service is NOT granted here.
-- (See PolicyEngine.evaluateConditions / the V018 lesson.)
--
-- Scope per rule: actor_type=PROVIDER, a single clinical role, action=POST,
-- facility_scope=true, purpose TREATMENT (experience-shell default; the in-service
-- guard waives for EMERGENCY/BREAK_GLASS). DENY-wins + first-matching-ALLOW unchanged.
--
-- CONSENT SCOPE: these are POST-to-collection creates (subject CPID is in the body,
-- not the path), so ext_authz enforces the RBAC dimensions only; subject-level
-- relationship is enforced in-service by
-- InpatientClinicalService.requireActiveCareContext (the strict active-care-context
-- guard already landed + tested, InpatientClinicalDepthIT). These gateway rules make
-- the routes reachable; the in-service guard binds the subject.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Fluid balance (POST /internal/v1/fluid-balance) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-fluid-balance-clinician',
 'CLINICIAN: record an inpatient fluid-balance entry.',
 'PROVIDER', 'CLINICIAN', 'fluid-balance', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/fluid-balance"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-fluid-balance-nurse',
 'NURSE: record an inpatient fluid-balance entry.',
 'PROVIDER', 'NURSE', 'fluid-balance', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/fluid-balance"}', true),
-- ── Observations (POST /internal/v1/observations) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-observations-clinician',
 'CLINICIAN: record an inpatient observation.',
 'PROVIDER', 'CLINICIAN', 'observations', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/observations"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-observations-nurse',
 'NURSE: record an inpatient observation.',
 'PROVIDER', 'NURSE', 'observations', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/observations"}', true),
-- ── Ward-chart entry (POST /internal/v1/ward-charts/{chartType}/entries) — segment `entries` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ward-chart-entry-clinician',
 'CLINICIAN: record a ward-chart entry.',
 'PROVIDER', 'CLINICIAN', 'entries', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/ward-charts/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ward-chart-entry-nurse',
 'NURSE: record a ward-chart entry.',
 'PROVIDER', 'NURSE', 'entries', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/ward-charts/"}', true),
-- ── MAR sync (POST /internal/v1/mar/sync) — segment `sync` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-mar-sync-clinician',
 'CLINICIAN: sync the medication administration record schedule.',
 'PROVIDER', 'CLINICIAN', 'sync', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/mar/sync"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-mar-sync-nurse',
 'NURSE: sync the medication administration record schedule.',
 'PROVIDER', 'NURSE', 'sync', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/mar/sync"}', true),
-- ── MAR administer (POST /internal/v1/mar/administer) — segment `administer` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-mar-administer-clinician',
 'CLINICIAN: administer a medication (MAR).',
 'PROVIDER', 'CLINICIAN', 'administer', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/mar/administer"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-mar-administer-nurse',
 'NURSE: administer a medication (MAR).',
 'PROVIDER', 'NURSE', 'administer', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/mar/administer"}', true),
-- ── EWS (POST /internal/v1/ews) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ews-clinician',
 'CLINICIAN: record an early-warning-score entry.',
 'PROVIDER', 'CLINICIAN', 'ews', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/ews"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ews-nurse',
 'NURSE: record an early-warning-score entry.',
 'PROVIDER', 'NURSE', 'ews', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/ews"}', true),
-- ── Emergency activate (POST /internal/v1/emergency/activate) — segment `activate` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-emergency-activate-clinician',
 'CLINICIAN: activate an inpatient emergency/resuscitation protocol.',
 'PROVIDER', 'CLINICIAN', 'activate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/emergency/activate"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-emergency-activate-nurse',
 'NURSE: activate an inpatient emergency/resuscitation protocol.',
 'PROVIDER', 'NURSE', 'activate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/emergency/activate"}', true),
-- ── APGAR (POST /internal/v1/apgar) — neonatal; midwife included ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-apgar-clinician',
 'CLINICIAN: record a neonatal APGAR score.',
 'PROVIDER', 'CLINICIAN', 'apgar', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/apgar"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-apgar-nurse',
 'NURSE: record a neonatal APGAR score.',
 'PROVIDER', 'NURSE', 'apgar', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/apgar"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-apgar-midwife',
 'MIDWIFE: record a neonatal APGAR score.',
 'PROVIDER', 'MIDWIFE', 'apgar', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 57, '{"path_contains": "/apgar"}', true),
-- ── Ward alert (POST /internal/v1/ward-alerts) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ward-alert-clinician',
 'CLINICIAN: raise a ward alert.',
 'PROVIDER', 'CLINICIAN', 'ward-alerts', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/ward-alerts"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-ward-alert-nurse',
 'NURSE: raise a ward alert.',
 'PROVIDER', 'NURSE', 'ward-alerts', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/ward-alerts"}', true),
-- ── Discharge clearances init (POST /internal/v1/discharge-clearances/init) — segment `init` COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-clearances-init-clinician',
 'CLINICIAN: initialise discharge clearances for an encounter.',
 'PROVIDER', 'CLINICIAN', 'init', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/discharge-clearances/init"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inpatient-discharge-clearances-init-nurse',
 'NURSE: initialise discharge clearances for an encounter.',
 'PROVIDER', 'NURSE', 'init', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/discharge-clearances/init"}', true);
