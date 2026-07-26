package zw.gov.mohcc.impilo.clinical.contraception;

/**
 * Whether a provider can be reasonably certain a woman is not pregnant.
 *
 * <p>Three values, not two, and the third is the one that does the work. A boolean would collapse
 * "we asked and the answer is no" together with "nobody asked", and those lead to different actions:
 * the first starts a method today, the second asks a question or offers a test first.
 *
 * <p>None of the three is a reason to refuse contraception. {@link #NOT_ESTABLISHED} and
 * {@link #CANNOT_DETERMINE} change <em>which</em> method may be started today and what backup is
 * needed; they do not change whether she leaves with one.
 */
public enum PregnancyCertainty {

    /** No signs of pregnancy, and at least one of the checklist criteria is met. */
    ESTABLISHED,

    /**
     * Positively not established: either she has symptoms or signs of pregnancy, or every criterion
     * was asked and none was met. An answer, not a gap.
     */
    NOT_ESTABLISHED,

    /**
     * Not enough was asked to reach either answer. Distinct from {@link #NOT_ESTABLISHED} because
     * the remedy is different — here there is a specific unanswered question, and the engine names
     * it.
     */
    CANNOT_DETERMINE
}
