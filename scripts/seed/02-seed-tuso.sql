-- =============================================================================
-- TUSO — Facility Registry Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Facilities: 5 national reference hospitals across Zimbabwe
-- Workspaces: OPD, Pharmacy, Emergency, Lab per facility
-- =============================================================================

\c tuso

INSERT INTO tuso.facility
    (tenant_id, facility_code, name, facility_type, province, district,
     latitude, longitude, status, gofr_id, ownership, level, operational_status)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'ZW-HCH-001', 'Harare Central Hospital',
     'CENTRAL_HOSPITAL', 'Harare', 'Harare Metropolitan',
     -17.8292, 31.0522, 'ACTIVE', 'GOFR-ZW-001', 'GOVERNMENT', 'CENTRAL', 'OPERATIONAL'),

    ('00000000-0000-4000-8000-000000000001', 'ZW-PGH-001', 'Parirenyatwa Group of Hospitals',
     'CENTRAL_HOSPITAL', 'Harare', 'Harare Metropolitan',
     -17.8058, 31.0455, 'ACTIVE', 'GOFR-ZW-002', 'GOVERNMENT', 'CENTRAL', 'OPERATIONAL'),

    ('00000000-0000-4000-8000-000000000001', 'ZW-CCH-001', 'Chitungwiza Central Hospital',
     'PROVINCIAL_HOSPITAL', 'Harare', 'Chitungwiza',
     -17.9982, 31.0731, 'ACTIVE', 'GOFR-ZW-003', 'GOVERNMENT', 'PROVINCIAL', 'OPERATIONAL'),

    ('00000000-0000-4000-8000-000000000001', 'ZW-MCH-001', 'Mpilo Central Hospital',
     'CENTRAL_HOSPITAL', 'Bulawayo', 'Bulawayo Metropolitan',
     -20.1503, 28.5814, 'ACTIVE', 'GOFR-ZW-004', 'GOVERNMENT', 'CENTRAL', 'OPERATIONAL'),

    ('00000000-0000-4000-8000-000000000001', 'ZW-GPH-001', 'Gweru Provincial Hospital',
     'PROVINCIAL_HOSPITAL', 'Midlands', 'Gweru',
     -19.4572, 29.8124, 'ACTIVE', 'GOFR-ZW-005', 'GOVERNMENT', 'PROVINCIAL', 'OPERATIONAL');

INSERT INTO tuso.facility_geo (facility_id, address_line1, city, province, district, country)
VALUES
    (1, 'Lobengula Road', 'Harare', 'Harare', 'Harare Metropolitan', 'ZWE'),
    (2, 'Mazowe Street', 'Harare', 'Harare', 'Harare Metropolitan', 'ZWE'),
    (3, 'Chitungwiza Town Centre', 'Chitungwiza', 'Harare', 'Chitungwiza', 'ZWE'),
    (4, 'Mpilo Avenue', 'Bulawayo', 'Bulawayo', 'Bulawayo Metropolitan', 'ZWE'),
    (5, 'Robert Mugabe Way', 'Gweru', 'Midlands', 'Gweru', 'ZWE');

INSERT INTO tuso.facility_capability
    (facility_id, tenant_id, capability_code, capability_type, name, active)
