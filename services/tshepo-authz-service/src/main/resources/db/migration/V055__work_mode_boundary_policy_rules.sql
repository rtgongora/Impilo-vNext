-- =====================================================================================
-- TSHEPO-authz :: V055 — WorkMode boundary rules (Phase B, SHADOW).
--
-- The negative lock that makes "management/support/regulatory mode grants no patient
-- access" a live policy statement, not only the role-narrowing PolicyEngine.bindWorkContext
-- already does in this same wave. DENY wins over any ALLOW, so these are the backstop for
-- rules this migration cannot enumerate (present or future) that might otherwise permit a
-- clinical read to a non-clinical duty mode.
--
-- Seeded active=false — pure SHADOW, zero runtime effect until a later migration flips
-- them, following the same staged pattern as V045/V046/V047 (ROM cross-council isolation):
-- author the rule as data now, observe via WORK_CONTEXT_MODE_NARROWED /
-- WORK_CONTEXT_MISMATCH governance signals during the SHADOW soak, flip once evidence is
-- clean. See the Work Context Resolution programme plan, Phase D (D7 staged cutover).
--
-- Deliberately NOT included here: the positive companion lock
-- ("clinical ALLOW rules require requires_identified_clinical_access") from the original
-- design sketch. That requires auditing and UPDATE-ing each currently-live clinical ALLOW
-- rule's conditions individually — a live-rule mutation, not a purely additive DENY row —
-- and is safer done as its own reviewed migration during the Phase D cutover, one rule at
-- a time, than bundled into this one sight-unseen.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- A management-mode session (department/facility/jurisdiction/programme management) must
-- never read a patient record merely because the duty role includes a clinical template.
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-management-no-patient-record',
 'Management-mode duty sessions (DEPARTMENT_MANAGEMENT/FACILITY_MANAGEMENT/'
 || 'JURISDICTION_OPERATIONS/PROGRAMME_MANAGEMENT) may not read /patients/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/patients/", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT"]}',
 false),

-- Technical/facility support must never read clinical content — platform/config/device
-- signals only. Companion rows below cover the clinical resource families the CLINICAL_
-- RESOURCE_TYPES set already recognises.
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-support-no-clinical-read',
 'Support-mode duty sessions (TECHNICAL_SUPPORT/FACILITY_SUPPORT) may not read /clinical/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/clinical/", "allowed_modes": ["TECHNICAL_SUPPORT","FACILITY_SUPPORT"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-support-no-encounter-read',
 'Support-mode duty sessions may not read /encounters/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/encounters/", "allowed_modes": ["TECHNICAL_SUPPORT","FACILITY_SUPPORT"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-support-no-prescription-read',
 'Support-mode duty sessions may not read /prescriptions/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/prescriptions/", "allowed_modes": ["TECHNICAL_SUPPORT","FACILITY_SUPPORT"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-support-no-observation-read',
 'Support-mode duty sessions may not read /observations/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/observations/", "allowed_modes": ["TECHNICAL_SUPPORT","FACILITY_SUPPORT"]}',
 false),

-- A regulator (REGULATORY_OPERATIONS / INSPECTION_COMPLIANCE) reviews and decides — they
-- must never become the facility operator. Complements the V045 regulatory-org-isolation
-- rows (which gate cross-council access), not a duplicate of them.
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-regulator-no-facility-operation',
 'Regulatory-mode duty sessions may not call /facility-mode/** mutation/config surfaces (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/facility-mode/", "allowed_modes": ["REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-regulator-no-queue-operation',
 'Regulatory-mode duty sessions may not call /queue/** operational surfaces (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/queue/", "allowed_modes": ["REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-regulator-no-service-point-operation',
 'Regulatory-mode duty sessions may not call /service-points/** operational surfaces (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/service-points/", "allowed_modes": ["REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE"]}',
 false),
('00000000-0000-0000-0000-000000000001'::uuid, 'work-mode-regulator-no-workspace-operation',
 'Regulatory-mode duty sessions may not call /workspaces/** operational surfaces (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 55,
 '{"path_contains": "/workspaces/", "allowed_modes": ["REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE"]}',
 false)

ON CONFLICT DO NOTHING;
