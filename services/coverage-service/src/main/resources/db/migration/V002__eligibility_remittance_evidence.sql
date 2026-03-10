-- ============================================================================
-- coverage-service V002 — Eligibility checks, remittance, decision evidence
-- Adds: cv_eligibility_checks, cv_remittances
-- Alters: cv_preauth_requests (add decision_evidence_json)
-- ============================================================================

-- Eligibility Checks — Class B bounded-stale allowed
CREATE TABLE cv_eligibility_checks (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    pod_id                  VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id             UUID            NOT NULL REFERENCES cv_member_coverage(id),
    patient_ref             VARCHAR(255)    NOT NULL,
    service_code            VARCHAR(64),
    result_code             VARCHAR(32)     NOT NULL,     -- ELIGIBLE, INELIGIBLE, PENDING, ERROR
    result_message          TEXT,
    decision_evidence_json  JSONB,                         -- staleness evidence per v1.1 Law 6
    checked_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_elig_tenant ON cv_eligibility_checks (tenant_id);
CREATE INDEX idx_cv_elig_coverage ON cv_eligibility_checks (coverage_id);
CREATE INDEX idx_cv_elig_patient ON cv_eligibility_checks (patient_ref);

-- Remittances — provider payout records
CREATE TABLE cv_remittances (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    payer_id            VARCHAR(255)    NOT NULL,
    provider_ref        VARCHAR(255)    NOT NULL,
    amount              NUMERIC(12,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',  -- PENDING, ISSUED, RECONCILED, FAILED
    reference_number    VARCHAR(128),
    line_items          JSONB,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_remit_tenant ON cv_remittances (tenant_id);
CREATE INDEX idx_cv_remit_payer ON cv_remittances (payer_id);
CREATE INDEX idx_cv_remit_provider ON cv_remittances (provider_ref);
CREATE INDEX idx_cv_remit_status ON cv_remittances (status);

-- Add decision_evidence_json to preauth requests (staleness evidence)
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS decision_evidence_json JSONB;
