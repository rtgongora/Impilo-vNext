package zw.gov.mohcc.impilo.pct.core.clinical;

/**
 * Raised when a patient-level register listing is requested for a programme on the confidential
 * lane, while the confidentiality seam that would protect it is not yet enforcing.
 *
 * <p><strong>Why a refusal rather than a filter.</strong> A register listing names, for every person
 * on it, that they are on it. For hypertension that is a worklist. For HIV care it is a list of
 * people's HIV status, and it is the single most sensitive artefact this system could produce. The
 * {@code SPECIALLY_PROTECTED} classification exists for exactly this, but it ships in shadow —
 * {@code CATEGORY_MAP_RATIFIED = false} — so a record can carry the label without the label
 * restricting anything.</p>
 *
 * <p>Serving the list with a protection class that does not protect would be worse than not serving
 * it, because the label would tell everyone downstream that the problem had been handled. Refusing
 * and naming the missing control is the honest state: the capability is built, the governance act
 * that turns it on has not happened, and the refusal says so rather than implying the data does not
 * exist.</p>
 *
 * <p>Aggregate cohort counts remain available for these programmes. A count of how many people are
 * in HIV care is programme reporting; a list of who they are is disclosure.</p>
 */
public class ConfidentialRegisterException extends RuntimeException {

    private final String programme;

    public ConfidentialRegisterException(String programme) {
        super("A patient-level register cannot be served for " + programme + ": it is on the "
              + "confidential lane and would name every person on it as having that condition. The "
              + "SPECIALLY_PROTECTED classification that would restrict this list is built but not "
              + "enforcing (CATEGORY_MAP_RATIFIED is false), so the list would carry a protection "
              + "label that does not protect it. Aggregate cohort counts for this programme are "
              + "available at /v1/programme-enrolments/cohort-counts.");
        this.programme = programme;
    }

    public String getProgramme() {
        return programme;
    }
}
