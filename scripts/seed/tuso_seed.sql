-- =============================================================================
-- Service : tuso-service
-- Database: tuso
-- Purpose : Initial seed data for facilities, levels, workspaces, and shifts
-- =============================================================================

\c tuso

-- 1. National Level Facility (Pod 'national')
INSERT INTO tuso.facility (tenant_id, facility_code, name, facility_type, province, district, status, level)
VALUES ('00000000-0000-0000-0000-000000000000', 'NATIONAL-HQ', 'Ministry of Health HQ', 'ADMIN', 'Harare', 'Harare Central', 'ACTIVE', 'NATIONAL')
ON CONFLICT (tenant_id, facility_code) DO NOTHING;

-- 2. Provincial Level Facility
INSERT INTO tuso.facility (tenant_id, facility_code, name, facility_type, province, district, status, level, parent_id)
SELECT '00000000-0000-0000-0000-000000000000', 'PROV-HRE', 'Harare Provincial Office', 'ADMIN', 'Harare', 'Harare Central', 'ACTIVE', 'PROVINCIAL', id
FROM tuso.facility WHERE facility_code = 'NATIONAL-HQ'
ON CONFLICT (tenant_id, facility_code) DO NOTHING;

-- 3. District Level Facility
INSERT INTO tuso.facility (tenant_id, facility_code, name, facility_type, province, district, status, level, parent_id)
SELECT '00000000-0000-0000-0000-000000000000', 'DIST-HRE', 'Harare District Office', 'ADMIN', 'Harare', 'Harare Central', 'ACTIVE', 'DISTRICT', id
FROM tuso.facility WHERE facility_code = 'PROV-HRE'
ON CONFLICT (tenant_id, facility_code) DO NOTHING;

-- 4. Actual Health Facility
INSERT INTO tuso.facility (tenant_id, facility_code, name, facility_type, province, district, status, level, parent_id)
SELECT '00000000-0000-0000-0000-000000000000', 'FAC-PARI-01', 'Parirenyatwa General Hospital', 'HOSPITAL', 'Harare', 'Harare Central', 'ACTIVE', 'FACILITY', id
FROM tuso.facility WHERE facility_code = 'DIST-HRE'
ON CONFLICT (tenant_id, facility_code) DO NOTHING;

-- 5. Workspaces for Facility FAC-PARI-01
INSERT INTO tuso.workspace (id, facility_id, tenant_id, name, workspace_type, active)
SELECT gen_random_uuid(), id, tenant_id, 'Outpatient Department', 'OPD', true
FROM tuso.facility WHERE facility_code = 'FAC-PARI-01'
ON CONFLICT DO NOTHING;

INSERT INTO tuso.workspace (id, facility_id, tenant_id, name, workspace_type, active)
SELECT gen_random_uuid(), id, tenant_id, 'Emergency Room', 'ER', true
FROM tuso.facility WHERE facility_code = 'FAC-PARI-01'
ON CONFLICT DO NOTHING;

-- 6. Default Shift Definition for OPD
INSERT INTO tuso.shift (id, tenant_id, facility_id, workspace_id, provider_id, provider_type, status)
SELECT gen_random_uuid(), tenant_id, facility_id, id, 'SYSTEM-BOT-01', 'BOT', 'ACTIVE'
FROM tuso.workspace 
WHERE name = 'Outpatient Department' 
AND facility_id = (SELECT id FROM tuso.facility WHERE facility_code = 'FAC-PARI-01')
ON CONFLICT DO NOTHING;
