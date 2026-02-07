package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status of a clinical encounter within a journey.
 *
 * <ul>
 *   <li>{@link #STARTED} — encounter is in progress</li>
 *   <li>{@link #ON_HOLD} — encounter temporarily paused</li>
 *   <li>{@link #COMPLETED} — encounter finished normally</li>
 *   <li>{@link #CANCELLED} — encounter was cancelled before completion</li>
 * </ul>
 */
public enum EncounterStatus {
    STARTED,
    ON_HOLD,
    COMPLETED,
    CANCELLED
}
