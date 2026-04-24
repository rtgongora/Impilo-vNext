-- Additional Keycloak realm roles for Experience shell workspace (V008 baseline).
-- Synthetic path remains /internal/v1/shell/workspace-state → resource_type workspace-state.

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-nurse',
    'NURSE: shell workspace persistence.',
    NULL, 'NURSE', 'workspace-state', NULL, 'TREATMENT',
    false, false, 'ALLOW', 57, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-pharmacist',
    'PHARMACIST: shell workspace persistence.',
    NULL, 'PHARMACIST', 'workspace-state', NULL, 'TREATMENT',
    false, false, 'ALLOW', 58, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-support-agent',
    'SUPPORT_AGENT: shell workspace persistence (queue / front desk).',
    NULL, 'SUPPORT_AGENT', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 59, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-facility-admin',
    'FACILITY_ADMIN: shell workspace persistence.',
    NULL, 'FACILITY_ADMIN', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 60, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-public-health',
    'PUBLIC_HEALTH_OFFICER: shell workspace persistence.',
    NULL, 'PUBLIC_HEALTH_OFFICER', 'workspace-state', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 61, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-env-health',
    'ENV_HEALTH: shell workspace persistence.',
    NULL, 'ENV_HEALTH', 'workspace-state', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 62, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-chw',
    'CHW: shell workspace persistence.',
    NULL, 'CHW', 'workspace-state', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 63, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-caregiver',
    'CAREGIVER: shell workspace persistence.',
    NULL, 'CAREGIVER', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 64, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'shell-workspace-state-care-partner',
    'CARE_PARTNER: shell workspace persistence.',
    NULL, 'CARE_PARTNER', 'workspace-state', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 65, NULL, true
);
