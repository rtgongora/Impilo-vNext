-- V002: Permit-token replay protection (B3 / G003, G056).
-- Each issued permit token carries a unique nonce. The enforcement (verify) path
-- records the nonce on first successful use; a second presentation of the same nonce
-- is rejected as a replay. Single-use enforcement for sensitive data-access grants.

CREATE TABLE dags.permit_replay (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    nonce       VARCHAR(64)  NOT NULL,
    request_id  BIGINT,
    consumed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_permit_replay_nonce UNIQUE (nonce)
);

CREATE INDEX idx_permit_replay_tenant ON dags.permit_replay (tenant_id);

-- v2 permit tokens bind the full access context and are longer than the original 255 chars.
ALTER TABLE dags.access_requests ALTER COLUMN permit_token TYPE VARCHAR(4096);
