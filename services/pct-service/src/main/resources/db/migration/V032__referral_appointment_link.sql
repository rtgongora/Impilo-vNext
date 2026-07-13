-- G18: persist the booking↔referral back-link on the teleconsult referral.
-- Previously a scheduled teleconsult's appointment id lived only in the appointment's
-- free-text `notes` tag ("teleconsult:referral:<id>"); the referral had no column for it,
-- so PCT silently dropped the appointment_id/scheduled_at the BFF sent back. These columns
-- make the referral the durable join to its scheduling-service appointment.
ALTER TABLE pct_referrals
    ADD COLUMN IF NOT EXISTS appointment_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS scheduled_at   TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_pct_referrals_appointment_id
    ON pct_referrals (appointment_id)
    WHERE appointment_id IS NOT NULL;
