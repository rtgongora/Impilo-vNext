-- The declared future work from V204's own comment: "There is deliberately no automatic expiry
-- sweep in this migration: expiry is an explicit action (by a clinician or a later scheduled job),
-- never a silent state change." This is that later scheduled job's clock.
--
-- Additive and brownfield-safe: emergency_handover only exists since V204 in this same pack, so
-- nullable with no backfill is honest — no historical PENDING row predates this column, and any
-- that somehow does simply never becomes sweep-eligible (NULL is excluded by the partial index and
-- by the sweep job's own WHERE clause), never auto-expired on a guessed deadline.

ALTER TABLE emergency_handover ADD COLUMN response_due_at TIMESTAMPTZ;

COMMENT ON COLUMN emergency_handover.response_due_at IS
    'Stamped by requestHandover() from a per-target-type response window, mirroring '
    'emergency_alert.response_due_at (V203). NULL for any request predating this column — the sweep '
    'never guesses a deadline for a row that never declared one.';

-- Feeds the sweep's own query: PENDING rows whose deadline has passed. Partial, since only PENDING
-- rows are ever sweep-eligible and ACCEPTED/DECLINED/EXPIRED rows vastly outnumber PENDING ones over
-- time.
CREATE INDEX idx_emergency_handover_pending_due
    ON emergency_handover (response_due_at)
    WHERE status = 'PENDING';
