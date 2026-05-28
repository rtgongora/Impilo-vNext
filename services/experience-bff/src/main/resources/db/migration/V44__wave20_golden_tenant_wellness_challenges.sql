-- Wave 21 — golden-tenant wellness challenges for production-readiness probes.

INSERT INTO wellness_challenges (
    id, tenant_id, title, description, challenge_type, target_value, target_unit,
    start_date, end_date, participant_count, status
) VALUES (
    'w3000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001',
    'Wave 20 Steps Challenge',
    'Demo wellness challenge for production-readiness golden tenant',
    'STEPS',
    70000,
    'steps',
    CURRENT_DATE,
    CURRENT_DATE + 7,
    1,
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;
