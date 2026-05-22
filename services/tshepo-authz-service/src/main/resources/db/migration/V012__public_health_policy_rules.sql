-- Public Health Operations synthetic ext_authz paths (Experience BFF PublicHealthGovernanceService).
-- :path /internal/v1/public-health-governed-read|mutate|export → resource_type = last segment.

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-read-officer',
    'PUBLIC_HEALTH_OFFICER: governed public-health reads.',
    NULL, 'PUBLIC_HEALTH_OFFICER', 'public-health-governed-read', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 70, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-read-env',
    'ENV_HEALTH: governed public-health reads.',
    NULL, 'ENV_HEALTH', 'public-health-governed-read', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 71, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-read-chw',
    'CHW: governed public-health reads.',
    NULL, 'CHW', 'public-health-governed-read', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 72, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-mutate-officer',
    'PUBLIC_HEALTH_OFFICER: governed public-health mutations.',
    NULL, 'PUBLIC_HEALTH_OFFICER', 'public-health-governed-mutate', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 73, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-mutate-env',
    'ENV_HEALTH: governed public-health mutations.',
    NULL, 'ENV_HEALTH', 'public-health-governed-mutate', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 74, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-export-officer',
    'PUBLIC_HEALTH_OFFICER: governed public-health exports.',
    NULL, 'PUBLIC_HEALTH_OFFICER', 'public-health-governed-export', NULL, 'PUBLIC_HEALTH',
    false, false, 'ALLOW', 75, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ph-governed-admin',
    'SYSTEM_ADMIN: full governed public-health access.',
    NULL, 'SYSTEM_ADMIN', 'public-health-governed-mutate', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 50, NULL, true
);
