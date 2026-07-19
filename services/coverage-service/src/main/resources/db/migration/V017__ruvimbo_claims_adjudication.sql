-- ============================================================================
-- coverage-service V017 — Ruvimbo Wave 3: Claims v2 + Adjudication + Remittance
--
--   1. cv_claims extended for the 21-status lifecycle + lineage + adjudication   (spec §19)
--   2. cv_claim_lines — line-level adjudication (independent per-line outcomes)   (spec §19.5)
--   3. cv_claim_events — status/lineage audit trail                               (spec §7/§40)
--   4. cv_remittances linked to claims (settlement instruction)                   (spec §20-§21)
--
-- Compatibility-first: legacy cv_claims columns + the existing submit/adjudicate
-- path are untouched; new columns are additive. COSTA continues to file into
-- cv_claims (POST /internal/v1/coverage/claims) — cv_claims stays the canonical claim.
-- ============================================================================

-- Widen status for the 21-value lifecycle (spec §19.4).
ALTER TABLE cv_claims ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS claim_number          VARCHAR(64);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS member_cpid           VARCHAR(255);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS plan_version_id       UUID;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS authorisation_id      UUID;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS referral_id           UUID;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS allowed_amount        NUMERIC(14,2);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS denied_amount         NUMERIC(14,2);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS patient_responsibility NUMERIC(14,2);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS reason_codes          JSONB;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS ruleset_version       VARCHAR(32);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS scrub_result          JSONB;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS replaces_claim_id     UUID;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS replaced_by_claim_id  UUID;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS reversed              BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS settlement_ref        VARCHAR(128);
ALTER TABLE cv_claims ADD COLUMN IF NOT EXISTS currency              VARCHAR(8) NOT NULL DEFAULT 'USD';

CREATE INDEX IF NOT EXISTS idx_cv_claims_member ON cv_claims (tenant_id, member_cpid);
CREATE INDEX IF NOT EXISTS idx_cv_claims_status ON cv_claims (tenant_id, status);

-- Line-level claim detail (spec §19.5) — each line adjudicated independently.
CREATE TABLE cv_claim_lines (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    claim_id            UUID            NOT NULL REFERENCES cv_claims(id),
    line_number         INT             NOT NULL DEFAULT 1,
    benefit_code        VARCHAR(64)     NOT NULL,
    service_code        VARCHAR(64),
    description         VARCHAR(255),
    quantity            NUMERIC(14,2)   NOT NULL DEFAULT 1,
    submitted_amount    NUMERIC(14,2)   NOT NULL,
    allowed_amount      NUMERIC(14,2),
    approved_amount     NUMERIC(14,2),
    denied_amount       NUMERIC(14,2),
    copay               NUMERIC(14,2)   NOT NULL DEFAULT 0,
    coinsurance         NUMERIC(14,2)   NOT NULL DEFAULT 0,
    patient_responsibility NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_status         VARCHAR(24)     NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED, APPROVED, PARTIALLY_APPROVED, DENIED, DUPLICATE, NOT_SEPARATELY_PAYABLE
    reason_codes        JSONB,
    authorisation_line_id UUID,
    consume_ref         VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_claim_lines_claim ON cv_claim_lines (claim_id);

-- Claim event / lineage audit trail (spec §7 §40).
CREATE TABLE cv_claim_events (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    claim_id            UUID            NOT NULL REFERENCES cv_claims(id),
    event_type          VARCHAR(48)     NOT NULL,
    from_status         VARCHAR(32),
    to_status           VARCHAR(32),
    actor               VARCHAR(128),
    organisation        VARCHAR(128),
    reason              VARCHAR(255),
    correlation_id      UUID,
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cv_claim_events_claim ON cv_claim_events (claim_id, occurred_at);

-- Link remittances to the claim they settle (settlement instruction, spec §21).
ALTER TABLE cv_remittances ADD COLUMN IF NOT EXISTS claim_id UUID;
ALTER TABLE cv_remittances ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS idx_cv_remittances_claim ON cv_remittances (claim_id);