VALUES
    (1, '00000000-0000-4000-8000-000000000001', 'OPD',       'SERVICE', 'Outpatient Department',     true),
    (1, '00000000-0000-4000-8000-000000000001', 'PHARMACY',  'SERVICE', 'Pharmacy',                  true),
    (1, '00000000-0000-4000-8000-000000000001', 'LAB',       'SERVICE', 'Laboratory',                true),
    (1, '00000000-0000-4000-8000-000000000001', 'IPD',       'SERVICE', 'Inpatient Department',      true),
    (1, '00000000-0000-4000-8000-000000000001', 'RADIOLOGY', 'SERVICE', 'Radiology',                 true),
    (2, '00000000-0000-4000-8000-000000000001', 'OPD',       'SERVICE', 'Outpatient Department',     true),
    (2, '00000000-0000-4000-8000-000000000001', 'EMERGENCY', 'SERVICE', 'Emergency Unit',            true),
    (2, '00000000-0000-4000-8000-000000000001', 'PHARMACY',  'SERVICE', 'Pharmacy',                  true),
    (2, '00000000-0000-4000-8000-000000000001', 'LAB',       'SERVICE', 'Laboratory',                true),
    (2, '00000000-0000-4000-8000-000000000001', 'ICU',       'SERVICE', 'Intensive Care Unit',       true),
    (3, '00000000-0000-4000-8000-000000000001', 'OPD',       'SERVICE', 'Outpatient Department',     true),
    (3, '00000000-0000-4000-8000-000000000001', 'PHARMACY',  'SERVICE', 'Pharmacy',                  true),
    (3, '00000000-0000-4000-8000-000000000001', 'LAB',       'SERVICE', 'Laboratory',                true),
    (4, '00000000-0000-4000-8000-000000000001', 'OPD',       'SERVICE', 'Outpatient Department',     true),
    (4, '00000000-0000-4000-8000-000000000001', 'EMERGENCY', 'SERVICE', 'Emergency Unit',            true),
    (4, '00000000-0000-4000-8000-000000000001', 'PHARMACY',  'SERVICE', 'Pharmacy',                  true),
    (5, '00000000-0000-4000-8000-000000000001', 'OPD',       'SERVICE', 'Outpatient Department',     true),
    (5, '00000000-0000-4000-8000-000000000001', 'PHARMACY',  'SERVICE', 'Pharmacy',                  true);

INSERT INTO tuso.workspace
    (id, facility_id, tenant_id, name, workspace_type, active)
VALUES
    ('a0000000-0000-4000-8000-000000000001', 1, '00000000-0000-4000-8000-000000000001',
     'OPD Consultation Room 1',      'OPD',       true),
    ('a0000000-0000-4000-8000-000000000002', 1, '00000000-0000-4000-8000-000000000001',
     'Pharmacy Dispensing Bay A',     'PHARMACY',  true),
    ('a0000000-0000-4000-8000-000000000003', 1, '00000000-0000-4000-8000-000000000001',
     'Laboratory Intake',             'LABORATORY', true),
    ('a0000000-0000-4000-8000-000000000004', 2, '00000000-0000-4000-8000-000000000001',
     'Emergency Triage Bay',          'EMERGENCY', true),
    ('a0000000-0000-4000-8000-000000000005', 2, '00000000-0000-4000-8000-000000000001',
     'OPD Main Consultation',         'OPD',       true),
    ('a0000000-0000-4000-8000-000000000006', 3, '00000000-0000-4000-8000-000000000001',
     'Chitungwiza OPD',               'OPD',       true),
    ('a0000000-0000-4000-8000-000000000007', 4, '00000000-0000-4000-8000-000000000001',
     'Mpilo Emergency Bay',           'EMERGENCY', true),
    ('a0000000-0000-4000-8000-000000000008', 4, '00000000-0000-4000-8000-000000000001',
     'Mpilo OPD',                     'OPD',       true),
    ('a0000000-0000-4000-8000-000000000009', 5, '00000000-0000-4000-8000-000000000001',
     'Gweru OPD',                     'OPD',       true),
    ('a0000000-0000-4000-8000-000000000010', 1, '00000000-0000-4000-8000-000000000001',
     'Harare Central IPD Ward A',     'IPD',       true);

INSERT INTO tuso.facility_readiness
    (facility_id, connectivity, power_source, power_backup, device_count, ehr_ready)
VALUES
    (1, 'FIBRE', 'GRID', true,  45, true),
    (2, 'FIBRE', 'GRID', true,  60, true),
    (3, '4G',    'GRID', true,  20, true),
    (4, 'FIBRE', 'GRID', true,  38, true),
    (5, '4G',    'GRID', false, 15, true);
