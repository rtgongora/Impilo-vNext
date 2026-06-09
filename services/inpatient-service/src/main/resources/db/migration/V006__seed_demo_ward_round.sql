-- Demo ward round for golden admission Tatenda Moyo (CPID-ZW-00001).

INSERT INTO inpatient.ward_round (
    round_id,
    tenant_id,
    admission_ref,
    ward_id,
    round_type,
    led_by,
    status,
    notes,
    started_at,
    created_at
) VALUES (
    '33333333-3333-3333-3333-333333333301',
    '00000000-0000-4000-8000-000000000001',
    'f2000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111101',
    'MORNING',
    'Dr. Tendai Mapfumo',
    'IN_PROGRESS',
    'Stable overnight; continue IV antibiotics.',
    now(),
    now()
) ON CONFLICT (round_id) DO NOTHING;

INSERT INTO inpatient.ward_round_entry (
    entry_id,
    round_id,
    assessment,
    plan,
    reviewed_by,
    reviewed_at
) VALUES (
    '44444444-4444-4444-4444-444444444401',
    '33333333-3333-3333-3333-333333333301',
    'Afebrile, improving respiratory effort.',
    'Continue current regimen; review labs at 14:00.',
    'Dr. Tendai Mapfumo',
    now()
) ON CONFLICT (entry_id) DO NOTHING;
