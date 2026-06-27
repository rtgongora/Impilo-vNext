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
    // ── Happy path ───────────────────────────────────────────────────────
    RECEIVED,
    ACCEPTED,
    SCHEDULED,
    ARRIVED,
    IN_PROGRESS,
    PERFORMED,
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
