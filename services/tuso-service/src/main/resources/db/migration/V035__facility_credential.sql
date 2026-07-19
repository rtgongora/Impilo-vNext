-- =============================================================================
-- Tuso V035 — Facility QR credential (FJ QR, D-L7; Provider+Place W9)
--
-- An approved facility may receive a signed QR credential. Scanning opens a
-- policy-filtered public verification page — official name, type, public
-- identifier, verified location, current public status, permitted services,
-- validity — never internal registry ids, confidential findings, or
-- administrators' personal details. Distinct from the HPA certificate QR
-- (V018): this is a facility-identity credential. Revoked on trust collapse.
-- Signing is seed-derived Ed25519 (the vito QrSigningService house pattern,
-- replicated) so a printed QR cannot be forged; live status still comes from
-- the registry at scan time.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tuso.facility_credential (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    credential_uuid     UUID            NOT NULL UNIQUE,
    facility_uuid       UUID            NOT NULL,
    verification_code   VARCHAR(24)     NOT NULL UNIQUE,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | REVOKED | EXPIRED
    issued_by           VARCHAR(255),
    issued_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(255),
    CONSTRAINT chk_facility_credential_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

COMMENT ON TABLE tuso.facility_credential IS
    'Facility QR credentials (FJ QR, D-L7): signed credential + short verification code; scan opens a policy-filtered public verification page. Distinct from the HPA certificate QR.';

CREATE INDEX IF NOT EXISTS idx_facility_credential_facility
    ON tuso.facility_credential (tenant_id, facility_uuid);
