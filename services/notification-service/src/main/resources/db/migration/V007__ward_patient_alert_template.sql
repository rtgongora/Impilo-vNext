-- Ward patient bedside alert — routed to ward staff inbox (provider mobile / ward console).

INSERT INTO ns_templates (
    id,
    tenant_id,
    pod_id,
    key,
    channel,
    name,
    subject,
    content,
    enabled,
    status,
    current_version,
    created_at,
    updated_at
)
VALUES (
    gen_random_uuid()::text,
    'tenant-moh-zw',
    'national',
    'WARD_PATIENT_ALERT',
    'PUSH',
    'Ward Patient Alert',
    'Ward assistance requested',
    'Patient {{patientId}} needs ward assistance: {{message}} (alert {{alertId}})',
    true,
    'PUBLISHED',
    1,
    NOW(),
    NOW()
)
ON CONFLICT DO NOTHING;
