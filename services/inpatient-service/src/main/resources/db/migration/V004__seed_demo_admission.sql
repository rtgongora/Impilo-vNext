-- Wave 20 demo admission — golden patient Tatenda Moyo (CPID-ZW-00001) at Harare Central.
INSERT INTO inpatient.admission (
    tenant_id,
    admission_ref,
    encounter_id,
    subject_cpid,
    facility_id,
    status,
    admitted_at
) VALUES (
    '00000000-0000-4000-8000-000000000001',
    'f2000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'CPID-ZW-00001',
    'f1000000-0000-0000-0000-000000000001',
    'ADMITTED',
    now()
) ON CONFLICT (admission_ref) DO NOTHING;
