-- =============================================================================
-- Theatre Persona Truth Pack (Workforce Governance) — theatre Wave 0
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- ACTIVE facility assignments for the surgical-team providers seeded in
-- 18-seed-theatre-personas-varapi.sql, so the personas have facility work access
-- (the shell resolves the workspace from an ACTIVE wgv_assignment) and the
-- theatre TEAM readiness gate can resolve a real surgical team at BOTH central
-- hospitals. Facility 1 = Harare Central Hospital; facility 2 = Parirenyatwa.
-- Role definition f4...0001 is the facility clinical role used by seeds 09/13/15.
--
-- NOTE: the vashandi ROSTER leg of the TEAM readiness gate additionally reads a
-- PUBLISHED vashandi roster (GET /v1/internal/vashandi/rosters?...&status=PUBLISHED),
-- which is a distinct domain object from these assignments — see the Wave 0
-- report / Wave 1-2 follow-up. These assignments are what give the personas the
-- workforce anchor they need to sign in and operate.
--
-- Idempotent via fixed UUID + WHERE NOT EXISTS (mirrors seed 15).
-- UUID range f5...0040-004d (0001-0022, 0101-0105 seeds 09/11/13; 0030-0037 seed 15).
-- =============================================================================

-- surgeon.moyo — Harare Central + Parirenyatwa
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000040'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00020', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000040'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000041'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00020', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000041'::uuid);

-- anaes.ndlovu
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000042'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00021', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000042'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000043'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00021', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000043'::uuid);

-- scrub.ncube
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000044'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00022', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000044'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000045'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00022', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000045'::uuid);

-- pacu.sibanda
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000046'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00023', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000046'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000047'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00023', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000047'::uuid);

-- ward.dube
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000048'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00024', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000048'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-000000000049'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00024', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000049'::uuid);

-- porter.chuma
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-00000000004a'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00025', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-00000000004a'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-00000000004b'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00025', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-00000000004b'::uuid);

-- coord.mutasa
INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-00000000004c'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00026', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '1',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', true, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-00000000004c'::uuid);

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT 'f5000000-0000-4000-8000-00000000004d'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER', 'PROV-ZW-00026', 'f4000000-0000-4000-8000-000000000001'::uuid, 'FACILITY', '2',
    'f2000000-0000-4000-8000-000000000001'::uuid, CURRENT_DATE, 'ACTIVE', false, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-00000000004d'::uuid);
