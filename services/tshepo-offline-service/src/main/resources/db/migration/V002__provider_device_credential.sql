-- =============================================================================
-- tshepo-offline V002 — Device-bound provider offline credential (PJ13, D-P9)
--
-- For intermittent-connectivity facilities: a provider authenticates ONLINE at
-- least once; a managed device receives a device-bound, time-limited offline
-- credential carrying a CACHED provider/facility/role status snapshot. Offline
-- login (badge/passkey/PIN) then validates against the cached snapshot. NO new
-- professional identity is ever created offline. High-risk actions
-- (prescribing, signing, closing sensitive records) are restricted offline.
-- On reconnection the credential is revalidated and expired/revoked access is
-- detected. Distinct from the generic capability_token (which is a
-- per-capability offline grant); this is the provider's device sign-in anchor.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tshepo_offline.provider_device_credential (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    provider_public_id      VARCHAR(40)     NOT NULL,
    person_health_id        VARCHAR(128)    NOT NULL,
    device_id               VARCHAR(255)    NOT NULL,
    facility_id             UUID            NOT NULL,
    -- cached provider/facility/role status at issue time (the offline snapshot)
    status_snapshot         JSONB           NOT NULL,
    -- action classes that MUST stay online (restricted offline)
    high_risk_denylist      JSONB           NOT NULL,
    -- the online session this credential was minted from (never issued cold)
    issued_from_session     VARCHAR(255)    NOT NULL,
    issued_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ     NOT NULL,
    revoked_at              TIMESTAMPTZ,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT chk_provider_device_credential_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

COMMENT ON TABLE tshepo_offline.provider_device_credential IS
    'Device-bound provider offline credential (PJ13, D-P9): issued only from an online session; cached status snapshot; high-risk actions restricted; never creates a new identity offline.';

CREATE UNIQUE INDEX IF NOT EXISTS ux_provider_device_credential_active
    ON tshepo_offline.provider_device_credential (tenant_id, provider_public_id, device_id)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_provider_device_credential_expiry
    ON tshepo_offline.provider_device_credential (status, expires_at);
