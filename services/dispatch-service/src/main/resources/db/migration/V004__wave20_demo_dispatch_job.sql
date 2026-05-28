-- Wave 20 slice 13 — demo dispatch job for Rx → pay → dispatch journey.

INSERT INTO dsp_dispatch_jobs (
    job_id,
    tenant_id,
    facility_ref,
    request_ref,
    status
) VALUES (
    'd3000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'f1000000-0000-0000-0000-000000000001',
    'RX-WAVE20-CPID-ZW-00001',
    'NEW'
) ON CONFLICT (job_id) DO NOTHING;
