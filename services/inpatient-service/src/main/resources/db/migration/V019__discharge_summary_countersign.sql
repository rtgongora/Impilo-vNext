-- P3 PCT care-tracker: countersignature gate on the discharge summary.
-- Mirrors the PCT form-level countersign semantics (author vs countersigner are distinct
-- signatures; countersign is required before finalisation when the flag is set).
-- Additive only — existing summaries default to countersign_required = FALSE so no
-- behaviour changes for rows created before this migration.

ALTER TABLE inpatient.discharge_summary
    ADD COLUMN countersign_required      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN authored_by               VARCHAR(128),
    ADD COLUMN countersigned_by          VARCHAR(128),
    ADD COLUMN countersigned_at          TIMESTAMPTZ,
    ADD COLUMN countersign_attestation   TEXT;

COMMENT ON COLUMN inpatient.discharge_summary.countersign_required IS
    'When TRUE the summary cannot be finalised until countersigned_by is set (policy/cadre-driven, configurable)';
COMMENT ON COLUMN inpatient.discharge_summary.authored_by IS
    'Actor who last saved the draft — a countersigner must be a different actor';
