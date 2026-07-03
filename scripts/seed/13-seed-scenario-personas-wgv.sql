-- =============================================================================
-- Scenario persona alignment (Workforce Governance) — nurse.chienda
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- ACTIVE facility assignment at Harare Central (facility 1) for PROV-ZW-00007
-- (Rumbidzai Chienda, seeded in 12-seed-scenario-personas-varapi.sql), using
-- the same role definition as the other Harare Central clinicians (seed 09).
--
-- Idempotent via fixed UUID + WHERE NOT EXISTS.
-- =============================================================================

INSERT INTO wgv_assignment
    (id, tenant_id, subject_type, subject_id, role_definition_id, target_type, target_id,
     organisation_id, start_date, status, primary_flag, secondary_flag, created_at, updated_at)
SELECT
    'f5000000-0000-4000-8000-000000000007'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PROVIDER',
    'PROV-ZW-00007',
    'f4000000-0000-4000-8000-000000000001'::uuid,
    'FACILITY',
    '1',
    'f2000000-0000-4000-8000-000000000001'::uuid,
    CURRENT_DATE,
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM wgv_assignment WHERE id = 'f5000000-0000-4000-8000-000000000007'::uuid
);
