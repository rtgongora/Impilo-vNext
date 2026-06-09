-- Demo OPD queue for compose smoke / Wave 20 golden patient (Harare Central).
-- Tenant aligns with inpatient and coverage seeds.

INSERT INTO pct.pct_journeys (
    journey_id,
    tenant_id,
    facility_id,
    patient_cpid,
    state,
    referral_source,
    created_at,
    updated_at
) VALUES (
    '01HARAREQUEUE00001CPIDZW',
    '00000000-0000-4000-8000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'CPID-ZW-00001',
    'QUEUED',
    'WALK_IN',
    now(),
    now()
) ON CONFLICT (journey_id) DO NOTHING;

INSERT INTO pct.pct_queues (
    queue_id,
    tenant_id,
    facility_id,
    name,
    queue_type,
    active,
    created_at
) VALUES (
    '11111111-1111-1111-1111-111111111201',
    '00000000-0000-4000-8000-000000000001',
    'f1000000-0000-0000-0000-000000000001',
    'Harare Central OPD',
    'FIFO',
    true,
    now()
) ON CONFLICT (queue_id) DO NOTHING;

INSERT INTO pct.pct_queue_items (
    item_id,
    tenant_id,
    queue_id,
    journey_id,
    patient_cpid,
    priority,
    status,
    token_number,
    created_at
) VALUES (
    '22222222-2222-2222-2222-222222222201',
    '00000000-0000-4000-8000-000000000001',
    '11111111-1111-1111-1111-111111111201',
    '01HARAREQUEUE00001CPIDZW',
    'CPID-ZW-00001',
    3,
    'WAITING',
    101,
    now()
) ON CONFLICT (item_id) DO NOTHING;
