package zw.gov.mohcc.impilo.oros.domain;

/**
 * Status of an individual workstep within an order's fulfillment workflow.
 *
 * <ul>
 *   <li>{@code PENDING} — not yet started</li>
 *   <li>{@code IN_PROGRESS} — currently being executed</li>
 *   <li>{@code COMPLETED} — successfully finished</li>
 *   <li>{@code SKIPPED} — bypassed (e.g. direct-to-lab specimen)</li>
 *   <li>{@code FAILED} — could not complete</li>
 * </ul>
 */
public enum WorkstepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    FAILED
}
