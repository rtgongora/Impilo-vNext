-- =============================================================================
-- Varapi V016 — Provider bootstrap chain: bulk preload + self-claim (L3 W6)
--
-- Bootstrap doctrine: national-admin -> org -> representatives -> BULK provider
-- preload -> provider SELF-CLAIM. A regulator/org representative preloads a
-- skeleton provider profile (lifecycle_status = 'PRELOADED'); the real provider
-- later signs in as a person and CLAIMS that profile, binding their verified
-- Impilo Health ID to it (PRELOADED -> CLAIMED).
--
-- Preloaded profiles reuse the existing varapi.provider row (no shadow table);
-- this migration only adds the claim-token surface + bootstrap provenance.
-- =============================================================================

-- 1) Bootstrap provenance on the provider row -------------------------------
ALTER TABLE varapi.provider
    ADD COLUMN IF NOT EXISTS bootstrap_origin     VARCHAR(40),   -- e.g. BULK_PRELOAD, SELF_REGISTERED, COUNCIL_IMPORT
    ADD COLUMN IF NOT EXISTS preload_batch_id     UUID,          -- groups a single bulk preload run
    ADD COLUMN IF NOT EXISTS claimed_at           TIMESTAMPTZ,   -- set when the profile is claimed
    ADD COLUMN IF NOT EXISTS claimed_health_id    UUID;          -- person anchor that claimed the profile

COMMENT ON COLUMN varapi.provider.bootstrap_origin IS
    'How this provider row came into existence (BULK_PRELOAD, SELF_REGISTERED, COUNCIL_IMPORT).';
COMMENT ON COLUMN varapi.provider.preload_batch_id IS
    'Identifies the bulk-preload run that created this PRELOADED skeleton; null for self-registered.';
COMMENT ON COLUMN varapi.provider.claimed_health_id IS
    'Impilo Health ID that claimed this preloaded profile (binds the skeleton to a real person).';

CREATE INDEX IF NOT EXISTS idx_provider_preload_batch
    ON varapi.provider (tenant_id, preload_batch_id);

-- 2) Claim tokens ------------------------------------------------------------
-- A single-use, expiring token a preloaded provider presents to claim their
-- profile. The token never authenticates; it only authorises the claim of a
-- specific preloaded provider row once the claimant's identity is established.
CREATE TABLE IF NOT EXISTS varapi.provider_claim_token (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider (id),
    -- Hashed token value (SHA-256 hex) — the raw token is returned once at
    -- issue time and never stored, so a DB compromise cannot replay claims.
    token_hash          VARCHAR(64)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ISSUED', -- ISSUED | CLAIMED | EXPIRED | REVOKED
    issued_to_hint      VARCHAR(255),   -- e.g. masked email/phone the rep nominated (delivery hint only)
    expires_at          TIMESTAMPTZ     NOT NULL,
    claimed_at          TIMESTAMPTZ,
    claimed_health_id   UUID,
    preload_batch_id    UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_provider_claim_token_hash
    ON varapi.provider_claim_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_provider_claim_token_provider
    ON varapi.provider_claim_token (tenant_id, provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_claim_token_status
    ON varapi.provider_claim_token (tenant_id, status);

COMMENT ON TABLE varapi.provider_claim_token IS
    'Single-use expiring tokens authorising self-claim of a PRELOADED provider profile (L3 W6). Stores only the SHA-256 hash of the token.';
