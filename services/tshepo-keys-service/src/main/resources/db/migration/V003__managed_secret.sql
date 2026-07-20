-- W4a: managed-secret custody. tshepo-keys becomes the custody home for named
-- secrets (e.g. de-id dataset HMAC secrets, integration credentials) so callers
-- reference a secret by ref and never hold raw material in their own config/env.
-- The value is AES-256-GCM encrypted at rest with the service KEK (same custody
-- posture as signing keys: [12-byte IV | ciphertext | GCM tag]). Resolve is
-- INTERNAL-only and audited.
CREATE TABLE IF NOT EXISTS tshepo_keys.managed_secret (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    secret_ref       VARCHAR(128) NOT NULL,
    purpose          VARCHAR(64),
    value_encrypted  BYTEA       NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at       TIMESTAMPTZ,
    CONSTRAINT uq_managed_secret_ref UNIQUE (tenant_id, secret_ref)
);

CREATE INDEX IF NOT EXISTS idx_managed_secret_lookup
    ON tshepo_keys.managed_secret (tenant_id, secret_ref, status);
