package zw.gov.mohcc.impilo.pct.domain;

/**
 * Supported queue ordering strategies.
 *
 * <ul>
 *   <li>{@link #FIFO} — first in, first out (default)</li>
 *   <li>{@link #PRIORITY} — ordered by priority score (higher = sooner)</li>
 *   <li>{@link #TRIAGE} — ordered by clinical acuity level</li>
 *   <li>{@link #APPOINTMENT} — ordered by scheduled appointment time</li>
 * </ul>
 */
public enum QueueType {
    FIFO,
    PRIORITY,
    TRIAGE,
    APPOINTMENT
}
