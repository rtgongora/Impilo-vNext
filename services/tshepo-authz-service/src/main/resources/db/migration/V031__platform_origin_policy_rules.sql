-- ============================================================================
-- V031 — Platform Origin governance policy rules (IATG Wave-1, WS-A)
-- Fine-grained ext_authz ALLOW rules for the workforce-governance-service
-- Platform Origin API (/internal/v1/platform-origin/**). Before this, these
-- endpoints had no matching policy rule → PDP fail-closed
-- (NO_MATCHING_RULES → DENY).
--
-- PLATFORM_ORIGIN_ADMINISTRATOR: full access to the platform-origin family
-- (country operation creation, two-person approvals, execution, national
-- appointments and revocations). NULL actor_type/resource_type/action/purpose
-- are wildcards; the rule is pinned with `path_contains` "/platform-origin"
-- so it cannot leak onto colliding resource types from other services.
--
-- NATIONAL_ADMINISTRATOR: read-only — country operation reads and own-country
-- appointment reads (GET only, pinned to /platform-origin/country-operations).
-- Own-country scoping of appointment data is enforced by the governance
-- service; the two-person mutation rule itself is enforced in
-- workforce-governance-service (TwoPersonApprovalService).
-- DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Platform Origin administrators: full access to /platform-origin/** ──
('00000000-0000-0000-0000-000000000001'::uuid, 'platform-origin-admin-full',
 'PLATFORM_ORIGIN_ADMINISTRATOR: full access to the Platform Origin governance API (country operations, two-person approvals, execution, national appointments).',
 NULL, 'PLATFORM_ORIGIN_ADMINISTRATOR', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/platform-origin"}', true),

-- ── National administrators: read country operations + own-country appointment reads ──
('00000000-0000-0000-0000-000000000001'::uuid, 'platform-origin-national-admin-read',
 'NATIONAL_ADMINISTRATOR: read country operations and own-country national appointments (own-country scoping enforced by workforce-governance-service).',
 NULL, 'NATIONAL_ADMINISTRATOR', NULL, 'GET', NULL,
 false, false, 'ALLOW', 55, '{"path_contains": "/platform-origin/country-operations"}', true);
