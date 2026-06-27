-- B1 (Wave B): real step-up verification model.
-- Adds verification mode + hashed secret + context + attempt/replay/audit fields so
-- step-up challenges can be verified by mode (TOTP / SMS_OTP / BIOMETRIC /
-- SUPERVISOR_APPROVAL) instead of accepting any non-blank code.

ALTER TABLE tshepo_authz.step_up_challenge
    ADD COLUMN IF NOT EXISTS mode                     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS verification_secret_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS purpose                  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requested_action         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS risk_level               VARCHAR(16),
    ADD COLUMN IF NOT EXISTS attempt_count            INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS approved_by              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS audit_metadata           TEXT;
