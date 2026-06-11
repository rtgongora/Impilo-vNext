-- V40: Test Facility for local telemedicine dev + workspace for quick entry.

INSERT INTO facilities (id, tenant_id, name, code, facility_type, status, province, district, capabilities)
VALUES (
    'a1b2c3d4-00ff-4000-8000-000000000099',
    'tenant-moh-zw',
    'Test Facility',
    'ZW-TEST-001',
    'GENERAL_HOSPITAL',
    'ACTIVE',
    'Harare',
    'Harare Metropolitan',
    '["OUTPATIENT","EMERGENCY","TELEMEDICINE","LABORATORY","PHARMACY"]'::jsonb
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO workspaces (id, tenant_id, facility_id, name, workspace_type, status, config)
VALUES (
    'b1c2c3d4-00ff-4000-8000-000000000099',
    'tenant-moh-zw',
    'a1b2c3d4-00ff-4000-8000-000000000099',
    'Telemedicine Suite 1',
    'OPD',
    'ACTIVE',
    '{"max_concurrent_patients": 1}'::jsonb
)
ON CONFLICT (id) DO NOTHING;
