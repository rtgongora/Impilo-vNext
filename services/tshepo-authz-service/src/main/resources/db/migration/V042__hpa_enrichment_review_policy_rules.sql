-- ============================================================================
-- V042 — HPA national facility enrichment: review-surface policy pins.
--
-- ALLOW rules for the HPA enrichment admin surfaces:
--   * tuso HPA import runs + reconciliation review queues (/hpa-import)
--   * facility data-gap + verification tasks review (/facility-data-gaps)
-- Governance-administrator roles (SYSTEM_ADMIN + HIE_ADMIN — Trust Console
-- principals) plus FACILITY_ADMIN for the facility-side data-gap/verification
-- review of a facility they administer. The services add actor-type/steward
-- defence-in-depth; everything else stays fail-closed (NO_MATCHING_RULES → DENY).
--
-- NOTE: the HPA IMPORT itself grants NO authority to anyone. A practitioner-in-
-- charge import is a candidate PENDING relationship only — these rules gate the
-- human REVIEW of imported candidates, never any operational access.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'hpa-import-system-admin',
 'SYSTEM_ADMIN: run/read HPA enrichment imports and reconciliation review queues (tuso /hpa-import).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/hpa-import"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'hpa-import-hie-admin',
 'HIE_ADMIN: run/read HPA enrichment imports and reconciliation review queues (tuso /hpa-import).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/hpa-import"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'facility-data-gaps-system-admin',
 'SYSTEM_ADMIN: review facility data-gap and verification tasks (tuso /facility-data-gaps).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/facility-data-gaps"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'facility-data-gaps-hie-admin',
 'HIE_ADMIN: review facility data-gap and verification tasks (tuso /facility-data-gaps).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/facility-data-gaps"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'facility-data-gaps-facility-admin',
 'FACILITY_ADMIN: resolve the data-gap and verification tasks for a facility they administer (facility-scoped).',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL,
 true, false, 'ALLOW', 50, '{"path_contains": "/facility-data-gaps"}', true);
