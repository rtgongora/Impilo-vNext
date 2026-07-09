-- ============================================================================
-- V032 — IATG Trust Console governance policy rules
-- Fine-grained ext_authz ALLOW rules for the Trust Console review lanes:
--   * varapi provider-access-request review queue + decisions
--     (/v1/internal/providers/access-requests/review, .../{publicId}/decision)
--   * tuso cross-facility facility-admin appointment queue + approve
--     (/v1/internal/facility-admin-appointments)
--   * experience-bff Trust Console composition routes (/internal/v1/trust-console)
-- Before this, these paths had no matching policy rule → PDP fail-closed
-- (NO_MATCHING_RULES → DENY).
--
-- SYSTEM_ADMIN: national platform administration — full access to all three
-- families. HIE_ADMIN: HIE governance administrators (role being added to the
-- Keycloak realm in the parallel IATG workstream) — same access; the console
-- is their primary surface. NULL actor_type/resource_type/action/purpose are
-- wildcards; each rule is pinned with `path_contains` so it cannot leak onto
-- colliding resource types from other services.
-- DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Varapi provider-access-request review lane ──
('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-varapi-review-system-admin',
 'SYSTEM_ADMIN: review and decide provider access requests (Trust Console, varapi /providers/access-requests family).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/providers/access-requests"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-varapi-review-hie-admin',
 'HIE_ADMIN: review and decide provider access requests (Trust Console, varapi /providers/access-requests family).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/providers/access-requests"}', true),

-- ── Tuso cross-facility facility-admin appointment queue ──
('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-tuso-appointments-system-admin',
 'SYSTEM_ADMIN: list and approve facility administrator appointments across facilities (Trust Console, tuso /facility-admin-appointments).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/facility-admin-appointments"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-tuso-appointments-hie-admin',
 'HIE_ADMIN: list and approve facility administrator appointments across facilities (Trust Console, tuso /facility-admin-appointments).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/facility-admin-appointments"}', true),

-- ── Experience-BFF Trust Console composition routes ──
('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-bff-system-admin',
 'SYSTEM_ADMIN: Trust Console summary, queues and decision passthroughs (experience-bff /internal/v1/trust-console).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/trust-console"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'trust-console-bff-hie-admin',
 'HIE_ADMIN: Trust Console summary, queues and decision passthroughs (experience-bff /internal/v1/trust-console).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/trust-console"}', true);
