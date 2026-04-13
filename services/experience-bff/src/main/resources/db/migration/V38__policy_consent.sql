-- ============================================================================
-- V38: Digital consent pipeline — multi-channel policy consent tracking
--
-- Records user acceptance of Privacy Policy and Terms of Use across all
-- device types and access channels: WEB, APP, SMS, USSD, KIOSK, VOICE,
-- PAPER, OPERATOR.
--
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
    -- Multi-channel consent pipeline fields
    consent_channel VARCHAR(20)  NOT NULL DEFAULT 'WEB',  -- WEB | APP | SMS | USSD | KIOSK | VOICE | PAPER | OPERATOR
    device_type     VARCHAR(30),                          -- SMARTPHONE | FEATURE_PHONE | DESKTOP | TABLET | KIOSK_TERMINAL | OTHER
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_policy_consent UNIQUE (tenant_id, user_id, policy_type, policy_version)
);

CREATE INDEX idx_policy_consent_user ON policy_consent (tenant_id, user_id);
CREATE INDEX idx_policy_consent_type ON policy_consent (tenant_id, policy_type, policy_version);
CREATE INDEX idx_policy_consent_channel ON policy_consent (consent_channel);

-- ============================================================================
-- Consent request tracking — outbound consent solicitations
--
-- Tracks consent requests sent to users via SMS, USSD, or other channels.
-- When a user responds (e.g. SMS reply YES), the corresponding
-- policy_consent row is created and this request is marked COMPLETED.
-- ============================================================================

CREATE TABLE consent_request (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255),                           -- NULL if user not yet resolved
    phone_number    VARCHAR(20),                            -- For SMS/USSD-based requests
    policy_version  VARCHAR(20)  NOT NULL,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'SMS',    -- SMS | USSD | VOICE | EMAIL
    language        VARCHAR(10)  NOT NULL DEFAULT 'en',     -- en | sn | nd
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | COMPLETED | EXPIRED | FAILED
    sent_at         TIMESTAMPTZ,
    responded_at    TIMESTAMPTZ,
    response        VARCHAR(50),                            -- Raw reply text (YES, NO, 1, 2, etc.)
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consent_request_phone ON consent_request (phone_number);
CREATE INDEX idx_consent_request_user ON consent_request (tenant_id, user_id);
CREATE INDEX idx_consent_request_status ON consent_request (status) WHERE status = 'PENDING';

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
