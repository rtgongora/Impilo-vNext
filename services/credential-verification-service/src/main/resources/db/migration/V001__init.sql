CREATE SCHEMA IF NOT EXISTS credential_verification;

CREATE TABLE credential_verification.credentials (
    id BIGSERIAL PRIMARY KEY,
    credential_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(50) NOT NULL,  -- PROVIDER|CLIENT|FACILITY
    subject_id VARCHAR(255) NOT NULL,
    subject_name VARCHAR(500) NOT NULL,
    credential_type VARCHAR(100) NOT NULL,  -- LICENSE|CERTIFICATE|REGISTRATION|BADGE
    title VARCHAR(500) NOT NULL,
    issued_by VARCHAR(500) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|EXPIRED|REVOKED|SUPERSEDED
    verification_token VARCHAR(64) NOT NULL UNIQUE,
    signature_hex TEXT NOT NULL,
    signed_payload TEXT NOT NULL,
    pdf_document_id UUID,  -- reference to landela-adapter document
    metadata JSONB DEFAULT '{}',
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(255),
    revocation_reason VARCHAR(500),
    superseded_by BIGINT REFERENCES credential_verification.credentials(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_creds_tenant_subject ON credential_verification.credentials(tenant_id, subject_type, subject_id);
CREATE INDEX idx_creds_type ON credential_verification.credentials(credential_type);
CREATE INDEX idx_creds_status ON credential_verification.credentials(status);
CREATE INDEX idx_creds_token ON credential_verification.credentials(verification_token);

CREATE TABLE credential_verification.verification_log (
    id BIGSERIAL PRIMARY KEY,
    credential_id UUID NOT NULL,
    verification_token VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL,  -- VALID|EXPIRED|REVOKED|NOT_FOUND
    verifier_ip VARCHAR(50),
    verifier_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verify_log_cred ON credential_verification.verification_log(credential_id);

CREATE TABLE credential_verification.signing_keys (
    id BIGSERIAL PRIMARY KEY,
    key_id VARCHAR(100) NOT NULL UNIQUE,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'Ed25519',
    public_key_hex TEXT NOT NULL,
    private_key_hex TEXT NOT NULL,  -- encrypted at rest in production
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMPTZ
);

CREATE TABLE credential_verification.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
