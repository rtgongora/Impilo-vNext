-- TM-B4 (scheduling half): dedicated external-ref on appointments — the epic's explicit outcome,
-- replacing the free-text notes tag ("teleconsult:referral:<id>") as the machine linkage between a
-- TELECONSULT appointment and its pct referral (referral->appointment landed as pct V032; this is
-- the reverse direction). Generic name: other domains may link their own case ids the same way.
ALTER TABLE booking.appointment ADD COLUMN IF NOT EXISTS external_ref VARCHAR(160);
CREATE INDEX IF NOT EXISTS ix_appointment_external_ref
    ON booking.appointment (tenant_id, external_ref) WHERE external_ref IS NOT NULL;
-- Double-booking pre-check support (TM-B4 slot-race): overlap scans by provider + window.
CREATE INDEX IF NOT EXISTS ix_appointment_provider_window
    ON booking.appointment (tenant_id, provider_id, start_time);
