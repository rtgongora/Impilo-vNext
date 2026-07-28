-- inventory-service V300 — Clinical Procedures Pipeline P8 (§14 devices + implants).
-- First migration this programme writes in inventory-service; starts the V300-V329 band here,
-- same as every other co-edited service in this programme (procedures/tshepo-authz/oros/mvumo/
-- inpatient all reserve V300-V329; new standalone services start at V001 — inventory-service
-- already has its own V001-V014 history, so it joins the shared band rather than restarting).
--
-- The pipeline audit (docs/clinical/procedures-pipeline/audit.md §14) found the national implant
-- registry real for recall traceability, but missing two things: patient-facing information (what
-- a patient carrying the device would be told about it) and the removal/revision lifecycle (a
-- device does not stay implanted forever — it is removed, or replaced by a newer one). Both
-- additive to inv_patient_implant; inv_implant_registry (the physical-unit table) is untouched.

ALTER TABLE inv_patient_implant
    ADD COLUMN IF NOT EXISTS patient_facing_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS patient_facing_description  TEXT,
    ADD COLUMN IF NOT EXISTS expected_lifespan_months     INT,
    ADD COLUMN IF NOT EXISTS next_review_due_at           TIMESTAMPTZ,
    -- Removal: the device leaves the patient with no replacement recorded here.
    ADD COLUMN IF NOT EXISTS removed_at                   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS removed_by                   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS removal_reason               TEXT,
    -- Revision: THIS row is the replacement; it points BACK at the row it superseded. The old
    -- row is marked REVISED (removed_at/by/reason set, same as a removal) by the same
    -- transaction that inserts this one — no forward-pointer column needed, and no race between
    -- "old row updated" and "new row inserted" because both happen together in
    -- ImplantTraceabilityService.reviseImplant.
    ADD COLUMN IF NOT EXISTS revision_of_patient_implant_id UUID
        REFERENCES inv_patient_implant(patient_implant_id);

-- status already existed as free text defaulting to 'IMPLANTED' (V012); constrain the vocabulary
-- now that there are real transitions out of it, without disturbing existing rows (all currently
-- 'IMPLANTED', which satisfies the new CHECK unchanged).
ALTER TABLE inv_patient_implant
    ADD CONSTRAINT chk_patient_implant_status CHECK (status IN ('IMPLANTED', 'REMOVED', 'REVISED')),
    -- A removal or revision without who/when is not a removal — same actor+timestamp pairing law
    -- as every other confirmation this programme has added (P4 site/side, P5 consent, P8 specimen
    -- custody).
    ADD CONSTRAINT chk_patient_implant_removed_pair CHECK (
        (removed_at IS NULL) = (removed_by IS NULL)),
    -- An IMPLANTED device has no removal recorded; a REMOVED or REVISED one always does.
    ADD CONSTRAINT chk_patient_implant_removal_matches_status CHECK (
        (status = 'IMPLANTED') = (removed_at IS NULL));

CREATE INDEX IF NOT EXISTS idx_patient_implant_revision_of
    ON inv_patient_implant (revision_of_patient_implant_id) WHERE revision_of_patient_implant_id IS NOT NULL;

-- Devices due a review (e.g. a pacemaker battery check) — the query a future reminder/worklist
-- feature will run.
CREATE INDEX IF NOT EXISTS idx_patient_implant_review_due
    ON inv_patient_implant (tenant_id, next_review_due_at)
    WHERE status = 'IMPLANTED' AND next_review_due_at IS NOT NULL;
