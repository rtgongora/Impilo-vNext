-- ============================================================================
-- V038 — Provider+Place identity program W2/W4: place-governance lane pins
--
-- ALLOW rules for the new place-governance steward surfaces:
--   * tuso facility trust dimensions (/trust-dimensions view + recompute)
--   * indawo cross-registry place links (/place-links create/end/read)
--   * ndila place anchors + site sync (/ndila/places)
-- Governance-administrator roles only (SYSTEM_ADMIN + HIE_ADMIN — the Trust
-- Console principals, V032 pattern); the services add their own actor-type
-- defence-in-depth (steward gates). Everything else on these paths stays
-- fail-closed (NO_MATCHING_RULES → DENY).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'trust-dimensions-system-admin',
 'SYSTEM_ADMIN: view and recompute facility trust dimensions (tuso /trust-dimensions — governance read-model, never public).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/trust-dimensions"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'trust-dimensions-hie-admin',
 'HIE_ADMIN: view and recompute facility trust dimensions (tuso /trust-dimensions).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/trust-dimensions"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'place-links-system-admin',
 'SYSTEM_ADMIN: create/end/read typed cross-registry place links (indawo /place-links — single-writer steward surface).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/place-links"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'place-links-hie-admin',
 'HIE_ADMIN: create/end/read typed cross-registry place links (indawo /place-links).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/place-links"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'place-anchors-system-admin',
 'SYSTEM_ADMIN: place anchors, campus view and Indawo site sync (ndila /ndila/places).',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/ndila/places"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'place-anchors-hie-admin',
 'HIE_ADMIN: place anchors, campus view and Indawo site sync (ndila /ndila/places).',
 NULL, 'HIE_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/ndila/places"}', true);
