-- ============================================================================
-- V022 — OROS Orders / Diagnostics / Results policy rules (SYS-1 enforcement)
-- Enforces the OROS §21 policy intent at the ext_authz gateway. The oros-service
-- /v1/** routes are Envoy-direct and ext_authz-gated; before this they had no
-- matching policy_rule -> deny-by-default for user purposes (the diagnostics flow
-- was never live through the gateway). These ALLOW rules are STRICTLY ADDITIVE
-- (they only enable; uncovered routes remain deny-by-default; no regression).
--
-- Internal callbacks (/v1/orders/blood, /butano/writeback, /pct/hook, /v1/internal,
-- /v1/integrations, /v1/fhir) run under purpose SYSTEM and are covered by the
-- PolicyEngine SYSTEM-purpose bypass — NOT seeded here.
--
-- MECHANICS: resource_type/action/purpose = NULL wildcards (verified), pinned by
-- `path_contains` to the oros service path (collision-safe). purpose=NULL (RBAC now;
-- purpose tightening later). facility_scope=false. Subject binding (patient sees own
-- results; QR-claim identity confirm) is the IN-SERVICE follow-up.
--
-- Roles (the diagnostic journey): requesters + reporters = clinical cadres; fulfillers
-- = imaging/lab cadres; patient self-service = CITIZEN; directory/routing/catalogue
-- config + reconciliation dashboards = ADMIN/FACILITY_ADMIN/supervisors.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Diagnostic workforce: orders tree (create/draft/submit/route/schedule/arrive/accept/exam/specimen) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-clinician',   'CLINICIAN: request/manage diagnostic orders.',   NULL, 'CLINICIAN',   NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-doctor',      'DOCTOR: request/manage diagnostic orders.',      NULL, 'DOCTOR',      NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-nurse',       'NURSE: request/manage diagnostic orders.',       NULL, 'NURSE',       NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-midwife',     'MIDWIFE: request diagnostic orders.',            NULL, 'MIDWIFE',     NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-specialist',  'SPECIALIST: request/manage diagnostic orders.',  NULL, 'SPECIALIST',  NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-radiographer','RADIOGRAPHER: fulfil imaging orders (accept/schedule/exam).', NULL, 'RADIOGRAPHER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-sonographer', 'SONOGRAPHER: fulfil ultrasound orders.',         NULL, 'SONOGRAPHER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-lab-tech',    'LAB_TECH: fulfil lab orders (accept/specimen workflow).', NULL, 'LAB_TECH', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-orders-dispenser',   'DISPENSER: fulfil pharmacy-linked orders.',      NULL, 'DISPENSER',   NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/orders"}', true),
-- ── Diagnostic workforce: results tree (report/validate/release/amend/critical/ack/view) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-clinician',  'CLINICIAN: view/ack diagnostic results.',        NULL, 'CLINICIAN',   NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-doctor',     'DOCTOR: report/release/view diagnostic results.',NULL, 'DOCTOR',      NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-nurse',      'NURSE: view/ack diagnostic results.',            NULL, 'NURSE',       NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-specialist', 'SPECIALIST: report/validate/release results.',   NULL, 'SPECIALIST',  NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-radiographer','RADIOGRAPHER: author imaging report.',          NULL, 'RADIOGRAPHER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-sonographer','SONOGRAPHER: author ultrasound report.',         NULL, 'SONOGRAPHER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-lab-tech',   'LAB_TECH: enter/validate/release lab results.',  NULL, 'LAB_TECH',    NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
-- ── Patient self-service: view own results + claim QR/secure-code order (subject-bound in-service) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-results-citizen',    'CITIZEN: view own results (subject-bound in-service).', NULL, 'CITIZEN', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/results"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-qr-claim-citizen',   'CITIZEN: claim a QR/secure-code order at a facility.', NULL, 'CITIZEN', NULL, 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/intake/qr"}', true),
-- ── Directory / routing / catalogue / config + reconciliation dashboards ──
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-admin-config-admin', 'ADMIN: routing rules, critical-escalation, catalogue, specimen config.', NULL, 'ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/admin"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-routing-admin',      'ADMIN: manage diagnostic routing/directory.',    NULL, 'ADMIN',       NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/routing"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-catalogue-admin',    'ADMIN: manage service/orderable catalogue.',     NULL, 'ADMIN',       NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/catalog"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-admin-config-facadmin','FACILITY_ADMIN: facility-scoped routing/catalogue/config.', NULL, 'FACILITY_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/admin"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-routing-facadmin',   'FACILITY_ADMIN: facility routing/directory.',    NULL, 'FACILITY_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/routing"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-reconcile-facadmin', 'FACILITY_ADMIN: reconciliation dashboards.',     NULL, 'FACILITY_ADMIN', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/reconcile"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-reconcile-district', 'DISTRICT_PHO: reconciliation/turnaround dashboards.', NULL, 'DISTRICT_PHO', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/reconcile"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'oros-reconcile-pho',      'PUBLIC_HEALTH_OFFICER: above-site reconciliation dashboards.', NULL, 'PUBLIC_HEALTH_OFFICER', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/v1/reconcile"}', true);
