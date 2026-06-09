-- First-class subsidy programmes (government / donor / facility assistance rails).

CREATE TABLE cv_subsidy_programs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    program_code    VARCHAR(64) NOT NULL,
    program_name    VARCHAR(255) NOT NULL,
    payer_id        VARCHAR(255),
    subsidy_type    VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    annual_cap      NUMERIC(14,2),
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    rules_json      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_subsidy_program_code UNIQUE (tenant_id, program_code)
);

CREATE INDEX idx_cv_subsidy_tenant ON cv_subsidy_programs (tenant_id, status);

INSERT INTO cv_subsidy_programs (
    id, tenant_id, pod_id, program_code, program_name, payer_id, subsidy_type, status, annual_cap, currency, effective_from, rules_json
) VALUES
(
    'dddddddd-0001-0000-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'SUB-MOHCC-PRIMARY',
    'MOHCC Primary Care Subsidy',
    'PAYER-MOHCC',
    'GOVERNMENT',
    'ACTIVE',
    1500.00,
    'USD',
    CURRENT_DATE,
    '{"eligible_benefits":["OUTPATIENT","PHARMACY"],"copay_waiver_percent":100}'::jsonb
),
(
    'dddddddd-0001-0000-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'national-spine',
    'SUB-DONOR-MATERNAL',
    'Donor Maternal Health Subsidy',
    'PAYER-DONOR-UNICEF',
    'DONOR',
    'ACTIVE',
    3000.00,
    'USD',
    CURRENT_DATE,
    '{"eligible_benefits":["MATERNITY","DELIVERY"],"requires_programme_enrolment":true}'::jsonb
)
ON CONFLICT (tenant_id, program_code) DO NOTHING;
