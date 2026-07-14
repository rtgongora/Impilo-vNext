-- =============================================================================
-- Coverage V014 — HEALTH_WORKER_RECOGNITION subsidy programme (governed seed).
--
-- Recognised health workers may OPT IN to this programme when seeking care
-- themselves; enrolment requires a positive server-side recognition check
-- (VARAPI licensed provider / Vashandi workforce) and records
-- enrolled_by = SELF_OPT_IN:RECOGNITION:<class>, exemption_category =
-- HEALTH_WORKER against the unified ledgered enrolment model (V013).
--
-- Governance: seeded DRAFT (invisible to listActive and enrol, which require
-- ACTIVE). A tenant must explicitly activate the programme before any opt-in
-- can succeed. Advantages are administrative/financial ONLY.
-- Pattern: governed seed per V008/V009.
-- =============================================================================

INSERT INTO cv_subsidy_programs (
    id, tenant_id, pod_id, program_code, program_name, payer_id, subsidy_type,
    status, annual_cap, currency, effective_from, rules_json
) VALUES (
    'dddddddd-0001-0000-0000-000000000003'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'SUB-HEALTH-WORKER-RECOGNITION',
    'Health Worker Recognition Subsidy',
    'PAYER-MOHCC',
    'GOVERNMENT',
    'DRAFT',
    500.00,
    'USD',
    CURRENT_DATE,
    '{"eligibility":"recognised health worker (VARAPI licensed provider or Vashandi workforce)","enrolment":"self opt-in with server-side recognition check","suspension":"automatic when licence lapses or employment ends","doctrine":"administrative_financial_only_never_triage_priority"}'::jsonb
)
ON CONFLICT (tenant_id, program_code) DO NOTHING;
