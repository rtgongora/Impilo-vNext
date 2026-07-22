-- =====================================================================================
-- TSHEPO-authz :: V046 — Committee docket-scoped visibility (ROM-W8, SHADOW)
-- A COMMITTEE_MEMBER may read only cases docketed to them (doctrine §4.2). Seeded INACTIVE
-- (active=false) — pure SHADOW. The docket match (token member Health-ID ∈ case_docket_assignment)
-- is enforced by the impilo.authz rego, wired + flipped at ROM-W11 ENFORCE via the CZO channel.
-- The service layer (DisciplinaryProceedingService/HearingDocketService) enforces docket scoping
-- in-band meanwhile (defense in depth). No PolicyEngine / tshepo-service edit.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-committee-member-docket-read',
 'COMMITTEE_MEMBER may read a disciplinary case only when docketed to them (SHADOW; docket match by rego).',
 NULL, 'COMMITTEE_MEMBER', NULL, 'GET', 'REGULATORY_DUTY', false, false, 'ALLOW', 60,
 '{"path_contains": "/regulatory/hearings", "require_docket_assignment": true}', false),
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-committee-non-docket-deny',
 'DENY a COMMITTEE_MEMBER read of a case not docketed to them (SHADOW).',
 NULL, 'COMMITTEE_MEMBER', NULL, 'GET', NULL, false, false, 'DENY', 40,
 '{"path_contains": "/regulatory/hearings", "deny_when_not_docketed": true}', false)
ON CONFLICT DO NOTHING;
