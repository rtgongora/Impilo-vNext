-- ============================================================================
-- V040 — Provider+Place identity program W8: place governance journey pins
--
-- Facility governance cases (FJ6 high-risk update, FJ7 transfer, FJ9 duplicate
-- report) and site operator grants / inspection lanes. Opening a case is an
-- operator/citizen action; DECIDING is steward-gated in-service. One path-
-- pinned ALLOW per family keeps the lane reachable while the real control
-- (in-service steward gates + append-only finding trigger) stays where it can
-- see the actor and the record. DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'facility-governance-lane',
 'Facility governance journeys — high-risk update / transfer / duplicate-report cases (tuso /governance; FJ6/FJ7/FJ9). Opening is operator/citizen; decisions are steward-gated in-service.',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "/governance"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'site-operator-grants-lane',
 'Site operator grants — person role-scoped site management (indawo /site-operator-grants + /operator-grants; SJ1). Approvals steward-gated in-service.',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "operator-grants"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'site-registrations-lane',
 'Site registrations (indawo /site-registrations; SJ2). Submission generic; steward queue gated in-service.',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "/site-registrations"}', true);
