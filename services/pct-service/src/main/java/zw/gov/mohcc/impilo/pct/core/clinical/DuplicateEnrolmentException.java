package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.UUID;

/**
 * Raised when a patient is already actively enrolled in the programme being enrolled into.
 *
 * <p>Unlike a duplicate problem — which is a question, because a second primary cancer is genuinely
 * new — a second concurrent enrolment in the same programme is always an error: a person is in HIV
 * care once at a time. A returning patient who was lost to follow-up gets a new enrolment only once
 * the prior one has EXITED. So this is a hard conflict pointing at the active enrolment, not an
 * offer of resolutions.</p>
 */
public class DuplicateEnrolmentException extends RuntimeException {

    private final String programme;
    private final UUID activeEnrolmentId;

    public DuplicateEnrolmentException(String programme, UUID activeEnrolmentId) {
        super("The patient already has an active " + programme + " enrolment (" + activeEnrolmentId
              + "). Exit it before re-enrolling; a person is enrolled in a programme once at a time.");
        this.programme = programme;
        this.activeEnrolmentId = activeEnrolmentId;
    }

    public String getProgramme() { return programme; }

    public UUID getActiveEnrolmentId() { return activeEnrolmentId; }
}
