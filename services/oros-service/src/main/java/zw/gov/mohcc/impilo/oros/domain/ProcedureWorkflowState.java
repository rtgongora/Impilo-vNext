package zw.gov.mohcc.impilo.oros.domain;

/**
 * Fine-grained procedure / clinical-assessment workflow state (ECG, echo, endoscopy,
 * spirometry, specialist & allied-health assessments, nursing procedures), tracked on the
 * generalised {@code oros_orders.workflow_state} column as a projection over the coarse
 * canonical {@link OrderStatus} envelope.
 *
 * <p>Covers the scheduling/performance journey (schedule → arrive → perform → report) and the
 * shared report lifecycle. Legal transitions are enforced by the {@code ProcedureWorkflow}
 * guard.</p>
 */
public enum ProcedureWorkflowState {
    /**
     * Recommended but not yet ordered. The pipeline's §4 entry point: a clinician or a
     * decision-support rule proposes a procedure, and it is visible and actionable before
     * anyone commits to it. Distinct from RECEIVED, which means somebody has ordered it.
     */
    PROPOSED,

    // ── Happy path ───────────────────────────────────────────────────────
    RECEIVED,
    ACCEPTED,
    SCHEDULED,
    ARRIVED,
    IN_PROGRESS,
    PERFORMED,
    /**
     * Started and stopped before completion — the patient deteriorated, the view was
     * inadequate, the equipment failed. Distinct from FAILED: the procedure was abandoned
     * deliberately rather than attempted and unsuccessful.
     */
    ABORTED,
    /** Attempted and unsuccessful. The distinction from ABORTED matters for §26 analytics. */
    FAILED,
    /** Some of the intended procedure was done. Neither PERFORMED nor FAILED is honest here. */
    PARTIALLY_COMPLETED,
    /** Done again after an inadequate or failed attempt, linked to the original request. */
    REPEATED,
    REPORT_PENDING,
    PRELIMINARY_REPORT,
    FINAL_REPORT,
    RELEASED,
    ACKNOWLEDGED,
    CLOSED,

    // ── Exceptions ───────────────────────────────────────────────────────
    RETURNED_FOR_CLARIFICATION,
    REJECTED,
    CANCELLED,
    DEFERRED,
    NO_SHOW,
    REASSIGNED,
    AMENDED,
    SUPERSEDED
}
