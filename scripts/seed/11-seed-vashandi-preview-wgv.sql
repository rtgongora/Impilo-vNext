-- =============================================================================
-- Vashandi preview personas — Workforce Governance assignments
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Role templates are carried in extension_json.roleTemplateId for Session Contract.
-- Idempotent via fixed UUIDs and WHERE NOT EXISTS
-- =============================================================================

-- Health Service Commission organisation (HSC workforce persona)
INSERT INTO wgv_organisation
    (id, tenant_id, organisation_code, name, legal_name, organisation_type, status, active_flag, created_at, updated_at)
SELECT
    'f2000000-0000-4000-8000-000000000020'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'MOHCC-HSC',
    'Health Service Commission of Zimbabwe',
    'Health Service Commission of Zimbabwe',
    'HEALTH_SERVICE_COMMISSION',
    'ACTIVE',
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_organisation
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND organisation_code = 'MOHCC-HSC'
);

-- Vashandi role definitions (role_code mirrors trust role template ids)
INSERT INTO wgv_role_definition
    (id, tenant_id, role_code, name, description, role_category, role_level,
     allowed_target_types, requires_provider_flag, requires_professional_standing_flag,
     special_governance_flag, active_flag, created_at, updated_at)
SELECT v.id::uuid, '00000000-0000-4000-8000-000000000001'::uuid, v.code, v.name, v.description,
       'ADMINISTRATIVE', v.level, v.targets, true, false, false, true, NOW(), NOW()
FROM (VALUES
    ('f4000000-0000-4000-8000-000000000101', 'vashandi_national_workforce_admin', 'Vashandi National Workforce Admin', 'National operational workforce administrator', 'NATIONAL_LEVEL', '["ORGANISATION"]'),
    ('f4000000-0000-4000-8000-000000000102', 'vashandi_facility_workforce_manager', 'Vashandi Facility Workforce Manager', 'Facility workforce manager for rosters and assignments', 'FACILITY_LEVEL', '["FACILITY","ORGANISATION"]'),
    ('f4000000-0000-4000-8000-000000000103', 'vashandi_self_service_worker', 'Vashandi Self-Service Worker', 'Ordinary worker with my roster and attendance only', 'FACILITY_LEVEL', '["ORGANISATION","FACILITY"]'),
    ('f4000000-0000-4000-8000-000000000104', 'vashandi_hsc_workforce_admin', 'Vashandi HSC Workforce Admin', 'Health Service Commission workforce administrator', 'NATIONAL_LEVEL', '["ORGANISATION"]'),
    ('f4000000-0000-4000-8000-000000000105', 'vashandi_access_reviewer', 'Vashandi Access Reviewer', 'Workforce access review and analytics reviewer', 'NATIONAL_LEVEL', '["ORGANISATION"]')
) AS v(id, code, name, description, level, targets)
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_role_definition rd
    WHERE rd.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid AND rd.role_code = v.code
);

-- 1. National Workforce Admin — MOHCC-NATIONAL
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, extension_json, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000101'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-VASH-001',
    'f4000000-0000-4000-8000-000000000101'::uuid,
    'ORGANISATION', 'f2000000-0000-4000-8000-000000000002',
    'f2000000-0000-4000-8000-000000000002'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false,
    '{"roleTemplateId":"vashandi_national_workforce_admin"}',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000101'::uuid);

-- 2. Facility Workforce Manager — Harare Central Hospital
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, extension_json, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000102'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-VASH-002',
    'f4000000-0000-4000-8000-000000000102'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false,
    '{"roleTemplateId":"vashandi_facility_workforce_manager"}',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000102'::uuid);

-- 3. Ordinary Worker — national org assignment (avoids public_facility workspace bleed)
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, extension_json, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000103'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-VASH-003',
    'f4000000-0000-4000-8000-000000000103'::uuid,
    'ORGANISATION', 'f2000000-0000-4000-8000-000000000002',
    'f2000000-0000-4000-8000-000000000002'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false,
    '{"roleTemplateId":"vashandi_self_service_worker"}',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000103'::uuid);

-- 4. HSC Workforce User — Health Service Commission
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, extension_json, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000104'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-VASH-004',
    'f4000000-0000-4000-8000-000000000104'::uuid,
    'ORGANISATION', 'f2000000-0000-4000-8000-000000000020',
    'f2000000-0000-4000-8000-000000000020'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false,
    '{"roleTemplateId":"vashandi_hsc_workforce_admin"}',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000104'::uuid);

-- 5. Workforce Access Reviewer — MOHCC-NATIONAL
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, extension_json, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000105'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-VASH-005',
    'f4000000-0000-4000-8000-000000000105'::uuid,
    'ORGANISATION', 'f2000000-0000-4000-8000-000000000002',
    'f2000000-0000-4000-8000-000000000002'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false,
    '{"roleTemplateId":"vashandi_access_reviewer"}',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000105'::uuid);
