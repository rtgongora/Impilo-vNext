-- Registry intake orchestration (Experience BFF) — allow governed mutations under OPERATIONS purpose.
-- Tenant UUID matches other sample seeds (documentation / dev baseline).

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-system-admin',
    'SYSTEM_ADMIN: registry intake orchestration (sessions, import, recovery).',
    NULL, 'SYSTEM_ADMIN', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 40, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-hie-admin',
    'HIE_ADMIN: registry intake orchestration.',
    NULL, 'HIE_ADMIN', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 41, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-registry-admin',
    'REGISTRY_ADMIN: registry intake orchestration.',
    NULL, 'REGISTRY_ADMIN', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 42, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-import-admin',
    'IMPORT_ADMIN: bulk import execution and preview.',
    NULL, 'IMPORT_ADMIN', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 43, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-identity-steward',
    'IDENTITY_STEWARD: intake and recovery orchestration.',
    NULL, 'IDENTITY_STEWARD', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 44, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'registry-intake-developer',
    'DEVELOPER: governed dev access to registry intake APIs.',
    NULL, 'DEVELOPER', 'registry-intake', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 99, NULL, true
);
