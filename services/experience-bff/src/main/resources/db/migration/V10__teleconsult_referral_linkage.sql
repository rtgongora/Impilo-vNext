-- V10: Add referral_id to telemedicine sessions for teleconsult-to-referral linkage.
-- This enables tracking which telemedicine session was created as outcome of a referral,
-- closing the referral → teleconsult → outcome return loop.

ALTER TABLE telemedicine_sessions ADD COLUMN IF NOT EXISTS referral_id UUID;
CREATE INDEX IF NOT EXISTS idx_telemedicine_sessions_referral ON telemedicine_sessions(referral_id);

-- Also add referral_id to citizen telehealth sessions for citizen-initiated teleconsults
ALTER TABLE citizen_telehealth_sessions ADD COLUMN IF NOT EXISTS referral_id UUID;
CREATE INDEX IF NOT EXISTS idx_citizen_telehealth_referral ON citizen_telehealth_sessions(referral_id);
