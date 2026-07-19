-- ============================================================================
-- coverage-service V015 — Ruvimbo Coverage Foundation (spec §42 Wave 1)
--
-- Adds the foundation the flat plan model lacked:
--   1. Payer Registry (cv_payers)                                    (spec §8)
--   2. Scheme -> Product -> PlanVersion hierarchy (immutable pub)    (spec §9)
--   3. Membership state machine + verification + dependants          (spec §10)
--   4. Benefits, limits, reservation-aware accumulators              (spec §13)
--   5. Eligibility v2 fields (reason codes / ruleset ver / validity) (spec §12)
--
-- Compatibility-first: existing tables are extended, never replaced. The flat
-- cv_coverage_plans row stays the live "active projection" (all existing reads
-- keep working); new hierarchy rows LINK to it. The G3 subsidy dual-lane is
-- untouched.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Payer Registry (spec §8)
-- ---------------------------------------------------------------------------
CREATE TABLE cv_payers (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    payer_code          VARCHAR(64)     NOT NULL,
    legal_name          VARCHAR(255)    NOT NULL,
    trading_name        VARCHAR(255),
    organisation_type   VARCHAR(48)     NOT NULL DEFAULT 'MEDICAL_AID',
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, TERMINATED
    contacts_json       JSONB,
    integration_method  VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',  -- MANUAL, API, FILE
    settlement_reference VARCHAR(128),
    regulatory_status   VARCHAR(64),
    source_authority    VARCHAR(128),
    approved_by         VARCHAR(128),
    approved_at         TIMESTAMPTZ,
    effective_from      DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to        DATE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_payer_code UNIQUE (tenant_id, payer_code),
    CONSTRAINT ck_cv_payer_status CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_cv_payer_integration CHECK (integration_method IN ('MANUAL','API','FILE'))
);
CREATE INDEX idx_cv_payers_tenant ON cv_payers (tenant_id);
CREATE INDEX idx_cv_payers_status ON cv_payers (tenant_id, status);

-- ---------------------------------------------------------------------------
-- 2. Scheme -> Product -> PlanVersion (spec §9). Published plan versions are
--    immutable (enforced in the service layer; a correction creates a new
--    version). coverage_type CHECK carries the canonical 15-value enum (§5).
-- ---------------------------------------------------------------------------
CREATE TABLE cv_schemes (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    payer_ref           UUID            NOT NULL REFERENCES cv_payers(id),
    scheme_code         VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    coverage_type       VARCHAR(32)     NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    effective_from      DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to        DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_scheme_code UNIQUE (tenant_id, scheme_code),
    CONSTRAINT ck_cv_scheme_coverage_type CHECK (coverage_type IN (
        'SELF_PAY','MEDICAL_AID','COMMERCIAL_INSURANCE','EMPLOYER_SPONSORED',
        'GOVERNMENT_PROGRAMME','SOCIAL_PROTECTION','DONOR_PROGRAMME','FACILITY_WAIVER',
        'OCCUPATIONAL_INJURY','MOTOR_VEHICLE_ACCIDENT','TRAVEL_INSURANCE','CAPITATED_CARE',
        'CONTRACTED_PACKAGE','EMERGENCY_PROVISIONAL','OTHER'))
);
CREATE INDEX idx_cv_schemes_payer ON cv_schemes (payer_ref);

CREATE TABLE cv_products (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    scheme_ref          UUID            NOT NULL REFERENCES cv_schemes(id),
    product_code        VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_product_code UNIQUE (tenant_id, product_code)
);
CREATE INDEX idx_cv_products_scheme ON cv_products (scheme_ref);

