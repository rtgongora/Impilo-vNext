-- TM-B5 (B5-3): labelled provider-only side-channel.
-- Provider-to-provider side-discussion within a teleconsult, NEVER visible to the patient. Kept in a
-- SEPARATE column from `messages` (the patient-visible thread) so the boundary is STRUCTURAL, not a
-- read-time filter: no patient-facing read path (getTelehealthSession / getPatientTelehealthSessions /
-- messages thread) touches this column, so a provider note cannot leak regardless of actor. Each entry:
-- {author, authorName?, note, timestamp}.
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS provider_notes JSONB NOT NULL DEFAULT '[]';
