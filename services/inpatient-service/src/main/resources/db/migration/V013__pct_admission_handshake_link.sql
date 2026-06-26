-- PCT <-> inpatient admission handshake (inpatient side).
--
-- inpatient-service owns the physical CENSUS (ward/bed, bed-day accrual). PCT owns the admission DECISION
-- (request/approve). When PCT approves an admission, inpatient materialises the census admission and echoes
-- a bed-assignment back. This column records which PCT admission a census admission was created from, so the
-- handshake is idempotent and traceable across the two systems-of-record.

ALTER TABLE inpatient.admission
    ADD COLUMN IF NOT EXISTS pct_admission_id UUID;

CREATE INDEX IF NOT EXISTS idx_inpatient_admission_pct_admission ON inpatient.admission (pct_admission_id);

COMMENT ON COLUMN inpatient.admission.pct_admission_id IS
    'PCT admission_id this census admission was created from (PCT<->inpatient handshake). Idempotency + cross-SoR trace.';
