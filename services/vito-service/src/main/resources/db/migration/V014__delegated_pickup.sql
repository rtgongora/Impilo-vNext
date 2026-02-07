-- V014: Delegated pickup — OTP/QR-secured delegation for card/ID collection
-- A client can authorize a delegate to pick up their issued ID/card at a facility.

CREATE TABLE IF NOT EXISTS vito.delegated_pickup (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    issuance_request_id   BIGINT       NOT NULL REFERENCES vito.issuance_request(id),
    pickup_token_hash     VARCHAR(128) NOT NULL, -- HMAC of the QR token
    otp_hash              VARCHAR(255) NOT NULL, -- Argon2id of the 6-digit OTP
    delegate_name         VARCHAR(255) NOT NULL,
    delegate_contact      JSONB        NOT NULL DEFAULT '{}', -- {phone, email}
    delegate_id_ref       VARCHAR(100),                       -- national ID ref (not stored plaintext)
    facility_id           UUID         NOT NULL,
    expires_at            TIMESTAMPTZ  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REDEEMED, EXPIRED, REVOKED
    redeemed_at           TIMESTAMPTZ,
    redeemed_by           VARCHAR(255),
    attempts              INTEGER      NOT NULL DEFAULT 0,
    max_attempts          INTEGER      NOT NULL DEFAULT 3,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pickup_token ON vito.delegated_pickup (pickup_token_hash);
CREATE INDEX idx_pickup_tenant_issuance ON vito.delegated_pickup (tenant_id, issuance_request_id);
CREATE INDEX idx_pickup_status ON vito.delegated_pickup (status) WHERE status = 'ACTIVE';
