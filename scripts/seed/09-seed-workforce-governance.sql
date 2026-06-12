-- =============================================================================
-- Workforce Governance — Preview persona work assignments
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Links VARAPI provider public IDs to ACTIVE facility assignments (Harare Central = 1)
-- Idempotent via fixed UUIDs and ON CONFLICT / WHERE NOT EXISTS
-- =============================================================================

-- Organisation backing Harare Central Hospital
INSERT INTO wgv_organisation
    (id, tenant_id, organisation_code, name, legal_name, organisation_type, status, active_flag, created_at, updated_at)
SELECT
    'f2000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'MOHCC-HCH',
    'Harare Central Hospital',
    'Harare Central Hospital',
    'PUBLIC_FACILITY',
    'ACTIVE',
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_organisation
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND organisation_code = 'MOHCC-HCH'
);

INSERT INTO wgv_facility_organisation_link
    (id, tenant_id, facility_id, organisation_id, relationship_type, status, primary_flag, created_at, updated_at)
SELECT
    'f3000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    1,
    'f2000000-0000-4000-8000-000000000001'::uuid,
    'OPERATES',
    'ACTIVE',
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_facility_organisation_link
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND facility_id = 1
);

INSERT INTO wgv_role_definition
    (id, tenant_id, role_code, name, description, role_category, role_level,
     allowed_target_types, requires_provider_flag, requires_professional_standing_flag,
     special_governance_flag, active_flag, created_at, updated_at)
SELECT
    'f4000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'CLINICAL_DOCTOR',
    'Clinical Doctor',
    'Golden-path clinical doctor at facility',
    'CLINICAL',
    'FACILITY_LEVEL',
    'FACILITY',
    true,
    true,
    false,
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_role_definition
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND role_code = 'CLINICAL_DOCTOR'
);

INSERT INTO wgv_role_definition
    (id, tenant_id, role_code, name, description, role_category, role_level,
     allowed_target_types, requires_provider_flag, requires_professional_standing_flag,
     special_governance_flag, active_flag, created_at, updated_at)
SELECT
    'f4000000-0000-4000-8000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PLATFORM_ADMIN',
    'Platform Administrator',
    'National platform administrator work context',
    'ADMINISTRATIVE',
    'NATIONAL_LEVEL',
    'FACILITY,ORGANISATION',
    true,
    false,
    true,
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_role_definition
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND role_code = 'PLATFORM_ADMIN'
);

-- superadmin / System Admin provider (PROV-ZW-ADMIN-001)
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000010'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER',
    'PROV-ZW-ADMIN-001',
    'f4000000-0000-4000-8000-000000000002'::uuid,
    'FACILITY',
    '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE,
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000010'::uuid
);

-- Dr Mapfumo golden-path clinician (PROV-ZW-00001)
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER',
    'PROV-ZW-00001',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY',
    '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE,
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000001'::uuid
);

-- Nurse Musekwa (PROV-ZW-00002) at Harare Central
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER',
    'PROV-ZW-00002',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY',
    '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE,
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000002'::uuid
);
