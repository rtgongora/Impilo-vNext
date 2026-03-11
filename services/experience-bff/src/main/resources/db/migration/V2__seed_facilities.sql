-- ============================================================================
-- Experience BFF — Seed Data for Development
-- Provides realistic facility + workspace data for smoke testing
-- ============================================================================

INSERT INTO facilities (id, tenant_id, name, code, facility_type, status, province, district, capabilities)
VALUES
    ('a1b2c3d4-0001-4000-8000-000000000001', 'tenant-moh-zw', 'Harare Central Hospital', 'HCH-001', 'CENTRAL_HOSPITAL', 'ACTIVE', 'Harare', 'Harare Metropolitan', '["OPD","IPD","THEATRE","LAB","PHARMACY","RADIOLOGY"]'::jsonb),
    ('a1b2c3d4-0002-4000-8000-000000000002', 'tenant-moh-zw', 'Parirenyatwa Group of Hospitals', 'PGH-001', 'CENTRAL_HOSPITAL', 'ACTIVE', 'Harare', 'Harare Metropolitan', '["OPD","IPD","THEATRE","LAB","PHARMACY","RADIOLOGY","ICU"]'::jsonb),
    ('a1b2c3d4-0003-4000-8000-000000000003', 'tenant-moh-zw', 'Chitungwiza Central Hospital', 'CCH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Harare', 'Chitungwiza', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0004-4000-8000-000000000004', 'tenant-moh-zw', 'Mpilo Central Hospital', 'MCH-001', 'CENTRAL_HOSPITAL', 'ACTIVE', 'Bulawayo', 'Bulawayo Metropolitan', '["OPD","IPD","THEATRE","LAB","PHARMACY","RADIOLOGY"]'::jsonb),
    ('a1b2c3d4-0005-4000-8000-000000000005', 'tenant-moh-zw', 'United Bulawayo Hospitals', 'UBH-001', 'CENTRAL_HOSPITAL', 'ACTIVE', 'Bulawayo', 'Bulawayo Metropolitan', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0006-4000-8000-000000000006', 'tenant-moh-zw', 'Mutare Provincial Hospital', 'MPH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Manicaland', 'Mutare', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0007-4000-8000-000000000007', 'tenant-moh-zw', 'Gweru Provincial Hospital', 'GPH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Midlands', 'Gweru', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0008-4000-8000-000000000008', 'tenant-moh-zw', 'Masvingo Provincial Hospital', 'MVPH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Masvingo', 'Masvingo', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0009-4000-8000-000000000009', 'tenant-moh-zw', 'Bindura Provincial Hospital', 'BPH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Mashonaland Central', 'Bindura', '["OPD","IPD","LAB","PHARMACY"]'::jsonb),
    ('a1b2c3d4-0010-4000-8000-000000000010', 'tenant-moh-zw', 'Chinhoyi Provincial Hospital', 'CPH-001', 'PROVINCIAL_HOSPITAL', 'ACTIVE', 'Mashonaland West', 'Chinhoyi', '["OPD","IPD","LAB","PHARMACY"]'::jsonb);

INSERT INTO workspaces (id, tenant_id, facility_id, name, workspace_type, status, config)
VALUES
    ('b1c2d3e4-0001-4000-8000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'OPD Consultation Room 1', 'OPD', 'ACTIVE', '{"max_concurrent_patients": 1}'::jsonb),
    ('b1c2d3e4-0002-4000-8000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'Pharmacy Dispensing Bay', 'PHARMACY', 'INACTIVE', '{"max_concurrent_patients": 5}'::jsonb),
    ('b1c2d3e4-0003-4000-8000-000000000003', 'tenant-moh-zw', 'a1b2c3d4-0002-4000-8000-000000000002', 'Emergency Triage', 'EMERGENCY', 'ACTIVE', '{"max_concurrent_patients": 10}'::jsonb);
