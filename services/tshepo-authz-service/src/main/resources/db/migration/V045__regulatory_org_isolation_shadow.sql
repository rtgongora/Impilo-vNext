-- =====================================================================================
-- TSHEPO-authz :: V045 — Regulatory cross-council isolation (ROM-W2, SHADOW)
-- The isolation contract for regulatory work sessions (doctrine §4): a regulatory actor may act
-- only within the organisation their WORK_CONTEXT token is anchored to (org_id claim), and a
-- committee member sees only docketed cases (W8). These rows document the contract as data and
-- are seeded INACTIVE (active=false) — pure SHADOW, zero runtime effect. The org_id-vs-target
-- comparison is enforced by the impilo.authz rego, wired + flipped to active at ROM-W11 ENFORCE,
-- sequenced strictly AFTER the identity program's WORK_CONTEXT flip and through the CZO channel.
-- No PolicyEngine / tshepo-service edit. Golden tenant 00000000-0000-0000-0000-000000000001.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- A regulatory actor MAY act on their own organisation's regulatory surfaces (org match required).
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-regulatory-own-org-allow',
 'Regulatory personnel may act on /regulatory/** only within their token org (SHADOW; org match by rego).',
 NULL, NULL, NULL, NULL, 'REGULATORY_DUTY', false, false, 'ALLOW', 60,
 '{"path_contains": "/regulatory/", "require_token_org_match": true}', false),
-- Cross-council reads/writes are denied when the token org does not match the target org.
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-cross-council-deny',
 'DENY-wins for /regulatory/** when the token org does not match the target org (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 40,
 '{"path_contains": "/regulatory/", "deny_when_org_mismatch": true}', false),
-- A committee member sees only cases docketed to their committee (elaborated at W8/V046).
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-committee-docket-only',
 'COMMITTEE_MEMBER may read only docketed cases (SHADOW; docket match by rego, W8).',
 NULL, 'COMMITTEE_MEMBER', NULL, 'GET', 'REGULATORY_DUTY', false, false, 'ALLOW', 60,
 '{"path_contains": "/regulatory/", "require_docket_assignment": true}', false)
ON CONFLICT DO NOTHING;
