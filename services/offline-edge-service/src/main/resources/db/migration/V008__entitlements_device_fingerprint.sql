-- EntitlementEntity device binding columns

ALTER TABLE ofe_entitlements
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);

ALTER TABLE ofe_entitlements
    ADD COLUMN IF NOT EXISTS max_offline_encounters INT NOT NULL DEFAULT 50;
