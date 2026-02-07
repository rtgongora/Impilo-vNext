package zw.gov.mohcc.impilo.pct.domain;

/**
 * Lifecycle states of a patient journey through a facility.
 *
 * <p>A journey begins at {@link #REGISTRATION_PENDING} or {@link #ARRIVED}
 * and progresses through triage, queueing, service delivery, and ultimately
 * discharge, death recording, or cancellation.</p>
 */
public enum JourneyState {
    REGISTRATION_PENDING,
    ARRIVED,
    TRIAGED,
    QUEUED,
    IN_SERVICE,
    ADMITTED,
    TRANSFERRED,
    DISCHARGE_PENDING,
    DISCHARGED,
    DEATH_RECORDED,
    CANCELLED,
    NO_SHOW,
    LEFT_WITHOUT_BEING_SEEN
}
