-- Consolidated V004 demo seed: admission + ward/bed inventory (replaces duplicate V004 files).

INSERT INTO inpatient.admission (
    tenant_id, admission_ref, encounter_id, subject_cpid, facility_id, status, admitted_at
) VALUES (
    '00000000-0000-4000-8000-000000000001',
    'f2000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'CPID-ZW-00001',
    'f1000000-0000-0000-0000-000000000001',
    'ADMITTED',
    now()
) ON CONFLICT (admission_ref) DO NOTHING;

INSERT INTO inpatient.ward (id, tenant_id, facility_id, name, ward_type, floor_label, total_beds, status)
VALUES (
    '11111111-1111-1111-1111-111111111101',
    '00000000-0000-4000-8000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'Medical Ward A', 'MEDICAL', '1st Floor', 8, 'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO inpatient.bed (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status)
SELECT gen_random_uuid(), '00000000-0000-4000-8000-000000000001', 'f1000000-0000-0000-0000-000000000001',
       '11111111-1111-1111-1111-111111111101', 'MWA-' || LPAD(s.n::text, 2, '0'), 'STANDARD', 'AVAILABLE'
FROM generate_series(1, 8) AS s(n)
WHERE NOT EXISTS (
    SELECT 1 FROM inpatient.bed b
    WHERE b.ward_id = '11111111-1111-1111-1111-111111111101' AND b.bed_number = 'MWA-' || LPAD(s.n::text, 2, '0')
);

INSERT INTO inpatient.ward (id, tenant_id, facility_id, name, ward_type, floor_label, total_beds, status)
VALUES (
    '11111111-1111-1111-1111-111111111102',
    '00000000-0000-4000-8000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'ICU', 'ICU', '2nd Floor', 4, 'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO inpatient.bed (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status)
SELECT gen_random_uuid(), '00000000-0000-4000-8000-000000000001', 'f1000000-0000-0000-0000-000000000001',
       '11111111-1111-1111-1111-111111111102', 'ICU-' || LPAD(s.n::text, 2, '0'), 'ICU',
       CASE WHEN s.n = 1 THEN 'OCCUPIED' ELSE 'AVAILABLE' END
FROM generate_series(1, 4) AS s(n)
WHERE NOT EXISTS (
    SELECT 1 FROM inpatient.bed b
    WHERE b.ward_id = '11111111-1111-1111-1111-111111111102' AND b.bed_number = 'ICU-' || LPAD(s.n::text, 2, '0')
);

UPDATE inpatient.bed
SET subject_cpid = 'CPID-DEMO-00001', patient_name = 'Demo Patient', patient_diagnosis = 'Post-op observation',
    attending_physician = 'Dr. Demo', acuity_level = 'high', patient_age = 42, patient_gender = 'F', occupied_at = now()
WHERE bed_number = 'ICU-01' AND ward_id = '11111111-1111-1111-1111-111111111102';
