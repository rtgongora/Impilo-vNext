-- ============================================================================
-- V043 — WORK_CONTEXT dimensions become first-class + role-template catalog.
--
-- Part A: role_template_catalog — maps an opaque Vashandi roleTemplateId to one or
--   more canonical policy roles, so policy rules can be authored against readable
--   canonical roles while the duty token carries the raw template id. Resolved at
--   decision time by the PDP (RoleTemplateCatalog), folded additively into the
--   effective role set alongside the raw template id.
--
-- Part B: demonstrative dimension rules on an ISOLATED demo path
--   (/internal/v1/authz-dim-demo/*) that no live service serves, so they prove the
--   new evaluateConditions dimensions (workflow-state / department / provider-id)
--   via a direct PDP decision call WITHOUT affecting any real traffic. All ALLOW,
--   additive, fail-closed by construction.
-- ============================================================================

-- ── Part A: role-template → canonical-role catalog ──────────────────────────
CREATE TABLE IF NOT EXISTS tshepo_authz.role_template_catalog (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    role_template_id VARCHAR(128) NOT NULL,
    canonical_role   VARCHAR(128) NOT NULL,
    description      TEXT,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_template_catalog UNIQUE (tenant_id, role_template_id, canonical_role)
);

CREATE INDEX IF NOT EXISTS ix_role_template_catalog_lookup
    ON tshepo_authz.role_template_catalog (tenant_id, role_template_id) WHERE active;

-- Representative mappings (extensible — one row per (template, canonical role)).
INSERT INTO tshepo_authz.role_template_catalog
    (tenant_id, role_template_id, canonical_role, description)
VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'NURSE_GENERAL',       'CLINICIAN',  'General nurse duty template → clinician policy role.'),
('00000000-0000-0000-0000-000000000001'::uuid, 'NURSE_ONCOLOGY_SNR',  'CLINICIAN',  'Senior oncology nurse duty template → clinician policy role.'),
('00000000-0000-0000-0000-000000000001'::uuid, 'MEDICAL_OFFICER',     'CLINICIAN',  'Medical officer duty template → clinician policy role.'),
('00000000-0000-0000-0000-000000000001'::uuid, 'WARD_CHARGE_NURSE',   'CLINICIAN',  'Ward charge nurse duty template → clinician policy role.'),
('00000000-0000-0000-0000-000000000001'::uuid, 'PHARMACIST_DISPENSE', 'PHARMACIST', 'Dispensing pharmacist duty template → pharmacist policy role.')
ON CONFLICT ON CONSTRAINT uq_role_template_catalog DO NOTHING;

-- ── Part B: demonstrative first-class-dimension rules (isolated demo path) ───
INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- workflow-state: amend allowed EXCEPT on a DISCHARGED transaction.
('00000000-0000-0000-0000-000000000001'::uuid, 'authz-dim-demo-workflow-state',
 'DEMO: a CLINICIAN may amend a note unless the workflow-state is DISCHARGED (blocked_workflow_states).',
 NULL, 'CLINICIAN', NULL, NULL, NULL,
 false, false, 'ALLOW', 40,
 '{"path_contains": "/internal/v1/authz-dim-demo/note", "blocked_workflow_states": ["DISCHARGED"]}', true),

-- attached role-id: a provider public id must be present for a prescribe action.
('00000000-0000-0000-0000-000000000001'::uuid, 'authz-dim-demo-requires-provider',
 'DEMO: a CLINICIAN prescribe action requires an attached provider id (requires_provider_id).',
 NULL, 'CLINICIAN', NULL, NULL, NULL,
 false, false, 'ALLOW', 40,
 '{"path_contains": "/internal/v1/authz-dim-demo/prescribe", "requires_provider_id": true}', true),

-- department: gated to the cardiology department (duty-token-authoritative).
('00000000-0000-0000-0000-000000000001'::uuid, 'authz-dim-demo-department',
 'DEMO: a CLINICIAN action gated to the cardiology department (allowed_departments).',
 NULL, 'CLINICIAN', NULL, NULL, NULL,
 false, false, 'ALLOW', 40,
 '{"path_contains": "/internal/v1/authz-dim-demo/dept", "allowed_departments": ["cardiology"]}', true);
