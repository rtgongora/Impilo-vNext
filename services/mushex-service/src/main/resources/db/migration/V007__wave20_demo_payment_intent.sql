-- Wave 20 slice 13 — demo payment intent for finance / Rx billing probes.

INSERT INTO mushex_payment_intents (
    intent_id,
    tenant_id,
    facility_id,
    source_type,
    source_id,
    currency,
    amount_total,
    amount_paid,
    status,
    idempotency_key,
    metadata
) VALUES (
    '01H0000000000000000000001',
    '00000000-0000-4000-8000-000000000001'::uuid,
    'f1000000-0000-0000-0000-000000000001'::uuid,
    'ADHOC',
    'CPID-ZW-00001',
    'USD',
    42.50,
    0.00,
    'CREATED',
    'wave20-demo-rx-billing-0001',
    '{"demo":"wave20","patientCpid":"CPID-ZW-00001"}'::jsonb
) ON CONFLICT (idempotency_key) DO NOTHING;
