-- ============================================================================
-- V017 — Khuluma Comms Hub policy rules
-- Governed comms decisions evaluated by the Tshepo PDP (mirrors infra/opa/impilo/khuluma.rego).
-- Resource types: khuluma-conversation (converse), khuluma-call (call), khuluma-patient-link
-- (bind a conversation to a patient — regulated clinical action).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- Citizens may use core conversation/call comms in their own person context.
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-comms-citizen',
    'CITIZEN: native conversations + calls in own person context.',
    'CITIZEN', NULL, 'khuluma-conversation', NULL, 'PERSONAL_CARE',
    false, false, 'ALLOW', 70, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-call-citizen',
    'CITIZEN: native 1:1 calls in own person context.',
    'CITIZEN', NULL, 'khuluma-call', NULL, 'PERSONAL_CARE',
    false, false, 'ALLOW', 71, NULL, true
),
-- Providers with an active organisation assignment may use comms (facility-scoped).
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-comms-provider',
    'PROVIDER: native conversations within facility/organisation context.',
    'PROVIDER', NULL, 'khuluma-conversation', NULL, 'CARE_COORDINATION',
    true, false, 'ALLOW', 60, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-call-provider',
    'PROVIDER: native audio/video calls within facility/organisation context.',
    'PROVIDER', NULL, 'khuluma-call', NULL, 'CARE_COORDINATION',
    true, false, 'ALLOW', 61, NULL, true
),
-- Binding a conversation to a patient is a regulated clinical action — clinical roles only.
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-patient-link-clinician',
    'CLINICIAN: bind a conversation to a patient record.',
    'PROVIDER', 'CLINICIAN', 'khuluma-patient-link', NULL, 'CARE_COORDINATION',
    true, false, 'ALLOW', 50, NULL, true
),
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-patient-link-nurse',
    'NURSE: bind a conversation to a patient record.',
    'PROVIDER', 'NURSE', 'khuluma-patient-link', NULL, 'CARE_COORDINATION',
    true, false, 'ALLOW', 51, NULL, true
);
