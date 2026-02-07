package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status of a patient within a queue.
 *
 * <ul>
 *   <li>{@link #WAITING} — patient is in the queue awaiting service</li>
 *   <li>{@link #CALLED} — patient has been called but not yet in service</li>
 *   <li>{@link #IN_SERVICE} — patient is currently being attended to</li>
 *   <li>{@link #PAUSED} — service paused (e.g. awaiting lab results)</li>
 *   <li>{@link #COMPLETED} — service completed for this queue stop</li>
 *   <li>{@link #NO_SHOW} — patient did not respond when called</li>
 *   <li>{@link #LEFT} — patient left the queue voluntarily</li>
 *   <li>{@link #TRANSFERRED} — patient moved to a different queue</li>
 * </ul>
 */
public enum QueueItemStatus {
    WAITING,
    CALLED,
    IN_SERVICE,
    PAUSED,
    COMPLETED,
    NO_SHOW,
    LEFT,
    TRANSFERRED
}
