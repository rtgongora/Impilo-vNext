-- G20: link a teleconsult recording back to its clinical record (the referral).
-- rtc-gateway publishes impilo.rtc.recording.available.v1 with owningService + owningRef; PCT now
-- provisions rtc sessions with owningRef = referralId, so the recording is attributable to a referral.
-- A PCT consumer of that event writes the storage reference here.
ALTER TABLE pct_referrals
    ADD COLUMN IF NOT EXISTS recording_ref         VARCHAR(160),
    ADD COLUMN IF NOT EXISTS recording_storage_key VARCHAR(512);
