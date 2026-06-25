package zw.gov.mohcc.impilo.oros.domain;

/**
 * Fine-grained laboratory-investigation workflow state, tracked on the generalised
 * {@code oros_orders.workflow_state} column as a projection over the coarse canonical
 * {@link OrderStatus} envelope.
 *
 * <p>Covers the specimen-centric lab journey (collection → dispatch → receipt → analysis →
 * validation → release) and the lab-specific exception set (specimen rejection/loss,
 * recollection, could-not-perform) in addition to the shared report lifecycle
 * (preliminary/final/amended/corrected). Legal transitions are enforced by the
 * {@code LabWorkflow} guard.</p>
 */
public enum LabWorkflowState {
    // ── Happy path ───────────────────────────────────────────────────────
    RECEIVED,
    ACCEPTED,
    AWAITING_COLLECTION,
    COLLECTED,
    DISPATCHED,
    IN_TRANSIT,
    RECEIVED_IN_LAB,
    IN_PROGRESS,
    PRELIMINARY_RESULT,
    RESULT_VALIDATED,
    FINAL_RESULT,
    RELEASED,
    ACKNOWLEDGED,
    CLOSED,

    // ── Exceptions ───────────────────────────────────────────────────────
    RETURNED_FOR_CLARIFICATION,
    REJECTED,
    CANCELLED,
    DEFERRED,
    SPECIMEN_REJECTED,
    SPECIMEN_LOST,
    RECOLLECT_REQUESTED,
    COULD_NOT_PERFORM,
    REASSIGNED,
    AMENDED,
    CORRECTED,
    SUPERSEDED
}
