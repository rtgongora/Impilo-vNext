-- TSHEPO Keys — Service-to-Service Trust & Key Management: Base schema
-- Ed25519 signing keys, certificate trust store, rotation log, and event outbox.

CREATE SCHEMA IF NOT EXISTS tshepo_keys;

-- ============================================================================
-- Signing Keys — Ed25519 key pairs for JWS token issuance and payload signing
-- ============================================================================
CREATE TABLE tshepo_keys.signing_key (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    key_id               VARCHAR(64)  NOT NULL UNIQUE,
    algorithm            VARCHAR(32)  NOT NULL,
    public_key_pem       TEXT         NOT NULL,
    private_key_encrypted BYTEA       NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rotated_at           TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ
);

CREATE INDEX idx_signing_key_tenant_status ON tshepo_keys.signing_key (tenant_id, status);
CREATE INDEX idx_signing_key_tenant_created ON tshepo_keys.signing_key (tenant_id, created_at DESC);

-- ============================================================================
-- Certificate Trust — CA certificates for mTLS verification
-- ============================================================================
CREATE TABLE tshepo_keys.certificate_trust (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    subject_dn           TEXT         NOT NULL,
    issuer_dn            TEXT         NOT NULL,
    serial_number        VARCHAR(128) NOT NULL,
    fingerprint_sha256   VARCHAR(64)  NOT NULL UNIQUE,
    certificate_pem      TEXT         NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'TRUSTED',
    imported_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ
);

CREATE INDEX idx_cert_trust_tenant_status ON tshepo_keys.certificate_trust (tenant_id, status);
CREATE INDEX idx_cert_trust_fingerprint ON tshepo_keys.certificate_trust (fingerprint_sha256);

-- ============================================================================
-- Key Rotation Log — audit trail for all key rotation events
-- ============================================================================
CREATE TABLE tshepo_keys.key_rotation_log (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    old_key_id           VARCHAR(64),
    new_key_id           VARCHAR(64)  NOT NULL,
    rotated_by           VARCHAR(255) NOT NULL,
    reason               TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_rotation_log_tenant_time ON tshepo_keys.key_rotation_log (tenant_id, created_at DESC);

-- ============================================================================
-- Event Outbox — reliable Kafka publishing (standard Impilo outbox pattern)
-- ============================================================================
CREATE TABLE tshepo_keys.event_outbox (
    id                   BIGSERIAL    PRIMARY KEY,
    aggregate_type       VARCHAR(255) NOT NULL,
    aggregate_id         VARCHAR(255) NOT NULL,
    event_type           VARCHAR(255) NOT NULL,
    payload              JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at         TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON tshepo_keys.event_outbox (created_at ASC) WHERE published_at IS NULL;
