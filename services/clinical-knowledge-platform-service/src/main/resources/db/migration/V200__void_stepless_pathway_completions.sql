-- Void the false completions the stepless-pathway defect could produce.
--
-- Before ccaa1d73f, a pathway session opened on a definition with no steps could be advanced to
-- status = 'COMPLETED' with a completed_at stamp, having recorded no care at all: the eight
-- step-less ED_* definitions (ED_ACS, ED_DKA, ED_ECTOPIC, ED_OBSTETRIC, ED_POISONING,
-- ED_RESP_FAILURE, ED_STATUS_EPILEPTICUS, ED_STROKE) each produced a completion that never happened.
-- The code path is now blocked, but any such row already written is a record of care that did not
-- occur, sitting in the clinical knowledge store.
--
-- WHY VOID, NOT DELETE
--
-- Deleting the rows would erase the evidence that the defect occurred, which the no-delete-to-hide
-- law forbids and which would also make this cleanup unverifiable after the fact. A row marked
-- invalid, with a reason, is the honest artefact: it says "a completion was recorded here, it was
-- false, and here is why". So these are moved to status = 'VOIDED_DEFECT' with the reason recorded
-- in state_json, and completed_at is cleared because there was no completion to timestamp.
--
-- SCOPE — precise, so a legitimate completion is never touched
--
-- Only sessions that are BOTH marked COMPLETED AND attached to a definition that has zero steps.
-- A completed session on a real pathway is a real completion and is left alone. The subquery counts
-- steps rather than trusting a flag, so a definition that later gains steps does not retroactively
-- pull its (correctly voided) historical sessions back.

UPDATE clinical.pathway_sessions s
SET status = 'VOIDED_DEFECT',
    completed_at = NULL,
    state_json = jsonb_set(
        COALESCE(s.state_json, '{}'::jsonb),
        '{voided}',
        jsonb_build_object(
            'reason', 'STEPLESS_PATHWAY_FALSE_COMPLETION',
            'detail', 'Completed on a definition with no steps; no care was recorded. '
                      || 'Voided by V200 after the start/advance guard (ccaa1d73f) closed the path.',
            'previous_status', s.status,
            'previous_completed_at', to_jsonb(s.completed_at)
        )
    )
WHERE s.status = 'COMPLETED'
  AND NOT EXISTS (
        SELECT 1 FROM clinical.pathway_steps st WHERE st.pathway_id = s.pathway_id
  );

-- A record of what this migration touched, so the cleanup is itself auditable rather than a silent
-- UPDATE. The count is written to the Flyway output via a notice.
DO $$
DECLARE
    voided_count INTEGER;
BEGIN
    SELECT count(*) INTO voided_count
    FROM clinical.pathway_sessions
    WHERE status = 'VOIDED_DEFECT'
      AND state_json #>> '{voided,reason}' = 'STEPLESS_PATHWAY_FALSE_COMPLETION';
    RAISE NOTICE 'V200: voided % stepless-pathway false completion(s)', voided_count;
END $$;
