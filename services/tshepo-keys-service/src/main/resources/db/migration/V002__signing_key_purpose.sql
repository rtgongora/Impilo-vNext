-- B0-lite (Wave B): purpose-scoped Tshepo key registry.
-- Adds key purpose + alias + issuer so keys can be bound to a single purpose
-- (step-up, offline-capability, permit, document-signer, future VDHC) rather than
-- sharing one general key. Backfills existing keys to GENERAL.

ALTER TABLE tshepo_keys.signing_key
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS alias   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS issuer  VARCHAR(128);

-- Purpose-scoped, fail-closed lookup of the current active key.
CREATE INDEX IF NOT EXISTS idx_signing_key_tenant_purpose_status
    ON tshepo_keys.signing_key (tenant_id, purpose, status);
