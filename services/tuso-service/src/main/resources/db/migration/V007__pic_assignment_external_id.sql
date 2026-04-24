-- =============================================================================
-- TUSO V007 — PIC assignment external reference (Varapi source-of-truth)
-- Adds a stable external assignment identifier to support mirroring VARAPI PIC
-- assignments into TUSO without duplicating provider truth.
-- =============================================================================

ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS external_assignment_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tuso_pic_external
    ON tuso.practitioner_in_charge_assignment (facility_id, external_assignment_id)
    WHERE external_assignment_id IS NOT NULL;

