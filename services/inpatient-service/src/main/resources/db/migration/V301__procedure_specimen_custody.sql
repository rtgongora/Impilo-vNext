-- inpatient-service V301 — Clinical Procedures Pipeline P8 (§13 specimens + chain of custody).
--
-- The pipeline audit (docs/clinical/procedures-pipeline/audit.md §13) rated this THIN: the
-- episode → OROS specimen → histopathology link is real, but custody (who collected it, who
-- received it), adequacy (was there enough tissue/fluid to report on) and label-mismatch
-- prevention (does the label actually match the patient) were absent. This adds them additively
-- to inpatient.procedure_specimen (V022, theatre's own LINK/PROJECTION table — OROS stays the
-- specimen SoR; this is still only a projection with richer columns).
--
-- HONEST SCOPE, stated precisely rather than left implicit (a "no delete-to-hide" concern in the
-- other direction — don't let a schema addition read as a finished gate when it isn't one):
-- TheatreService.processSpecimensFromNote (Wave 1 clinical-safety) parses the operative note's
-- free-text specimen list and calls OROS collect+dispatch SYNCHRONOUSLY in the same transaction,
-- with no discrete human confirmation step to hang a label-check on. This migration adds the
-- COLUMNS and a new SpecimenCustodyService that lets a real person — a scrub nurse at collection,
-- a lab clerk at receipt — RECORD collection, label confirmation, receipt and adequacy against a
-- specimen row. It does NOT make processSpecimensFromNote call it, and does NOT add a CHECK
-- gating DISPATCHED on label confirmation, because doing either would either (a) silently
-- auto-stamp a "confirmation" that no human actually made — the exact fake-safety-gate failure
-- mode this estate has hit before — or (b) require redesigning theatre's auto-dispatch flow into
-- a two-step collect→confirm→dispatch sequence, which is theatre/emergency-lane workflow and
-- needs their sign-off, not a unilateral change from this programme. Recording is real and
-- consistency-checked; the gate into the automatic note-parse path is NOT wired. Declared PARTIAL
-- in the lease, the same way P5 declared adolescent confidentiality PARTIAL rather than silent.

ALTER TABLE inpatient.procedure_specimen
    ADD COLUMN IF NOT EXISTS collected_by         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS collected_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS container_type       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS fixative             VARCHAR(64),
    -- Label-mismatch prevention (PS.WRONG_SITE.PREVENTION, applied to specimen identity rather
    -- than surgical site): a named person attests the label on the container matches the patient
    -- it was taken from. This is the field the audit found absent.
    ADD COLUMN IF NOT EXISTS label_confirmed_by   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS label_confirmed_at   TIMESTAMPTZ,
    -- Custody handoff at the receiving end (lab), distinct from the NHUME transport delivery
    -- event (procedure_transport) — this is a person taking responsibility for the specimen, not
    -- a courier leg completing.
    ADD COLUMN IF NOT EXISTS received_by          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS received_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS adequacy             VARCHAR(24) NOT NULL DEFAULT 'NOT_ASSESSED',
    ADD COLUMN IF NOT EXISTS adequacy_assessed_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS adequacy_assessed_at TIMESTAMPTZ;

-- Actor+timestamp pairing — the same law P4 (site/side) and P5 (consent) encoded: a
-- confirmation is a person and a moment together, or it is not a confirmation.
ALTER TABLE inpatient.procedure_specimen
    ADD CONSTRAINT chk_procedure_specimen_collected_pair CHECK (
        (collected_by IS NULL) = (collected_at IS NULL)),
    ADD CONSTRAINT chk_procedure_specimen_label_confirmed_pair CHECK (
        (label_confirmed_by IS NULL) = (label_confirmed_at IS NULL)),
    ADD CONSTRAINT chk_procedure_specimen_received_pair CHECK (
        (received_by IS NULL) = (received_at IS NULL)),
    ADD CONSTRAINT chk_procedure_specimen_adequacy_assessed_pair CHECK (
        (adequacy_assessed_by IS NULL) = (adequacy_assessed_at IS NULL)),
    ADD CONSTRAINT chk_procedure_specimen_adequacy_vocabulary CHECK (
        adequacy IN ('NOT_ASSESSED', 'ADEQUATE', 'INADEQUATE')),
    -- An adequacy verdict other than the default must have been actually assessed by someone.
    ADD CONSTRAINT chk_procedure_specimen_adequacy_requires_assessor CHECK (
        adequacy = 'NOT_ASSESSED' OR adequacy_assessed_by IS NOT NULL);
