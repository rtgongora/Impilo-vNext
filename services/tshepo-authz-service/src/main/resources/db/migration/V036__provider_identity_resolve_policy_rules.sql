-- ============================================================================
-- V036 — Provider+Place identity program W1: private-resolve lane pins
--
-- The varapi private provider-identity resolver (POST /v1/registry/resolve,
-- D-P2) and the vito registry resolver (/v1/registry/*) are INTERNAL-only
-- service-to-service surfaces: they are enforced in-service (AccessMode
-- INTERNAL) and are not routed by the Envoy gateway. These explicit DENY pins
-- make that boundary visible and audited at the PDP as defence-in-depth: any
-- gateway-originated request that ever reaches a /v1/registry/resolve family
-- path (route misconfiguration, future route additions) is denied for every
-- actor, before fail-closed NO_MATCHING_RULES would even apply.
--
-- DENY-wins + first-matching-ALLOW unchanged; priority 10 beats the ALLOW
-- seeds (50) so no later ALLOW family can accidentally open this lane.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'registry-resolve-internal-only',
 'DENY all gateway-originated access to the private registry resolvers (varapi/vito /v1/registry/resolve family) — INTERNAL service-to-service only (anti-enumeration boundary, Identity Journey Doctrine §2 / D-P2).',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'DENY', 10, '{"path_contains": "/v1/registry/resolve"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'registry-claim-internal-only',
 'DENY all gateway-originated access to the private record-claim lane (vito /v1/registry/claim) — second-factor claim challenges are BFF-orchestrated, never direct.',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'DENY', 10, '{"path_contains": "/v1/registry/claim"}', true);
