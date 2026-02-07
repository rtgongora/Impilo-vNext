CREATE SCHEMA IF NOT EXISTS share_slip;

CREATE TABLE share_slip.share_links (
    id BIGSERIAL PRIMARY KEY,
    link_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    share_token VARCHAR(64) NOT NULL UNIQUE,
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    subject_name VARCHAR(500),
    created_by VARCHAR(255) NOT NULL,
    purpose VARCHAR(500),
    document_ids UUID[] NOT NULL,  -- array of document IDs from landela-adapter
    verification_method VARCHAR(30) NOT NULL DEFAULT 'OTP',  -- OTP|QR_SCAN|DEVICE_REPUTATION
    otp_hash VARCHAR(255),
    otp_attempts INTEGER NOT NULL DEFAULT 0,
    max_claims INTEGER NOT NULL DEFAULT 1,
    claim_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|CLAIMED|EXPIRED|REVOKED
    expires_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    claimed_by VARCHAR(255),
    claimed_device_fingerprint VARCHAR(255),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_share_token ON share_slip.share_links(share_token);
CREATE INDEX idx_share_tenant ON share_slip.share_links(tenant_id, status);
CREATE INDEX idx_share_subject ON share_slip.share_links(subject_type, subject_id);
CREATE INDEX idx_share_expires ON share_slip.share_links(expires_at);

CREATE TABLE share_slip.share_audit (
    id BIGSERIAL PRIMARY KEY,
    link_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,  -- CREATED|OTP_SENT|OTP_VERIFIED|CLAIMED|EXPIRED|REVOKED
    actor_id VARCHAR(255),
    device_fingerprint VARCHAR(255),
    ip_address VARCHAR(50),
    details JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_share_audit_link ON share_slip.share_audit(link_id);

CREATE TABLE share_slip.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
