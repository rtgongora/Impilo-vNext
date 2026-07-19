-- ============================================================================
-- V037 — Provider+Place identity program W3: work-session lane pins
--
-- ALLOW rules for the provider work-session composition lane
-- (experience-bff /internal/v1/work-context + /session): reading one's own
-- Vashandi work-context read-model and starting/switching a work session are
-- provider-facing actions available to any authenticated person actor — the
-- REAL gate is inside the lane (assignment proof against Vashandi + linked
-- provider check + generic denial) and at the WORK-zone rules
-- (WORK_REQUIRES_ASSIGNMENT / WORK_TOKEN_CONTEXT_MISMATCH in impilo.authz).
-- Without a matching ALLOW the PDP would fail-closed on the whole family.
--
-- Path-pinned; DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'work-context-self-read',
 'Any authenticated actor may read their OWN work-context read-model (experience-bff /internal/v1/work-context; Vashandi anti-enumeration: unknown actor gets an empty context).',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "/work-context"}', true);
