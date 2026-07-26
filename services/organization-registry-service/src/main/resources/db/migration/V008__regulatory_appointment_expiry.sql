-- =====================================================================================
-- org-registry :: V008 — make appointment expiry a fact the system reads
--
-- THE DEFECT THIS CLOSES.
--
-- V006 gave every regulatory appointment a `valid_to`. `RegulatoryAppointmentService.create`
-- writes it. Nothing has ever read it:
--
--   * RegulatoryAppointmentRepository has four methods, none mentioning validity;
--   * no @Scheduled bean exists in this service except the two outbox drainers;
--   * the BFF's matchActiveAppointment() filters on status == 'ACTIVE' and organisation only.
--
-- So an appointment whose validity lapsed a year ago is still ACTIVE, and still mints a fresh
-- 15-minute WORK_CONTEXT token every time its holder enters the workspace. The expiry date was
-- decoration. This migration plus RegulatoryAppointmentExpirySweep make it load-bearing.
--
-- WHY A SWEEP AND NOT A QUERY FILTER.
--
-- Filtering expired rows out of the read path would hide them rather than resolve them: the row
-- would stay ACTIVE in the table, every other consumer would keep believing it, and no `ended`
-- event would fire, so the trust plane would never tear down the tokens already issued. Expiry
-- has to be a state transition that emits — the same shape as vashandi's AssignmentExpirySweep,
-- which is what taught us this.
-- =====================================================================================

-- Idempotency marker: a swept appointment is stamped so a re-run cannot re-end it or re-emit.
ALTER TABLE org_registry.org_registry_regulatory_appointment
    ADD COLUMN IF NOT EXISTS expiry_swept_at TIMESTAMPTZ;

COMMENT ON COLUMN org_registry.org_registry_regulatory_appointment.expiry_swept_at IS
    'Set when the expiry sweep ended this appointment because valid_to had passed. Distinguishes '
    'an appointment that lapsed from one a registrar deliberately ended — both are ENDED, and the '
    'difference matters when someone asks why access stopped.';

-- The sweep''s working set: ACTIVE rows carrying a validity end date. Partial, because the
-- overwhelming majority of rows are not candidates and an unqualified index would be mostly dead
-- weight on a table read on every regulatory sign-in.
CREATE INDEX IF NOT EXISTS idx_regulatory_appointment_expiry_sweep
    ON org_registry.org_registry_regulatory_appointment (valid_to)
    WHERE status = 'ACTIVE' AND valid_to IS NOT NULL;

-- The read path the BFF drives on every regulatory session start (person -> their appointments).
-- It exists here because RB-1 is the first wave to make that path hot.
CREATE INDEX IF NOT EXISTS idx_regulatory_appointment_person_status
    ON org_registry.org_registry_regulatory_appointment (tenant_id, person_health_id, status);

-- NOTE deliberately absent:
--   * no auto-renewal. An appointment reaching its end date lapses; extending it is a decision a
--     registrar makes, with evidence, through verify(). A sweep that quietly extended validity
--     would be the system granting regulatory authority to itself.
--   * no CHECK forcing valid_to NOT NULL. Operational appointments are legitimately open-ended;
--     it is the ADMINISTRATIVE roles that must carry an end date, and that constraint lands in
--     V009 with the role split that defines what "administrative" means.
