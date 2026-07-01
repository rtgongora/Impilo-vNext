-- Review-decision overlay for facility import rows. The original import outcome (`outcome`) is preserved;
-- these columns record the reviewer's decision and its lifecycle so history stays clear. Row-level review
-- audit is captured append-only in `review_history` (JSONB). Raw source values are never mutated.

ALTER TABLE tuso.facility_import_row
    ADD COLUMN IF NOT EXISTS review_decision   VARCHAR(48),
    ADD COLUMN IF NOT EXISTS decision_status   VARCHAR(48),
    ADD COLUMN IF NOT EXISTS reviewed_by       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reviewed_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_reason     TEXT,
    ADD COLUMN IF NOT EXISTS conflict_reason   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS review_history    JSONB;

-- Backfill decision_status from the persisted import outcome so existing rows have a starting state.
UPDATE tuso.facility_import_row
   SET decision_status = CASE
        WHEN outcome = 'IMPORTED' THEN 'IMPORTED'
        WHEN outcome = 'READY_FOR_IMPORT' THEN 'READY_FOR_IMPORT'
        WHEN outcome = 'FAILED' THEN 'FAILED'
        ELSE 'PENDING_REVIEW'
   END
 WHERE decision_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_facility_import_row_decision
    ON tuso.facility_import_row (import_run_id, decision_status);
