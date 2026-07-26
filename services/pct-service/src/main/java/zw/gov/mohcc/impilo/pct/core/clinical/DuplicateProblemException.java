package zw.gov.mohcc.impilo.pct.core.clinical;

import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;

/**
 * Raised when a problem being added already appears to be on the patient's list.
 *
 * <p>This is deliberately not a validation failure and deliberately not a silent merge. Both of
 * those are wrong in a way that matters: refusing outright makes a genuine second problem
 * unrecordable (a second primary cancer, the other knee, a fresh episode of a condition that really
 * did resolve), and merging silently loses the fact that a different clinician, in a different
 * clinic, reached the same conclusion independently — which is itself clinically informative.</p>
 *
 * <p>So the caller is told what already exists and asked which it means. The existing problem
 * travels on the exception because the answer is unanswerable without seeing it.</p>
 */
public class DuplicateProblemException extends RuntimeException {

    private final transient ProblemEntity existing;

    public DuplicateProblemException(ProblemEntity existing) {
        super("A problem matching '" + existing.getDisplay() + "' is already on this patient's list "
              + "(status " + existing.getClinicalStatus() + "). State whether this is the same "
              + "problem returning or a distinct one.");
        this.existing = existing;
    }

    public ProblemEntity getExisting() {
        return existing;
    }
}
