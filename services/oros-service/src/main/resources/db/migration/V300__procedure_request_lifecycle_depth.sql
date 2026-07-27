-- =====================================================================================
-- OROS :: V300 — procedure request lifecycle depth (Clinical Procedures Pipeline §4)
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Adjacent reservation does not
-- survive on this tree; three ranges died under their owners in one hour.
--
-- OROS already owns the procedure request — order_type=PROCEDURE since V001, with a
-- deny-by-default ProcedureWorkflow guard over nineteen states since V011. This does NOT
-- rebuild that. It adds the two things §4 asks for and the guard cannot express on its own:
--
--   1. Five missing states (PROPOSED, ABORTED, FAILED, PARTIALLY_COMPLETED, REPEATED),
--      added in the Java enum; no DB vocabulary change is needed because workflow_state is
--      a free-text column guarded in code.
--   2. "Every rejection, cancellation or delay carries a reason and a next action" — which
--      is a durable property of the row, not of the code that wrote it, so it lives here.
--
-- WHY THE CHECK IS NOT VALID. Orders already exist in this estate that are CANCELLED or
-- REJECTED with no recorded reason, because until now nothing required one. A validating
-- constraint would fail the migration and take the service down on boot. NOT VALID enforces
-- the rule on every future write while grandfathering history, which is the honest position:
-- the rule starts now, and the backlog is visible rather than erased. Convalidating it later
-- is a deliberate act that first requires deciding what to do about the existing rows.
-- =====================================================================================

ALTER TABLE oros_orders
    ADD COLUMN IF NOT EXISTS workflow_state_reason TEXT,
    ADD COLUMN IF NOT EXISTS workflow_next_action  TEXT;

COMMENT ON COLUMN oros_orders.workflow_state_reason IS
    'Why the request is in a non-progressing state. Mandatory for rejection, cancellation, deferral, abort, failure, no-show and clarification (pipeline §4).';
COMMENT ON COLUMN oros_orders.workflow_next_action IS
    'What happens next for a non-progressing request. The half of §4 that stops a request disappearing: a reason explains, a next action keeps it alive.';

-- SCOPED TO order_type='PROCEDURE'. The non-progressing vocabulary is shared across
-- categories, so a broad constraint was tempting — and would silently impose a new
-- requirement on imaging and lab, whose callers cancel without a reason today and would start
-- failing. This programme's lease is additive-only in OROS. Extending the rule to the other
-- categories is a good idea and it is their owners' decision, not a side effect of this one.
ALTER TABLE oros_orders
    ADD CONSTRAINT chk_oros_procedure_non_progressing_needs_reason_and_next_action
    CHECK (
        order_type <> 'PROCEDURE'
        OR workflow_state IS NULL
        OR workflow_state NOT IN ('REJECTED','CANCELLED','DEFERRED','ABORTED','FAILED',
                                  'NO_SHOW','RETURNED_FOR_CLARIFICATION')
        OR (workflow_state_reason IS NOT NULL AND length(btrim(workflow_state_reason)) > 0
            AND workflow_next_action IS NOT NULL AND length(btrim(workflow_next_action)) > 0)
    ) NOT VALID;

-- The backlog this makes visible: pre-existing non-progressing orders with no reason. A
-- partial index rather than a report, so the count is cheap to watch and the set is cheap to
-- work through when someone decides to convalidate.
CREATE INDEX IF NOT EXISTS idx_oros_procedure_non_progressing_without_reason
    ON oros_orders (tenant_id, workflow_state)
    WHERE order_type = 'PROCEDURE'
      AND workflow_state IN ('REJECTED','CANCELLED','DEFERRED','ABORTED','FAILED',
                             'NO_SHOW','RETURNED_FOR_CLARIFICATION')
      AND (workflow_state_reason IS NULL OR workflow_next_action IS NULL);
