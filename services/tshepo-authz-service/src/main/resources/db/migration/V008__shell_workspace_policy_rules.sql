-- Experience BFF shell workspace (pins / recents in Redis).
-- Synthetic ext_authz uses :path /internal/v1/shell/workspace-state
-- PolicyEngine derives resource_type from the LAST non-UUID path segment → workspace-state
-- (see AuthzInternalRequest.deriveResourceType).

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-system-admin',
    'SYSTEM_ADMIN: Experience shell workspace persistence (GET/PUT/DELETE pins & recents).',
    NULL, 'SYSTEM_ADMIN', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 50, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-hie-admin',
    'HIE_ADMIN: shell workspace persistence.',
    NULL, 'HIE_ADMIN', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 51, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-admin',
    'ADMIN: shell workspace persistence.',
    NULL, 'ADMIN', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 52, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-clinician',
    'CLINICIAN: shell workspace persistence.',
    NULL, 'CLINICIAN', 'workspace-state', NULL, 'TREATMENT',
    false, false, 'ALLOW', 53, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-citizen',
    'CITIZEN: shell workspace persistence (My Life / citizen flows).',
    NULL, 'CITIZEN', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 54, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-finance',
    'FINANCE: shell workspace persistence.',
    NULL, 'FINANCE', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 55, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-commerce',
    'COMMERCE: shell workspace persistence.',
    NULL, 'COMMERCE', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 56, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-developer',
    'DEVELOPER: shell workspace in lab / non-prod.',
    NULL, 'DEVELOPER', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 99, NULL, true
);
