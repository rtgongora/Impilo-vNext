-- =============================================================================
-- Varapi V026 — Provider badge credentials (PJ6, D-P5; Provider+Place W7)
--
-- The badge is a CREDENTIAL and an ACCOUNT SELECTOR — never authorisation and
-- never the identity: it carries a serial + signed QR payload; it never holds
-- the HID, National ID, passwords or clinical privileges. Losing a badge
-- revokes THE SERIAL ONLY — the Provider ID, profile and history are
-- untouched. Verification is a live registry check (public register facts via
-- the 4-axis projection), so a revoked/suspended state is honest at scan time.
-- =============================================================================

CREATE TABLE IF NOT EXISTS varapi.provider_badge (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    badge_serial    VARCHAR(40)     NOT NULL UNIQUE,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider (id),
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | REVOKED | LOST
    print_job_ref   VARCHAR(120),
    issued_by       VARCHAR(255),
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(255),
    CONSTRAINT chk_badge_status CHECK (status IN ('ACTIVE', 'REVOKED', 'LOST'))
);

COMMENT ON TABLE varapi.provider_badge IS
    'Provider badge credentials (PJ6, D-P5): serial + signed QR; account selector only, never authorisation; lost badge = revoke serial only.';

CREATE INDEX IF NOT EXISTS idx_provider_badge_provider
    ON varapi.provider_badge (tenant_id, provider_id);
