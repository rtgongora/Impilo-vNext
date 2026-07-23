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
 *
 * <p>Volume II §9.1 target additions (OF-B1, each backward-compatible):</p>
 * <ul>
 *   <li>{@code PENDING_SIGNATURE} — validated draft awaiting the prescriber's
 *       detached JWS (reachable only when signing is enabled, OF-B2)</li>
 *   <li>{@code ON_HOLD} — paused with a coded reason (clarification, prior auth,
 *       safety signal); resumes to the state it was holding from</li>
 *   <li>{@code REVOKED} — clinically revoked by the prescriber/governance; terminal</li>
 *   <li>{@code REPLACED} — superseded by a successor order; terminal</li>
 *   <li>{@code EXPIRED} — validity window lapsed with no active fulfilment; terminal</li>
 *   <li>{@code ENTERED_IN_ERROR} — governance error-marking pre-fulfilment; terminal,
 *       hidden from clinical reads</li>
 * </ul>
 */
public enum OrderStatus {
    DRAFT,
    PENDING_SIGNATURE,
    PLACED,
    ACCEPTED,
    SCHEDULED,
    IN_PROGRESS,
    ON_HOLD,
    PARTIAL_RESULT,
    RESULT_AVAILABLE,
    REVIEWED,
    RELEASED,
    COMPLETED,
    CANCELLED,
    REJECTED,
    FAILED,
    REVOKED,
    REPLACED,
    EXPIRED,
    ENTERED_IN_ERROR
}
