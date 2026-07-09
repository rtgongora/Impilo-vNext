-- ============================================================================
-- V032 — Workforce Intake journey policy rules
-- Fine-grained ext_authz ALLOW rules for the staged MoHCC staff bootstrap
-- journey served by experience-bff at /internal/v1/workforce-intake/** and
-- persisted in workforce-governance-service. Before this, these paths had no
-- matching policy rule → PDP fail-closed (NO_MATCHING_RULES → DENY).
--
-- SYSTEM_ADMIN / FACILITY_ADMIN / HIE_ADMIN: full access to the intake
-- pipeline (upload, column mapping, match preview, approve, execute, status).
-- NULL actor_type/resource_type/action/purpose are wildcards; each rule is
-- pinned with `path_contains` "/workforce-intake" so it cannot leak onto
-- colliding resource types from other services. Per-step management-workspace
-- scoping is additionally prechecked in experience-bff
-- (AdminGovernancePolicyService) before any upstream call.
-- DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── System administrators: full access to /workforce-intake/** ──
('00000000-0000-0000-0000-000000000001'::uuid, 'workforce-intake-system-admin-full',
 'SYSTEM_ADMIN: full access to the staged workforce intake journey (upload, mapping, match, approve, execute, tracker).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/workforce-intake"}', true),

-- ── Facility administrators: full access to /workforce-intake/** ──
('00000000-0000-0000-0000-000000000001'::uuid, 'workforce-intake-facility-admin-full',
 'FACILITY_ADMIN: full access to the staged workforce intake journey for facility staff bootstrap.',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/workforce-intake"}', true),

-- ── HIE administrators: full access to /workforce-intake/** ──
('00000000-0000-0000-0000-000000000001'::uuid, 'workforce-intake-hie-admin-full',
 'HIE_ADMIN: full access to the staged workforce intake journey (national interoperability administration).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/workforce-intake"}', true);
