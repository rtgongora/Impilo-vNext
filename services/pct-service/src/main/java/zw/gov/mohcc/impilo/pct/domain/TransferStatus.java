package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status of a patient transfer (ward-to-ward or facility-to-facility).
 *
 * <ul>
 *   <li>{@link #REQUESTED} — transfer has been requested</li>
 *   <li>{@link #APPROVED} — transfer approved by receiving party</li>
 *   <li>{@link #IN_TRANSIT} — patient is in transit to the destination</li>
 *   <li>{@link #COMPLETED} — patient has arrived and been received</li>
 *   <li>{@link #CANCELLED} — transfer was cancelled</li>
 * </ul>
 */
public enum TransferStatus {
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    COMPLETED,
    CANCELLED
}
