-- Demo ward/bed inventory for preview and integration tests (facility f1000000-...).

INSERT INTO inpatient.ward (id, tenant_id, facility_id, name, ward_type, floor_label, total_beds, status)
VALUES (
    '11111111-1111-1111-1111-111111111101',
    '00000000-0000-0000-0000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'Medical Ward A',
    'MEDICAL',
    '1st Floor',
    8,
    'ACTIVE'
);

INSERT INTO inpatient.bed (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status)
SELECT
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111101',
    'MWA-' || LPAD(s.n::text, 2, '0'),
    'STANDARD',
    'AVAILABLE'
FROM generate_series(1, 8) AS s(n);

INSERT INTO inpatient.ward (id, tenant_id, facility_id, name, ward_type, floor_label, total_beds, status)
VALUES (
    '11111111-1111-1111-1111-111111111102',
    '00000000-0000-0000-0000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'ICU',
    'ICU',
    '2nd Floor',
    4,
    'ACTIVE'
);

INSERT INTO inpatient.bed (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status)
SELECT
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111102',
    'ICU-' || LPAD(s.n::text, 2, '0'),
    'ICU',
    CASE WHEN s.n = 1 THEN 'OCCUPIED' ELSE 'AVAILABLE' END
FROM generate_series(1, 4) AS s(n);

UPDATE inpatient.bed
SET subject_cpid = 'CPID-DEMO-00001',
    patient_name = 'Demo Patient',
    patient_diagnosis = 'Post-op observation',
    attending_physician = 'Dr. Demo',
    acuity_level = 'high',
    patient_age = 42,
    patient_gender = 'F',
    occupied_at = now()
WHERE bed_number = 'ICU-01'
  AND ward_id = '11111111-1111-1111-1111-111111111102';
