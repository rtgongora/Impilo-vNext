-- V044 — real clinical-note signing.
--
-- The sign endpoint was a placebo: it returned signed=false unconditionally
-- because the note entity had no signed state at all. Add the columns so a
-- clinician's signature actually persists (signed, signed_at, signed_by).
-- Existing rows default to unsigned.

ALTER TABLE pct.pct_clinical_notes
    ADD COLUMN IF NOT EXISTS signed     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS signed_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS signed_by  VARCHAR(128);
