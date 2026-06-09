-- Demo telemedicine referral + session for compose smoke (golden patient).

INSERT INTO pct.pct_referrals (
    referral_id,
    tenant_id,
    patient_cpid,
    facility_id,
    referral_type,
    specialty,
    urgency,
    reason,
    status,
    preferred_mode,
    modality,
    created_at,
    updated_at
) VALUES (
    '55555555-5555-5555-5555-555555555501',
    '00000000-0000-4000-8000-000000000001',
    'CPID-ZW-00001',
    'f1000000-0000-0000-0000-000000000001',
    'SPECIALIST',
    'GENERAL_MEDICINE',
    'ROUTINE',
    'Follow-up teleconsult for chronic care review',
    'SUBMITTED',
    'VIDEO',
    'SYNCHRONOUS',
    now(),
    now()
) ON CONFLICT (referral_id) DO NOTHING;

INSERT INTO pct.pct_telehealth_sessions (
    session_id,
    tenant_id,
    patient_cpid,
    provider_id,
    facility_id,
    referral_id,
    session_type,
    status,
    scheduled_at,
    created_at,
    updated_at
) VALUES (
    '66666666-6666-6666-6666-666666666601',
    '00000000-0000-4000-8000-000000000001',
    'CPID-ZW-00001',
    'provider-demo-001',
    'f1000000-0000-0000-0000-000000000001',
    '55555555-5555-5555-5555-555555555501',
    'VIDEO',
    'SCHEDULED',
    now() + interval '1 hour',
    now(),
    now()
) ON CONFLICT (session_id) DO NOTHING;