CREATE TABLE cv_plan_versions (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    pod_id                      VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    product_ref                 UUID            NOT NULL REFERENCES cv_products(id),
    version_number              INT             NOT NULL DEFAULT 1,
    status                      VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT, PUBLISHED, RETIRED
    effective_from              DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to                DATE,
    currency                    VARCHAR(8)      NOT NULL DEFAULT 'USD',
    rules_json                  JSONB,
    claims_submission_deadline_days INT         NOT NULL DEFAULT 90,
    appeal_deadline_days        INT             NOT NULL DEFAULT 60,
    approval_authority          VARCHAR(128),
    approved_at                 TIMESTAMPTZ,
    published_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_plan_version UNIQUE (product_ref, version_number),
    CONSTRAINT ck_cv_plan_version_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED'))
);
CREATE INDEX idx_cv_plan_versions_product ON cv_plan_versions (product_ref);

-- Link the flat live plan to its hierarchy (nullable, backfilled below).
ALTER TABLE cv_coverage_plans ADD COLUMN IF NOT EXISTS payer_ref UUID REFERENCES cv_payers(id);
ALTER TABLE cv_coverage_plans ADD COLUMN IF NOT EXISTS plan_version_id UUID REFERENCES cv_plan_versions(id);

-- ---------------------------------------------------------------------------
-- 3. Membership state machine + verification + dependants (spec §10)
--    status widened for the 13-value machine; verification kept SEPARATE (§10.2).
-- ---------------------------------------------------------------------------
ALTER TABLE cv_member_coverage ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS verification_status VARCHAR(24) NOT NULL DEFAULT 'DECLARED';
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS verification_date   TIMESTAMPTZ;
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS verification_source VARCHAR(64);
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS coverage_priority   INT NOT NULL DEFAULT 1;
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS member_category     VARCHAR(48);
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS principal_member_id UUID REFERENCES cv_member_coverage(id);
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS termination_reason  VARCHAR(255);
ALTER TABLE cv_member_coverage ADD COLUMN IF NOT EXISTS card_reference      VARCHAR(64);

ALTER TABLE cv_member_coverage ADD CONSTRAINT ck_cv_member_status CHECK (status IN (
    'DRAFT','DECLARED','PENDING_VERIFICATION','VERIFIED','ACTIVE','SUSPENDED',
    'GRACE_PERIOD','EXPIRED','TERMINATED','CANCELLED','DISPUTED','MERGED','ENTERED_IN_ERROR'));
ALTER TABLE cv_member_coverage ADD CONSTRAINT ck_cv_member_verification CHECK (verification_status IN (
    'DECLARED','PENDING_VERIFICATION','VERIFIED','PROVISIONAL','DISPUTED','UNVERIFIED'));

-- Duplicate-active-enrolment guard (fixes the completion-lens nit: a member
-- could be enrolled twice on the same plan). One active membership per
-- (tenant, client, plan). Dependants sit on their own client_id.
CREATE UNIQUE INDEX uq_cv_member_active_plan ON cv_member_coverage (tenant_id, client_id, plan_id)
    WHERE status IN ('DRAFT','DECLARED','PENDING_VERIFICATION','VERIFIED','ACTIVE','GRACE_PERIOD');
CREATE INDEX idx_cv_member_principal ON cv_member_coverage (principal_member_id);

-- ---------------------------------------------------------------------------
-- 4. Benefits, limits, reservation-aware accumulators (spec §13)
--    Accumulators/movements model the proven subsidy ledger (V010/V011):
--    optimistic version + idempotent-by-reference movements.
-- ---------------------------------------------------------------------------
CREATE TABLE cv_benefit_definitions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    plan_version_id     UUID            NOT NULL REFERENCES cv_plan_versions(id),
    benefit_code        VARCHAR(64)     NOT NULL,
    category            VARCHAR(48)     NOT NULL,
    description         VARCHAR(255),
    coverage_percent    NUMERIC(5,2)    NOT NULL DEFAULT 100.00,
    copay_amount        NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    deductible_amount   NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    requires_authorisation BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_referral   BOOLEAN         NOT NULL DEFAULT FALSE,
    network_requirement VARCHAR(24)     NOT NULL DEFAULT 'ANY',  -- ANY, IN_NETWORK, PREFERRED
    zibo_code_system    VARCHAR(64),
    zibo_code           VARCHAR(64),
    currency            VARCHAR(8)      NOT NULL DEFAULT 'USD',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_benefit_def UNIQUE (plan_version_id, benefit_code)
);
CREATE INDEX idx_cv_benefit_def_pv ON cv_benefit_definitions (plan_version_id);

