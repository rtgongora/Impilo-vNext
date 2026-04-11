-- ============================================================================
-- V38: Policy consent tracking
--
-- Records user acceptance of Privacy Policy and Terms of Use.
-- Supports versioned policies so that when a policy is updated,
-- all users can be prompted to re-accept.
-- ============================================================================

CREATE TABLE policy_consent (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    policy_type     VARCHAR(50)  NOT NULL,   -- PRIVACY_POLICY | TERMS_OF_USE
    policy_version  VARCHAR(20)  NOT NULL,   -- e.g. "2026-04-11"
    accepted        BOOLEAN      NOT NULL DEFAULT FALSE,
    accepted_at     TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_policy_consent UNIQUE (tenant_id, user_id, policy_type, policy_version)
);

CREATE INDEX idx_policy_consent_user ON policy_consent (tenant_id, user_id);
CREATE INDEX idx_policy_consent_type ON policy_consent (tenant_id, policy_type, policy_version);

-- ============================================================================
-- Account deletion request tracking
-- ============================================================================

CREATE TABLE account_deletion_request (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | CANCELLED
    reason          TEXT,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deletion_request_user ON account_deletion_request (tenant_id, user_id);
