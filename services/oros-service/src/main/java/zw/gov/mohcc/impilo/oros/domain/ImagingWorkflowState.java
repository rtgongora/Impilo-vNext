package zw.gov.mohcc.impilo.oros.domain;

/**
 * Fine-grained diagnostic/imaging workflow state (spec §3), tracked as a projection over the
 * coarse canonical {@link OrderStatus} envelope so the platform-wide order state machine
 * (lab/pharmacy/procedure) is unaffected.
 *
 * <p>An IMAGING order's {@code OrderStatus} remains the coarse lifecycle (PLACED → ACCEPTED →
 * IN_PROGRESS → RESULT_AVAILABLE → …) used for routing/SHR; {@code ImagingWorkflowState} carries
 * the granular imaging journey the diagnostic workspace and reconciliation dashboards need.</p>
 */
public enum ImagingWorkflowState {
    // ── Happy path ───────────────────────────────────────────────────────
    RECEIVED,
    ACCEPTED,
    SCHEDULED,
    ARRIVED,
    IN_PROGRESS,
    PERFORMED,
    AWAITING_IMAGES,
    IMAGES_LINKED,
    AWAITING_REPORT,
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
    FAILED,
    REASSIGNED,
    AMENDED,
    SUPERSEDED
}
