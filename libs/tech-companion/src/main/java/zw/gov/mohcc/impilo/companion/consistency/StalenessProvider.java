package zw.gov.mohcc.impilo.companion.consistency;

/**
 * Provides staleness information for Class B consistency enforcement.
 *
 * <p>Each service implements this interface to report how stale its
 * local projection is relative to the authoritative source. The
 * {@link ConsistencyClassFilter} uses this to decide whether a
 * request may proceed under bounded staleness.</p>
 *
 * <p>Implementations typically check a projection watermark table
 * or a last-sync timestamp from Kafka consumer lag.</p>
 */
public interface StalenessProvider {

    /**
     * Return the current staleness in milliseconds for the given domain/action.
     *
     * @param actionType the logical action type being performed
     * @return staleness in milliseconds (0 = fully fresh)
     */
    long currentStalenessMs(String actionType);
}
