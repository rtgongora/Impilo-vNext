-- TM-B11 (B11-1): offline store-and-forward intake controls on the teleconsult spine.
-- A referral captured offline arrives later as a replayed create. Three controls (spec §19):
--   client_offline_id   — client-generated package id; UNIQUE per tenant so a replayed create
--                         returns the EXISTING referral instead of minting a duplicate
--                         (duplicate-event prevention at the SoR, beneath the BFF Idempotency-Key
--                         filter; V019 community offline_id precedent).
--   package_checksum    — client-sealed integrity control, verified on intake
--                         (sha256 hex of the documented canonical string — see
--                         TelemedicineOrchestrationService S&F contract).
--   captured_at         — freshness timestamp (when the package was captured offline; client
--                         clock ADVISORY per spec — server ordering stays authoritative).
--   package_expires_at  — staleness window; an expired package is refused intake (422) rather
--                         than silently routed as if fresh.
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS client_offline_id VARCHAR(128);
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS package_checksum VARCHAR(128);
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS package_expires_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS ux_pct_referrals_client_offline_id
    ON pct_referrals (tenant_id, client_offline_id) WHERE client_offline_id IS NOT NULL;
