-- PCT <-> inpatient admission handshake linkage.
--
-- Field-ownership reconciliation between the two AdmissionEntity owners:
--   * PCT owns the clinical admission DECISION: request -> approve -> admitted, keyed by journey_id,
--     with requested_by / approved_by and the admitting context. PCT.status is the decision lifecycle.
--   * inpatient-service owns the physical CENSUS: ward/bed assignment, admission_type, diet/activity,
--     bed-day accrual, keyed by admission_ref. inpatient assigns the bed and echoes it back.
--
-- This migration adds the cross-service link fields so PCT can carry the encounter ref (the inpatient
-- admission key parent) and stamp back the inpatient admission_ref + assigned bed once inpatient assigns it.

ALTER TABLE pct_admissions
    ADD COLUMN encounter_id            UUID,           -- BUTANO/encounter ref handed to inpatient
    ADD COLUMN inpatient_admission_ref UUID,           -- inpatient-service admission_ref (census SoR key)
    ADD COLUMN admitting_diagnosis     TEXT,           -- clinical context carried into the census admission
    ADD COLUMN admission_type          VARCHAR(32),    -- ELECTIVE | EMERGENCY | TRANSFER (clinical intent)
    ADD COLUMN bed_assigned_at         TIMESTAMPTZ;     -- when inpatient echoed the bed assignment back

CREATE INDEX idx_pct_admissions_inpatient_ref ON pct_admissions (inpatient_admission_ref);

COMMENT ON COLUMN pct_admissions.inpatient_admission_ref IS
    'inpatient-service admission_ref. PCT requests/approves; inpatient assigns the bed and owns census. Set on bed-assignment echo.';
COMMENT ON COLUMN pct_admissions.encounter_id IS
    'Encounter reference handed to inpatient-service as the census admission parent key.';
