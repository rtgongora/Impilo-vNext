-- ============================================================================
-- V020 — Patient Safety & Pharmacovigilance policy rules (SYS-1 enforcement)
-- Enforces docs/policy/patient-safety-policy-spec.md at the ext_authz gateway.
-- Before this, the patient-safety routes had no matching TSHEPO rule, so the PDP
-- fail-closed (NO_MATCHING_RULES -> DENY) — the spec was "queued, not enforced".
--
-- TAXONOMY: the spec's lowercase roles are reconciled to the canonical UPPERCASE_SNAKE
-- realm roles (added in the keycloak realm: MCAZ_REVIEWER, MCAZ_SUPERVISOR,
-- FACILITY_SAFETY_FOCAL, DISTRICT_PHO, VACCINATOR; provider = CLINICIAN/DOCTOR/NURSE/
-- MIDWIFE/PHARMACIST). The PolicyEngine matches rule.role by exact membership in the
-- actor's realm roles.
--
-- MECHANICS (verified against PolicyEngine/PolicyCacheService):
--   * resource_type = NULL is a WILDCARD (loaded for any resource_type); action/purpose
--     NULL are wildcards too. Each rule is pinned by a `path_contains` condition so it
--     only grants the intended patient-safety path (collision-safe across services).
--   * purpose = NULL (wildcard): the shell sets X-Purpose-Of-Use contextually; requiring
--     PHARMACOVIGILANCE here would risk false DENY. RBAC (role + path) is enforced now;
--     purpose-of-use tightening is a follow-up once the runtime purpose contract is pinned.
--   * facility_scope = false: MCAZ actors are national (no facility); citizen reporters
--     have no facility. Facility-focal facility-restriction + reporter own-report binding
--     are SUBJECT-relationship checks enforced IN-SERVICE (follow-up, mirroring
--     ClinicalAccessGuard) — these gateway rules enforce the RBAC dimension only.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Reporters: create/submit/read own reports (whole /patient-safety/reports tree) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-citizen',
 'CITIZEN: report own ADR/AEFI and respond to follow-ups.',
 NULL, 'CITIZEN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-caregiver',
 'CAREGIVER: report on behalf of a dependant.',
 NULL, 'CAREGIVER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-clinician',
 'CLINICIAN: create/submit ADR/AEFI from clinical context.',
 NULL, 'CLINICIAN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-doctor',
 'DOCTOR: create/submit ADR/AEFI from clinical context.',
 NULL, 'DOCTOR', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-nurse',
 'NURSE: create/submit ADR/AEFI from clinical context.',
 NULL, 'NURSE', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-midwife',
 'MIDWIFE: create/submit AEFI from clinical context.',
 NULL, 'MIDWIFE', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-pharmacist',
 'PHARMACIST: medicine-focused ADR reporting.',
 NULL, 'PHARMACIST', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-vaccinator',
 'VACCINATOR: AEFI-focused reporting.',
 NULL, 'VACCINATOR', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-report-facility-safety-focal',
 'FACILITY_SAFETY_FOCAL: report + view facility reports.',
 NULL, 'FACILITY_SAFETY_FOCAL', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/reports"}', true),
-- ── MCAZ case disposition (POST under /cases): triage/review/follow-up/vigiflow/export-ready/close/investigation ──
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-case-disposition-mcaz-reviewer',
 'MCAZ_REVIEWER: case disposition actions (triage/review/causality/follow-up/vigiflow/export-ready).',
 NULL, 'MCAZ_REVIEWER', NULL, 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-case-disposition-mcaz-supervisor',
 'MCAZ_SUPERVISOR: all case disposition + close/escalate/reassign.',
 NULL, 'MCAZ_SUPERVISOR', NULL, 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/cases"}', true),
-- ── MCAZ investigation update (PATCH /investigations/{id}) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-investigation-mcaz-reviewer',
 'MCAZ_REVIEWER: update assigned investigation.',
 NULL, 'MCAZ_REVIEWER', NULL, 'PATCH', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/investigations"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-investigation-mcaz-supervisor',
 'MCAZ_SUPERVISOR: manage investigations.',
 NULL, 'MCAZ_SUPERVISOR', NULL, 'PATCH', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/investigations"}', true),
-- ── MCAZ / facility-focal reads (workbench, queues, cases, reports, config) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-read-mcaz-reviewer',
 'MCAZ_REVIEWER: read workbench/queues/cases/reports/config.',
 NULL, 'MCAZ_REVIEWER', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-read-mcaz-supervisor',
 'MCAZ_SUPERVISOR: read workbench/queues/cases/reports/config.',
 NULL, 'MCAZ_SUPERVISOR', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-read-facility-safety-focal',
 'FACILITY_SAFETY_FOCAL: read facility cases/reports/config (facility-scoped in-service).',
 NULL, 'FACILITY_SAFETY_FOCAL', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-read-district-pho',
 'DISTRICT_PHO: read district cases/reports.',
 NULL, 'DISTRICT_PHO', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'ps-config-read-sysadmin',
 'SYSTEM_ADMIN: read adapter/config status.',
 NULL, 'SYSTEM_ADMIN', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/patient-safety/config"}', true);
