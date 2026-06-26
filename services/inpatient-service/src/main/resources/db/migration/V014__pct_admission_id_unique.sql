-- Enforce one census admission per PCT admission (DB-level idempotency for the PCT<->inpatient handshake).
--
-- The application short-circuits admitFromPctApproval on an existing pct_admission_id (status-agnostic),
-- but a concurrent redelivery could still race past the read. A UNIQUE constraint makes the database the
-- authoritative guard against a double-admit / double bed-day.
--
-- Partial: only rows with a non-null pct_admission_id are constrained. Manually-created census admissions
-- (inpatient.admission rows from createAdmission, not from the PCT handshake) have a NULL pct_admission_id
-- and are intentionally excluded — many such rows may coexist.

CREATE UNIQUE INDEX IF NOT EXISTS uq_inpatient_admission_pct_admission
    ON inpatient.admission (pct_admission_id)
    WHERE pct_admission_id IS NOT NULL;
