-- ============================================================================
-- coverage-service V018 — Ruvimbo Wave 4: Advanced Financing Operations
--
--   1. Coordination of Benefits (cv_cob_decisions)                   (spec §15)
--   2. Employer/group admin (cv_employers + roster stage/apply)      (spec §24)
--   3. Fraud, waste & abuse flags (cv_fraud_flags)                   (spec §25)
--   4. Capitation encounter reports (cv_capitation_reports)          (spec §5,§19)
--   5. Claims Switch / Payer Gateway transaction log                 (spec §5,§32)
-- ============================================================================

-- 1. Coordination of Benefits — persisted payment waterfall (spec §15).
CREATE TABLE cv_cob_decisions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    member_cpid         VARCHAR(255)    NOT NULL,
    benefit_code        VARCHAR(64),
    standard_charge     NUMERIC(14,2)   NOT NULL,
    allowed_amount      NUMERIC(14,2)   NOT NULL,
    waterfall           JSONB           NOT NULL,       -- ordered [{coverageId, payer, priority, paid}]
    total_payer_paid    NUMERIC(14,2)   NOT NULL,
    patient_responsibility NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(8)      NOT NULL DEFAULT 'USD',
    ruleset_version     VARCHAR(32),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_cob_member ON cv_cob_decisions (tenant_id, member_cpid);

-- 2. Employer / group administration (spec §24). Rosters use stage->validate->apply.
CREATE TABLE cv_employers (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    employer_code       VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    contract_number     VARCHAR(128),
    payer_ref           UUID,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    effective_from      DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to        DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_employer_code UNIQUE (tenant_id, employer_code)
);
CREATE INDEX idx_cv_employers_tenant ON cv_employers (tenant_id);

CREATE TABLE cv_employer_roster_batches (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    employer_id         UUID            NOT NULL REFERENCES cv_employers(id),
    plan_id             UUID            NOT NULL,        -- target cv_coverage_plans plan
    status              VARCHAR(16)     NOT NULL DEFAULT 'STAGED',  -- STAGED, VALIDATED, APPLIED, REJECTED
    total_rows          INT             NOT NULL DEFAULT 0,
    valid_rows          INT             NOT NULL DEFAULT 0,
    invalid_rows        INT             NOT NULL DEFAULT 0,
    applied_rows        INT             NOT NULL DEFAULT 0,
    uploaded_by         VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_roster_batch_employer ON cv_employer_roster_batches (employer_id);

CREATE TABLE cv_employer_roster_rows (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    batch_id            UUID            NOT NULL REFERENCES cv_employer_roster_batches(id),
    client_id           VARCHAR(255)    NOT NULL,
    member_number       VARCHAR(64),
    relationship        VARCHAR(32)     NOT NULL DEFAULT 'SELF',
    validation_status   VARCHAR(16)     NOT NULL DEFAULT 'PENDING',  -- PENDING, VALID, INVALID, APPLIED
    validation_error    VARCHAR(255),
    applied_membership_id UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_roster_rows_batch ON cv_employer_roster_rows (batch_id);

-- 3. Fraud, waste & abuse flags (spec §25). Automated flags -> governed review.
CREATE TABLE cv_fraud_flags (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_type        VARCHAR(32)     NOT NULL,       -- CLAIM, MEMBER, PROVIDER
    subject_ref         VARCHAR(255)    NOT NULL,
    flag_type           VARCHAR(48)     NOT NULL,       -- DUPLICATE_CLAIM, CLAIM_AFTER_TERMINATION, IMPOSSIBLE_AMOUNT, ...
    severity            VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM',
    detail              JSONB,
    status              VARCHAR(16)     NOT NULL DEFAULT 'OPEN',  -- OPEN, REVIEWING, CONFIRMED, DISMISSED
    reviewer_id         VARCHAR(128),
    resolution          VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_fraud_subject ON cv_fraud_flags (tenant_id, subject_type, subject_ref);
CREATE INDEX idx_cv_fraud_status ON cv_fraud_flags (tenant_id, status);

-- 4. Capitation encounter reports (spec §5, §19).
CREATE TABLE cv_capitation_reports (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    provider_id         VARCHAR(128)    NOT NULL,
    facility_id         VARCHAR(128),
    payer_id            VARCHAR(255),
    period_start        DATE            NOT NULL,
    period_end          DATE            NOT NULL,
    member_count        INT             NOT NULL DEFAULT 0,
    encounter_count     INT             NOT NULL DEFAULT 0,
    capitation_amount   NUMERIC(14,2)   NOT NULL DEFAULT 0,
    currency            VARCHAR(8)      NOT NULL DEFAULT 'USD',
    status              VARCHAR(16)     NOT NULL DEFAULT 'SUBMITTED',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_capitation_provider ON cv_capitation_reports (tenant_id, provider_id);

-- 5. Claims Switch / Payer Gateway transaction log (spec §5, §32). Switching != adjudication;
--    technical ack != business ack != approval. External rails are credential-gated.
CREATE TABLE cv_gateway_transactions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    switch_control_number VARCHAR(64)   NOT NULL,
    transaction_type    VARCHAR(48)     NOT NULL,       -- CLAIM_SUBMISSION, ELIGIBILITY_INQUIRY, AUTH_REQUEST, ...
    sender_ref          VARCHAR(128),
    receiver_payer      VARCHAR(255),
    route               VARCHAR(24)     NOT NULL,       -- INTERNAL_ADJUDICATION, EXTERNAL_MANUAL, EXTERNAL_API
    technical_ack       VARCHAR(16),                    -- ACCEPTED, REJECTED
    business_ack        VARCHAR(16),                    -- ACCEPTED, REJECTED, CORRECTION_REQUESTED, PENDING
    status              VARCHAR(24)     NOT NULL DEFAULT 'RECEIVED',  -- RECEIVED, ROUTED, ACKED, EXCEPTION, CLOSED
    original_reference  VARCHAR(128),
    correlation_id      UUID,
    detail              JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_gateway_scn UNIQUE (tenant_id, switch_control_number)
);
CREATE INDEX idx_cv_gateway_status ON cv_gateway_transactions (tenant_id, status);
