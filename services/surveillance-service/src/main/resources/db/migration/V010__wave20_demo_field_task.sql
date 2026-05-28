-- Wave 21 — demo public-health field task for journey probes.

INSERT INTO surv.field_tasks (
    tenant_id,
    title,
    task_type,
    status,
    assigned_to,
    created_by,
    details
) VALUES (
    '00000000-0000-4000-8000-000000000001'::uuid,
    'Wave 20 demo outreach visit — CPID-ZW-00001 catchment',
    'FIELD_OPERATION',
    'PLANNED',
    'demo-chw-001',
    'wave20-demo',
    '{"demo":"wave20","patientCpid":"CPID-ZW-00001"}'::jsonb
);
