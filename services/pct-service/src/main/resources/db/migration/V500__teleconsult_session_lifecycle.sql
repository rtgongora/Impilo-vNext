-- Teleconsult session lifecycle timestamps.
--
-- A teleconsult session is modelled as a referral package. The model recorded when the referral was
-- submitted and when it was completed, but nothing about the consultation itself — so the
-- telemedicine list could not say when a consult was scheduled or whether the parties had actually
-- joined. The shell declares scheduled_at and started_at and rendered them empty; filling them from
-- submitted_at would have reported the referral's paperwork time as the consultation's start, on a
-- screen a clinician reads to know whether a consult is under way.
--
-- session_ended_at is deliberately absent: a telemedicine referral's completion IS the end of the
-- consultation (completed_at, set on close, which already refuses to close without a note). Adding a
-- second end timestamp would create two answers to one question.
--
-- Band: V500–V529, claimed for Telemedicine in docs/registry/iatg-telemedicine-leases.md.
-- V430–V459 is RMNP's and V400–V429 Paediatrics', so the next free number was not the next number.

ALTER TABLE pct_referral_packages
    ADD COLUMN IF NOT EXISTS session_scheduled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS session_started_at   TIMESTAMPTZ;

COMMENT ON COLUMN pct_referral_packages.session_scheduled_at IS
    'When the teleconsultation is scheduled to take place. Null for on-demand consults.';
COMMENT ON COLUMN pct_referral_packages.session_started_at IS
    'When the consultation actually began — set once, on the first waiting-room admission. Distinct '
    'from submitted_at, which is when the referral paperwork was sent.';

-- Partial index: the telemedicine worklist asks for consults under way, which is a small slice of
-- a table that also holds every non-virtual referral.
CREATE INDEX IF NOT EXISTS idx_referral_packages_session_started
    ON pct_referral_packages (tenant_id, session_started_at)
    WHERE session_started_at IS NOT NULL;
