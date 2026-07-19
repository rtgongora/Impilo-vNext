-- =============================================================================
-- Vashandi V008 — Engagement types + ENFORCED assignment expiry (PJ4/PJ16, D-P7)
--
-- Facility access is a separate journey from professional standing: an
-- assignment carries HOW the provider is engaged (permanent posting vs
-- rotation/locum/outreach/telemed/specialist-pool/supervisory/training), and
-- every non-permanent engagement MUST be end-dated. Expiry is enforced by a
-- sweep (active + end_date passed → ended + event), never advisory — expired
-- access feeds the same revocation pipeline as licence lapse (work-context
-- token teardown in tshepo-identity).
-- =============================================================================

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD COLUMN IF NOT EXISTS engagement_type VARCHAR(32);

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD CONSTRAINT chk_assignment_engagement_type CHECK (
        engagement_type IS NULL OR engagement_type IN (
            'PERMANENT', 'ROTATION', 'LOCUM', 'OUTREACH', 'TELEMED',
            'SPECIALIST_POOL', 'SUPERVISORY', 'TRAINING')) NOT VALID;

-- A temporary engagement without an expiry is a doctrine violation.
ALTER TABLE vashandi.vsh_workforce_assignment
    ADD CONSTRAINT chk_assignment_temporary_expiry CHECK (
        engagement_type IS NULL OR engagement_type = 'PERMANENT' OR end_date IS NOT NULL) NOT VALID;

COMMENT ON COLUMN vashandi.vsh_workforce_assignment.engagement_type IS
    'How the provider is engaged (D-P7): PERMANENT | ROTATION | LOCUM | OUTREACH | TELEMED | SPECIALIST_POOL | SUPERVISORY | TRAINING. Non-permanent engagements require end_date.';

CREATE INDEX IF NOT EXISTS idx_assignment_expiry
    ON vashandi.vsh_workforce_assignment (end_date)
    WHERE status = 'active' AND end_date IS NOT NULL;
