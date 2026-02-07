package zw.gov.mohcc.impilo.oros.domain;

/**
 * Lifecycle states of a clinical order.
 *
 * <p>Orders progress through a defined state machine from placement to
 * completion, with branching paths for cancellation, rejection, and
 * failure. Key transitions:</p>
 * <ul>
 *   <li>{@code DRAFT} — saved but not yet submitted</li>
 *   <li>{@code PLACED} — submitted by clinician, pending acceptance</li>
 *   <li>{@code ACCEPTED} — acknowledged by fulfilling department</li>
 *   <li>{@code SCHEDULED} — queued for processing at a specific time</li>
 *   <li>{@code IN_PROGRESS} — actively being worked on</li>
 *   <li>{@code PARTIAL_RESULT} — some results available, more pending</li>
 *   <li>{@code RESULT_AVAILABLE} — all results ready for review</li>
 *   <li>{@code REVIEWED} — clinician has reviewed results</li>
 *   <li>{@code RELEASED} — results released to patient/chart</li>
 *   <li>{@code COMPLETED} — order fully closed out</li>
 *   <li>{@code CANCELLED} — withdrawn before completion</li>
 *   <li>{@code REJECTED} — declined by fulfilling department</li>
 *   <li>{@code FAILED} — system or external failure</li>
 * </ul>
 */
public enum OrderStatus {
    DRAFT,
    PLACED,
    ACCEPTED,
    SCHEDULED,
    IN_PROGRESS,
    PARTIAL_RESULT,
    RESULT_AVAILABLE,
    REVIEWED,
    RELEASED,
    COMPLETED,
    CANCELLED,
    REJECTED,
    FAILED
}
