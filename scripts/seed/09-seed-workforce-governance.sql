-- =============================================================================
-- Workforce Governance — Preview persona work assignments
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Links VARAPI provider public IDs to ACTIVE facility assignments (Harare Central = 1)
-- Idempotent via fixed UUIDs and ON CONFLICT / WHERE NOT EXISTS
-- =============================================================================

-- National sovereign owner organisation (Product Owner / platform administration preview)
INSERT INTO wgv_organisation
    (id, tenant_id, organisation_code, name, legal_name, organisation_type, status, active_flag, created_at, updated_at)
SELECT
    'f2000000-0000-4000-8000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'MOHCC-NATIONAL',
    'Ministry of Health and Child Care',
    'Ministry of Health and Child Care',
    'SOVEREIGN_PUBLIC_OWNER',
    'ACTIVE',
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_organisation
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND organisation_code = 'MOHCC-NATIONAL'
);

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
    '["FACILITY"]',
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
    '["FACILITY","ORGANISATION"]',
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

-- superadmin / System Admin provider (PROV-ZW-ADMIN-001) — national sovereign context
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000011'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER',
    'PROV-ZW-ADMIN-001',
    'f4000000-0000-4000-8000-000000000002'::uuid,
    'ORGANISATION',
    'f2000000-0000-4000-8000-000000000002',
    'f2000000-0000-4000-8000-000000000002'::uuid,
    CURRENT_DATE,
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000011'::uuid
);

-- superadmin facility assignment (secondary — clinical context at Harare Central)
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
    false,
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000010'::uuid
);

UPDATE wgv_assignment
SET primary_flag = false,
    secondary_flag = true,
    updated_at = NOW()
WHERE id = 'f5000000-0000-4000-8000-000000000010'::uuid
  AND EXISTS (
      SELECT 1 FROM wgv_assignment
      WHERE id = 'f5000000-0000-4000-8000-000000000011'::uuid
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

-- =============================================================================
-- Multi-context Product Owner — additional governance organisations the PO
-- (PROV-ZW-ADMIN-001) holds ACTIVE assignments in, so the platform owner can
-- context-switch across regulator / payer / marketplace governance surfaces
-- through the product's own model (no allowlist superuser).
-- NOTE: surfacing these per selected context depends on the BFF resolving the
-- active organisation context (a contract enhancement); the rows are seeded now
-- so that capability has real data to render. allowed_target_types are JSON
-- arrays (AssignmentService parses them as JSON).
-- =============================================================================

-- Governance organisations (regulator / payer / marketplace)
INSERT INTO wgv_organisation
    (id, tenant_id, organisation_code, name, legal_name, organisation_type, status, active_flag, created_at, updated_at)
SELECT v.id::uuid, '00000000-0000-4000-8000-000000000001'::uuid, v.code, v.name, v.name, v.otype, 'ACTIVE', true, NOW(), NOW()
FROM (VALUES
    ('f2000000-0000-4000-8000-000000000010', 'HPA-ZW',        'Health Professions Authority of Zimbabwe', 'HEALTH_PROFESSIONS_AUTHORITY'),
    ('f2000000-0000-4000-8000-000000000011', 'PAYER-NATPHARO','National Health Insurer',                  'INSURER'),
    ('f2000000-0000-4000-8000-000000000012', 'MKT-VENDOR-001','Approved Marketplace Software Vendor',     'SOFTWARE_VENDOR')
) AS v(id, code, name, otype)
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_organisation o
    WHERE o.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid AND o.organisation_code = v.code
);

-- Governance role definitions (org-derived, not clinical)
INSERT INTO wgv_role_definition
    (id, tenant_id, role_code, name, description, role_category, role_level,
     allowed_target_types, requires_provider_flag, requires_professional_standing_flag,
     special_governance_flag, active_flag, created_at, updated_at)
SELECT v.id::uuid, '00000000-0000-4000-8000-000000000001'::uuid, v.code, v.name, v.name,
       'ADMINISTRATIVE', 'NATIONAL_LEVEL', '["ORGANISATION"]', false, false, true, true, NOW(), NOW()
FROM (VALUES
    ('f4000000-0000-4000-8000-000000000010', 'REGULATOR_ADMIN',   'Regulator Administrator'),
    ('f4000000-0000-4000-8000-000000000011', 'PAYER_ADMIN',       'Payer Administrator'),
    ('f4000000-0000-4000-8000-000000000012', 'MARKETPLACE_ADMIN', 'Marketplace Administrator')
) AS v(id, code, name)
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_role_definition r
    WHERE r.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid AND r.role_code = v.code
);

-- ACTIVE assignments for the Product Owner across each governance organisation
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT v.id::uuid, '00000000-0000-4000-8000-000000000001'::uuid, 'PROVIDER', 'PROV-ZW-ADMIN-001',
       v.role_def::uuid, 'ORGANISATION', v.org, v.org::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
FROM (VALUES
    ('f5000000-0000-4000-8000-000000000020', 'f4000000-0000-4000-8000-000000000010', 'f2000000-0000-4000-8000-000000000010'),
    ('f5000000-0000-4000-8000-000000000021', 'f4000000-0000-4000-8000-000000000011', 'f2000000-0000-4000-8000-000000000011'),
    ('f5000000-0000-4000-8000-000000000022', 'f4000000-0000-4000-8000-000000000012', 'f2000000-0000-4000-8000-000000000012')
) AS v(id, role_def, org)
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment a WHERE a.id = v.id::uuid
);
