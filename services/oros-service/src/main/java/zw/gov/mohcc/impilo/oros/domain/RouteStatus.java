package zw.gov.mohcc.impilo.oros.domain;

/**
 * Status of an order routing attempt to an external system.
 *
 * <ul>
 *   <li>{@code PENDING} — routing decision made, not yet transmitted</li>
 *   <li>{@code SENT} — successfully transmitted to the target system</li>
 *   <li>{@code ACKNOWLEDGED} — target system has confirmed receipt</li>
 *   <li>{@code FAILED} — transmission or processing failed</li>
 *   <li>{@code RETRYING} — failed but scheduled for automatic retry</li>
 * </ul>
 */
public enum RouteStatus {
    PENDING,
    SENT,
    ACKNOWLEDGED,
    FAILED,
    RETRYING
}