CREATE TABLE cv_benefit_limits (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    benefit_id          UUID            NOT NULL REFERENCES cv_benefit_definitions(id),
    limit_type          VARCHAR(24)     NOT NULL,  -- MONETARY, QUANTITY, VISIT, DAY, FREQUENCY, EPISODE, LIFETIME, ANNUAL, NONE
    limit_value         NUMERIC(14,2),
    limit_period        VARCHAR(24)     NOT NULL DEFAULT 'CALENDAR_YEAR',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_benefit_limits_benefit ON cv_benefit_limits (benefit_id);

-- Reservation-aware accumulator: used + reserved (§13). remaining is derived.
CREATE TABLE cv_benefit_accumulators (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    member_id           UUID            NOT NULL REFERENCES cv_member_coverage(id),
    plan_version_id     UUID            NOT NULL REFERENCES cv_plan_versions(id),
    benefit_code        VARCHAR(64)     NOT NULL,
    period_key          VARCHAR(16)     NOT NULL,   -- e.g. '2026' for CALENDAR_YEAR
    limit_amount        NUMERIC(14,2),              -- snapshot of the monetary limit (null = unlimited)
    used_amount         NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    reserved_amount     NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    currency            VARCHAR(8)      NOT NULL DEFAULT 'USD',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_accum UNIQUE (member_id, benefit_code, period_key)
);
CREATE INDEX idx_cv_accum_member ON cv_benefit_accumulators (member_id);

-- Movement ledger — idempotent reserve / consume / release by unique reference.
CREATE TABLE cv_benefit_movements (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    accumulator_id      UUID            NOT NULL REFERENCES cv_benefit_accumulators(id),
    movement_type       VARCHAR(16)     NOT NULL,  -- RESERVE, CONSUME, RELEASE
    amount              NUMERIC(14,2)   NOT NULL,
    reference           VARCHAR(128)    NOT NULL,
    used_after          NUMERIC(14,2)   NOT NULL,
    reserved_after      NUMERIC(14,2)   NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_movement_ref UNIQUE (accumulator_id, reference),
    CONSTRAINT ck_cv_movement_type CHECK (movement_type IN ('RESERVE','CONSUME','RELEASE'))
);
CREATE INDEX idx_cv_movements_accum ON cv_benefit_movements (accumulator_id);

-- ---------------------------------------------------------------------------
-- 5. Eligibility v2 explainability fields (spec §12.2)
-- ---------------------------------------------------------------------------
ALTER TABLE cv_eligibility_checks ADD COLUMN IF NOT EXISTS reason_codes    JSONB;
ALTER TABLE cv_eligibility_checks ADD COLUMN IF NOT EXISTS ruleset_version VARCHAR(32);
ALTER TABLE cv_eligibility_checks ADD COLUMN IF NOT EXISTS valid_until     TIMESTAMPTZ;
ALTER TABLE cv_eligibility_checks ADD COLUMN IF NOT EXISTS network_status  VARCHAR(24);
ALTER TABLE cv_eligibility_checks ADD COLUMN IF NOT EXISTS plan_version_id UUID;

-- ============================================================================
-- BACKFILL — give the two governed seeded plans (V008) a real hierarchy so
-- every existing read keeps working and the foundation has live data.
-- Deterministic UUIDs so proofs/seeds are stable.
-- ============================================================================
DO $$
DECLARE
    t_id UUID := '00000000-0000-4000-8000-000000000001';
BEGIN
    -- Payers
    INSERT INTO cv_payers (id, tenant_id, payer_code, legal_name, trading_name, organisation_type,
                           status, integration_method, source_authority, approved_by, approved_at)
    VALUES
      ('d1000000-0000-4000-8000-000000000001', t_id, 'PAYER-MOHCC',
       'Ministry of Health and Child Care', 'MOHCC', 'GOVERNMENT_PROGRAMME',
       'ACTIVE', 'MANUAL', 'V015 governed backfill', 'system', NOW()),
      ('d1000000-0000-4000-8000-000000000002', t_id, 'PAYER-PRIVATE-01',
       'Private Medical Aid Society', 'PrivateMed', 'MEDICAL_AID',
       'ACTIVE', 'MANUAL', 'V015 governed backfill', 'system', NOW())
    ON CONFLICT (tenant_id, payer_code) DO NOTHING;

    -- Schemes
    INSERT INTO cv_schemes (id, tenant_id, payer_ref, scheme_code, name, coverage_type, status)
    VALUES
      ('d2000000-0000-4000-8000-000000000001', t_id, 'd1000000-0000-4000-8000-000000000001',
       'SCH-MOHCC-NATIONAL', 'MOHCC National Health Scheme', 'GOVERNMENT_PROGRAMME', 'ACTIVE'),
      ('d2000000-0000-4000-8000-000000000002', t_id, 'd1000000-0000-4000-8000-000000000002',
       'SCH-PRIVATE-PLUS', 'Private Medical Aid Plus Scheme', 'MEDICAL_AID', 'ACTIVE')
    ON CONFLICT (tenant_id, scheme_code) DO NOTHING;

    -- Products
    INSERT INTO cv_products (id, tenant_id, scheme_ref, product_code, name, status)
    VALUES
      ('d3000000-0000-4000-8000-000000000001', t_id, 'd2000000-0000-4000-8000-000000000001',
       'PRD-MOHCC-CORE', 'MOHCC National Core', 'ACTIVE'),
      ('d3000000-0000-4000-8000-000000000002', t_id, 'd2000000-0000-4000-8000-000000000002',
       'PRD-PRIVATE-PLUS', 'Private Medical Aid Plus', 'ACTIVE')
    ON CONFLICT (tenant_id, product_code) DO NOTHING;

    -- Plan versions (v1, PUBLISHED — immutable)
    INSERT INTO cv_plan_versions (id, tenant_id, product_ref, version_number, status,
                                  currency, approval_authority, approved_at, published_at)
    VALUES
      ('d4000000-0000-4000-8000-000000000001', t_id, 'd3000000-0000-4000-8000-000000000001',
       1, 'PUBLISHED', 'USD', 'MOHCC Health Financing', NOW(), NOW()),
      ('d4000000-0000-4000-8000-000000000002', t_id, 'd3000000-0000-4000-8000-000000000002',
       1, 'PUBLISHED', 'USD', 'Private Medical Aid Society', NOW(), NOW())
    ON CONFLICT (product_ref, version_number) DO NOTHING;

    -- Link the flat live plans to their hierarchy.
    UPDATE cv_coverage_plans SET payer_ref = 'd1000000-0000-4000-8000-000000000001',
        plan_version_id = 'd4000000-0000-4000-8000-000000000001'
        WHERE tenant_id = t_id AND plan_code = 'COV-MOHCC-CORE';
    UPDATE cv_coverage_plans SET payer_ref = 'd1000000-0000-4000-8000-000000000002',
        plan_version_id = 'd4000000-0000-4000-8000-000000000002'
        WHERE tenant_id = t_id AND plan_code = 'COV-PRIVATE-PLUS';

    -- Benefits aligned to COSTA AHFOZ-indicative tariffs so eligibility/estimates
    -- carry real numbers. (GP consult, FBC, chest X-ray, maternity, chronic meds.)
    INSERT INTO cv_benefit_definitions (id, tenant_id, plan_version_id, benefit_code, category,
        description, coverage_percent, copay_amount, requires_authorisation, requires_referral, network_requirement)
    VALUES
      -- MOHCC National Core (fully covered public benefits)
      ('d5000000-0001-4000-8000-000000000001', t_id, 'd4000000-0000-4000-8000-000000000001',
       'GP_CONSULT', 'PROFESSIONAL_FEE', 'General practitioner consultation', 100.00, 0.00, FALSE, FALSE, 'ANY'),
      ('d5000000-0001-4000-8000-000000000002', t_id, 'd4000000-0000-4000-8000-000000000001',
       'LAB_FBC', 'LABORATORY', 'Full blood count', 100.00, 0.00, FALSE, FALSE, 'ANY'),
      ('d5000000-0001-4000-8000-000000000003', t_id, 'd4000000-0000-4000-8000-000000000001',
       'IMG_CXR', 'IMAGING', 'Chest X-ray', 100.00, 0.00, TRUE, FALSE, 'ANY'),
      ('d5000000-0001-4000-8000-000000000004', t_id, 'd4000000-0000-4000-8000-000000000001',
       'MATERNITY', 'MATERNAL_NEWBORN', 'Maternity package', 100.00, 0.00, TRUE, FALSE, 'ANY'),
      ('d5000000-0001-4000-8000-000000000005', t_id, 'd4000000-0000-4000-8000-000000000001',
       'CHRONIC_MEDS', 'MEDICINE', 'Chronic disease medicines', 100.00, 0.00, FALSE, FALSE, 'ANY'),
      -- Private Plus (co-pays + auth requirements)
      ('d5000000-0002-4000-8000-000000000001', t_id, 'd4000000-0000-4000-8000-000000000002',
       'GP_CONSULT', 'PROFESSIONAL_FEE', 'General practitioner consultation', 80.00, 5.00, FALSE, FALSE, 'IN_NETWORK'),
      ('d5000000-0002-4000-8000-000000000002', t_id, 'd4000000-0000-4000-8000-000000000002',
       'LAB_FBC', 'LABORATORY', 'Full blood count', 80.00, 2.00, FALSE, FALSE, 'IN_NETWORK'),
      ('d5000000-0002-4000-8000-000000000003', t_id, 'd4000000-0000-4000-8000-000000000002',
       'IMG_CXR', 'IMAGING', 'Chest X-ray', 80.00, 10.00, TRUE, TRUE, 'IN_NETWORK'),
      ('d5000000-0002-4000-8000-000000000004', t_id, 'd4000000-0000-4000-8000-000000000002',
       'MATERNITY', 'MATERNAL_NEWBORN', 'Maternity package', 90.00, 0.00, TRUE, FALSE, 'IN_NETWORK'),
      ('d5000000-0002-4000-8000-000000000005', t_id, 'd4000000-0000-4000-8000-000000000002',
       'CHRONIC_MEDS', 'MEDICINE', 'Chronic disease medicines', 80.00, 3.00, FALSE, FALSE, 'ANY')
    ON CONFLICT (plan_version_id, benefit_code) DO NOTHING;

    -- Annual monetary limits per benefit.
    INSERT INTO cv_benefit_limits (tenant_id, benefit_id, limit_type, limit_value, limit_period)
    SELECT t_id, id, 'MONETARY',
        CASE category
            WHEN 'PROFESSIONAL_FEE' THEN 500.00
            WHEN 'LABORATORY' THEN 1000.00
            WHEN 'IMAGING' THEN 1500.00
            WHEN 'MATERNAL_NEWBORN' THEN 3000.00
            WHEN 'MEDICINE' THEN 2000.00
            ELSE 1000.00 END,
        'CALENDAR_YEAR'
    FROM cv_benefit_definitions
    WHERE tenant_id = t_id AND plan_version_id IN
        ('d4000000-0000-4000-8000-000000000001','d4000000-0000-4000-8000-000000000002')
    ON CONFLICT DO NOTHING;
END $$;
