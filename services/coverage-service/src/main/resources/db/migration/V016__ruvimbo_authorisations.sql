-- ============================================================================
-- coverage-service V016 — Ruvimbo Wave 2: Authorisations & Estimates (spec §16-§18)
--
--   1. Referrals & gatekeeping (cv_referrals)                        (spec §16)
--   2. Full authorisation state machine (cv_authorisations +         (spec §17)
--      cv_authorisation_lines) — 14-status machine, line-level
--   3. Patient-liability estimation (cv_liability_estimates)         (spec §18)
--
-- New model alongside the legacy cv_preauth_requests (kept for compatibility).
-- Approved authorisations reserve benefit via the V015 accumulator ledger.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Referrals & gatekeeping (spec §16)
-- ---------------------------------------------------------------------------
CREATE TABLE cv_referrals (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id         UUID            REFERENCES cv_member_coverage(id),
    member_cpid         VARCHAR(255)    NOT NULL,
    referring_provider_id VARCHAR(128),
    referred_provider_id  VARCHAR(128),
    referred_facility_id  VARCHAR(128),
    clinical_referral_ref VARCHAR(128),          -- link to the originating clinical referral (PCT)
    scope               VARCHAR(128),
    reason              VARCHAR(255),
    permitted_visits    INT             NOT NULL DEFAULT 1,
    visits_used         INT             NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, EXHAUSTED, EXPIRED, CANCELLED, REPLACED
    valid_from          DATE            NOT NULL DEFAULT CURRENT_DATE,
    valid_to            DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cv_referral_status CHECK (status IN ('ACTIVE','EXHAUSTED','EXPIRED','CANCELLED','REPLACED'))
);
CREATE INDEX idx_cv_referrals_member ON cv_referrals (tenant_id, member_cpid);
CREATE INDEX idx_cv_referrals_coverage ON cv_referrals (coverage_id);

-- ---------------------------------------------------------------------------
-- 2. Authorisation state machine (spec §17). 14-status header + line-level.
-- ---------------------------------------------------------------------------
CREATE TABLE cv_authorisations (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id         UUID            NOT NULL REFERENCES cv_member_coverage(id),
    member_cpid         VARCHAR(255)    NOT NULL,
    auth_type           VARCHAR(32)     NOT NULL DEFAULT 'PRIOR',   -- PRIOR, CONCURRENT, EXTENSION, RETROSPECTIVE, EMERGENCY_NOTIFICATION, ADMISSION, PROCEDURE, MEDICINE, DIAGNOSTIC, REHABILITATION, TRANSPORT, CHRONIC
    requesting_provider_id VARCHAR(128),
    facility_id         VARCHAR(128),
    referral_id         UUID            REFERENCES cv_referrals(id),
    diagnosis_system    VARCHAR(64),
    diagnosis_code      VARCHAR(64),
    clinical_justification TEXT,
    urgency             VARCHAR(16)     NOT NULL DEFAULT 'ROUTINE', -- ROUTINE, URGENT, EMERGENCY
    proposed_service_date DATE,
    estimated_amount    NUMERIC(14,2),
    status              VARCHAR(24)     NOT NULL DEFAULT 'DRAFT',
    reviewer_id         VARCHAR(128),
    decision_at         TIMESTAMPTZ,
    validity_from       DATE,
    validity_to         DATE,
    denial_reason       VARCHAR(255),
    information_request TEXT,
    conditions          TEXT,
    correlation_id      UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cv_auth_status CHECK (status IN (
        'DRAFT','SUBMITTED','ACKNOWLEDGED','IN_REVIEW','INFORMATION_REQUESTED','PARTIALLY_APPROVED',
        'APPROVED','DENIED','WITHDRAWN','EXPIRED','USED','CANCELLED','APPEALED','OVERTURNED')),
    CONSTRAINT ck_cv_auth_urgency CHECK (urgency IN ('ROUTINE','URGENT','EMERGENCY'))
);
CREATE INDEX idx_cv_auth_member ON cv_authorisations (tenant_id, member_cpid);
CREATE INDEX idx_cv_auth_coverage ON cv_authorisations (coverage_id);
CREATE INDEX idx_cv_auth_status ON cv_authorisations (tenant_id, status);

CREATE TABLE cv_authorisation_lines (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    authorisation_id    UUID            NOT NULL REFERENCES cv_authorisations(id),
    benefit_code        VARCHAR(64)     NOT NULL,
    service_code        VARCHAR(64),
    description         VARCHAR(255),
    quantity_requested  NUMERIC(14,2)   NOT NULL DEFAULT 1,
    quantity_approved   NUMERIC(14,2)   NOT NULL DEFAULT 0,
    quantity_consumed   NUMERIC(14,2)   NOT NULL DEFAULT 0,
    estimated_amount    NUMERIC(14,2),
    approved_amount     NUMERIC(14,2),
    line_status         VARCHAR(24)     NOT NULL DEFAULT 'REQUESTED',  -- REQUESTED, APPROVED, PARTIALLY_APPROVED, DENIED
    reason              VARCHAR(255),
    reservation_ref     VARCHAR(128),                                  -- benefit-movement idempotency key when reserved
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_auth_lines_auth ON cv_authorisation_lines (authorisation_id);

-- ---------------------------------------------------------------------------
-- 3. Patient-liability estimation (spec §18). Coverage rules over a COSTA price.
-- ---------------------------------------------------------------------------
CREATE TABLE cv_liability_estimates (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id         UUID            NOT NULL REFERENCES cv_member_coverage(id),
    member_cpid         VARCHAR(255)    NOT NULL,
    facility_id         VARCHAR(128),
    benefit_code        VARCHAR(64),
    standard_charge     NUMERIC(14,2)   NOT NULL,      -- supplied by COSTA
    allowed_amount      NUMERIC(14,2)   NOT NULL,
    payer_estimate      NUMERIC(14,2)   NOT NULL,
    deductible          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    copay               NUMERIC(14,2)   NOT NULL DEFAULT 0,
    coinsurance         NUMERIC(14,2)   NOT NULL DEFAULT 0,
    non_covered         NUMERIC(14,2)   NOT NULL DEFAULT 0,
    patient_responsibility NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(8)      NOT NULL DEFAULT 'USD',
    assumptions         TEXT,
    ruleset_version     VARCHAR(32),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cv_liability_nonneg CHECK (patient_responsibility >= 0)
);
CREATE INDEX idx_cv_liability_member ON cv_liability_estimates (tenant_id, member_cpid);
