-- =============================================================================
-- Persona Truth Pack (Workforce Governance) — journey-completion wave personas
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- ACTIVE facility assignments for the persona providers seeded in
-- 14-seed-persona-truth-pack-varapi.sql (+ the pre-existing pharmacist
-- PROV-ZW-00004 who never had an assignment, so pharm.zimba gains work access).
-- Facility 1 = Harare Central Hospital; facility 2 = Parirenyatwa (dr.gwena —
-- the second-facility specialist for cross-facility teleconsult journeys).
-- Role definition f4...0001 is the facility clinical role used by seeds 09/13.
--
-- Idempotent via fixed UUID + WHERE NOT EXISTS (mirrors seed 13).
-- UUID range f5...0030-0035 (0001-0022 and 0101-0105 are taken by seeds 09/11/13).
-- =============================================================================

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000030'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00008',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000030'::uuid
);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000031'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00009',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000031'::uuid
);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000032'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00010',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000032'::uuid
);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000033'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00011',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000033'::uuid
);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000034'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00012',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000034'::uuid
);

-- pharm.zimba — existing provider PROV-ZW-00004 (seed 04) never had an assignment.
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000035'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00004',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment
    WHERE subject_id = 'PROV-ZW-00004' AND target_type = 'FACILITY' AND status = 'ACTIVE'
);
