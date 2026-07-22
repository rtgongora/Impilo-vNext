-- TM-B16 (B16-1): teaching-consent gate on the recording→learning-artefact path (journey #12 / R34).
-- Spec §8 rows 18-19: learning artefacts require a SEPARATE teaching consent distinct from care
-- consent, and MUST NOT proceed on care-consent alone. This column carries the MVUMO teaching
-- consent directive reference captured at scheduling; ReplayService gates artefact adoption on it
-- (shadow-then-enforce via live.learning.teaching-consent.mode).
ALTER TABLE live_events ADD COLUMN IF NOT EXISTS teaching_consent_ref VARCHAR(128);
