-- =============================================================================
-- Varapi V021 — Provider self-service access request (IATG Journey D).
--
-- A durable, applicant-queryable record for provider-access requests that have
-- no auto-issue path (new provider, organization invitation) or that could not
-- auto-resolve. Every submission persists here with a status and a named next
-- actor; new Provider IDs are never minted directly from a self-service request.
-- =============================================================================

CREATE TABLE IF NOT EXISTS varapi.provider_access_request (
    id                    BIGSERIAL     PRIMARY KEY,
    public_id             VARCHAR(40)   NOT NULL UNIQUE,           -- opaque applicant handle (PAR-...)
    tenant_id             UUID          NOT NULL,
    applicant_health_id   UUID          NOT NULL,                  -- person anchor submitting the request
    request_type          VARCHAR(40)   NOT NULL,                  -- NEW_PROVIDER | ORG_INVITATION | COUNCIL_NUMBER | EC_NUMBER | HAVE_PROVIDER_ID | RECOVER
    status                VARCHAR(40)   NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED | PENDING_*_REVIEW | NEEDS_MORE_INFORMATION | NEEDS_ADJUDICATION | DUPLICATE_SUSPECTED | APPROVED | REJECTED | WITHDRAWN
    next_actor            VARCHAR(80),                             -- role responsible for the next decision
    profession            VARCHAR(120),
    council_code          VARCHAR(40),
    council_number_masked VARCHAR(40),                             -- masked; raw value stays in the council registry
    ec_number_masked      VARCHAR(40),                             -- masked; raw value stays in governance
    organization_ref      VARCHAR(120),
    evidence_summary      VARCHAR(500),
    reason                VARCHAR(500),                            -- why pending / rejected / needs-info
    provider_public_id    VARCHAR(40),                             -- set (masked) only when a Provider ID is linked/issued
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_provider_access_request_applicant
    ON varapi.provider_access_request (tenant_id, applicant_health_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_provider_access_request_status
    ON varapi.provider_access_request (tenant_id, status);

COMMENT ON TABLE varapi.provider_access_request IS
    'IATG Journey D: self-service provider-access requests, submitted by an applicant and queryable by status with a named next actor.';
