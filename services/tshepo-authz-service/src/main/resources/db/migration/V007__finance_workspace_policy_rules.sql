-- Experience BFF finance plane — synthetic ext_authz paths:
--   GET/POST /internal/v1/finance/billing-workspace  → resource_type billing-workspace
--   GET/POST /internal/v1/finance/mushex-platform     → resource_type mushex-platform

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-billing-workspace-system-admin',
    'SYSTEM_ADMIN: COSTA financial lifecycle workspace APIs.',
    NULL, 'SYSTEM_ADMIN', 'billing-workspace', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 50, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-billing-workspace-hie-admin',
    'HIE_ADMIN: COSTA financial lifecycle workspace.',
    NULL, 'HIE_ADMIN', 'billing-workspace', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 51, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-billing-workspace-finance',
    'FINANCE role: billing workspace and invoice lifecycle reads/writes.',
    NULL, 'FINANCE', 'billing-workspace', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 52, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-billing-workspace-developer',
    'DEVELOPER: finance workspace in lab.',
    NULL, 'DEVELOPER', 'billing-workspace', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 99, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-mushex-platform-system-admin',
    'SYSTEM_ADMIN: MusheX custodial wallet / remittance / card platform APIs.',
    NULL, 'SYSTEM_ADMIN', 'mushex-platform', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 50, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-mushex-platform-hie-admin',
    'HIE_ADMIN: MusheX financial platform.',
    NULL, 'HIE_ADMIN', 'mushex-platform', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 51, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-mushex-platform-finance',
    'FINANCE role: MusheX platform wallet and remittance operations.',
    NULL, 'FINANCE', 'mushex-platform', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 52, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'finance-mushex-platform-developer',
    'DEVELOPER: MusheX platform in lab.',
    NULL, 'DEVELOPER', 'mushex-platform', NULL, 'OPERATIONS',
    false, false, 'ALLOW', 99, NULL, true
);
