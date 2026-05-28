-- Wave 20 slice 13 — demo bill for Costa billing / Rx finance probes.

INSERT INTO costa_bill_headers (
    bill_id,
    tenant_id,
    facility_id,
    bill_type,
    status,
    currency,
    total_payable,
    patient_payable,
    notes
) VALUES (
    '01H0000000000000000000002',
    '00000000-0000-4000-8000-000000000001'::uuid,
    'f1000000-0000-0000-0000-000000000001'::uuid,
    'ENCOUNTER',
    'DRAFT',
    'USD',
    42.50,
    42.50,
    'DEMO-WAVE20 Rx billing for CPID-ZW-00001'
) ON CONFLICT (bill_id) DO NOTHING;
