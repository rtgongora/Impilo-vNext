-- =====================================================================================
-- TSHEPO-authz :: V047 — HPA oversight policies (ROM-W10, SHADOW)
-- HPA oversight = aggregate reads + granted-case reads only. Council OPERATIONAL workspace access
-- is absent BY CONSTRUCTION — there is simply no rule granting HPA_OVERSIGHT_OFFICER a council's
-- operational lanes, and fail-closed does the rest. Seeded INACTIVE (SHADOW); the granted-case
-- match (oversight_escalation_grant) is enforced by rego, flipped at W11 ENFORCE via CZO. No
-- PolicyEngine / tshepo-service edit.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- HPA may read the consolidated cross-council indicators (aggregate only).
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-hpa-oversight-aggregates',
 'HPA_OVERSIGHT_OFFICER may read consolidated aggregate indicators (SHADOW).',
 NULL, 'HPA_OVERSIGHT_OFFICER', NULL, 'GET', 'REGULATORY_DUTY', false, false, 'ALLOW', 60,
 '{"path_contains": "/regulatory/oversight/indicators"}', false),
-- HPA may read a council case ONLY when an active escalation grant exists.
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-hpa-oversight-granted-case',
 'HPA_OVERSIGHT_OFFICER may read a council case only with an active escalation grant (SHADOW; grant match by rego).',
 NULL, 'HPA_OVERSIGHT_OFFICER', NULL, 'GET', 'REGULATORY_DUTY', false, false, 'ALLOW', 60,
 '{"path_contains": "/regulatory/oversight/cases", "require_escalation_grant": true}', false),
-- HPA is denied council operational lanes (belt-and-braces; absence already fails closed).
('00000000-0000-0000-0000-000000000001'::uuid, 'rom-hpa-no-operational-access',
 'HPA_OVERSIGHT_OFFICER is denied council operational workspaces (SHADOW).',
 NULL, 'HPA_OVERSIGHT_OFFICER', NULL, NULL, NULL, false, false, 'DENY', 40,
 '{"path_contains": "/regulatory/", "deny_operational_lanes": true}', false)
ON CONFLICT DO NOTHING;
