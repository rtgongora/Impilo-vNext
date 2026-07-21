-- =====================================================================================
-- TSHEPO-authz :: V044 — Rito Experience & Reputation read authorization (RW10)
-- Governs the AUTHORISED management view of provider/facility reputation. The public
-- verified-experience read (/v1/public/rito/reputation/**) is on the header-stripped
-- public lane (BFF) and needs no rule. The management view (/internal/v1/rito/reputation/
-- **/management) exposes ALL four domains + moderation counts — doctrine restricts it to
-- authorised providers, facility leadership, regulators and quality teams
-- (provider-reputation-doctrine.md §2.1). Facility managers + district PHOs are already
-- covered by the broad `/rito/` grants in V021; this adds regulators and quality focals.
-- Additive ALLOW seeds (no row-level shadow exists here — a seed is live at the
-- fail-closed PDP when active=true). No PolicyEngine / frozen-service change.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- Regulators: read provider/facility reputation for quality/oversight programmes.
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-reputation-read-regulator',
 'REGULATOR: read the authorised reputation management view (all domains + moderation).',
 NULL, 'REGULATOR', NULL, 'GET', NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/rito/reputation"}', true),
-- Quality/safety focal: read reputation for facility quality improvement.
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-reputation-read-quality-focal',
 'FACILITY_SAFETY_FOCAL (quality team): read the reputation management view.',
 NULL, 'FACILITY_SAFETY_FOCAL', NULL, 'GET', NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/rito/reputation"}', true),
-- System/scheduled recompute of the derived reputation summaries.
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-reputation-recompute-system',
 'SYSTEM: trigger recompute of derived reputation summaries.',
 'SYSTEM', NULL, NULL, 'POST', NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/rito/reputation"}', true)
ON CONFLICT DO NOTHING;
