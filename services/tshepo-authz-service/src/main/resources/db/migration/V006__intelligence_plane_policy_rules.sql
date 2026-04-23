-- Health Intelligence plane (Experience BFF) — synthetic ext_authz path /internal/v1/intelligence-plane → resource_type intelligence-plane.

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'intelligence-plane-system-admin',
    'SYSTEM_ADMIN: cross-service intelligence queries.',
    NULL, 'SYSTEM_ADMIN', 'intelligence-plane', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 50, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'intelligence-plane-hie-admin',
    'HIE_ADMIN: intelligence plane queries.',
    NULL, 'HIE_ADMIN', 'intelligence-plane', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 51, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'intelligence-plane-clinician',
    'CLINICIAN: supervised intelligence retrieval.',
    NULL, 'CLINICIAN', 'intelligence-plane', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 52, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'intelligence-plane-developer',
    'DEVELOPER: intelligence plane in non-prod / lab.',
    NULL, 'DEVELOPER', 'intelligence-plane', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 99, NULL, true
);
