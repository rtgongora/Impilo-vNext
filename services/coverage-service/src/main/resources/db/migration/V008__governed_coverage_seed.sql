-- Governed MOHCC national coverage catalogue for compose, preview, and enrollment e2e.

INSERT INTO cv_coverage_plans (
    id, tenant_id, pod_id, plan_code, plan_name, payer_id, plan_type, status, effective_from
) VALUES
(
    'bbbbbbbb-0001-0000-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'COV-MOHCC-CORE',
    'MOHCC National Core Scheme',
    'PAYER-MOHCC',
    'GOVERNMENT',
    'ACTIVE',
    CURRENT_DATE
),
(
    'bbbbbbbb-0001-0000-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'COV-PRIVATE-PLUS',
    'Private Medical Aid Plus',
    'PAYER-PRIVATE-01',
    'PRIVATE',
    'ACTIVE',
    CURRENT_DATE
)
ON CONFLICT (tenant_id, plan_code) DO NOTHING;

INSERT INTO cv_remittances (
    id, tenant_id, pod_id, payer_id, provider_ref, amount, currency, status, reference_number
) VALUES
(
    'cccccccc-0001-0000-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'PAYER-MOHCC',
    'PRV-HARARE-CENTRAL',
    12500.00,
    'USD',
    'ISSUED',
    'REM-MOH-2026-001'
),
(
    'cccccccc-0001-0000-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'PAYER-PRIVATE-01',
    'PRV-BULAWAYO-GP',
    4200.50,
    'USD',
    'RECONCILED',
    'REM-PVT-2026-014'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO cv_utilization_summary (
    tenant_id, member_cpid, plan_code, benefit_code, period_start, period_end, limit_amount, used_amount, used_count
) VALUES
(
    '00000000-0000-4000-8000-000000000001'::uuid,
    'compose-coverage-citizen-001',
    'COV-MOHCC-CORE',
    'OUTPATIENT',
    DATE_TRUNC('year', CURRENT_DATE)::date,
    (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year' - INTERVAL '1 day')::date,
    5000.00,
    1200.00,
    4
),
(
    '00000000-0000-4000-8000-000000000001'::uuid,
    'compose-coverage-citizen-001',
    'COV-MOHCC-CORE',
    'INPATIENT',
    DATE_TRUNC('year', CURRENT_DATE)::date,
    (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year' - INTERVAL '1 day')::date,
    25000.00,
    0.00,
    0
)
ON CONFLICT (tenant_id, member_cpid, plan_code, benefit_code, period_start) DO NOTHING;
