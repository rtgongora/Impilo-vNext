package zw.gov.mohcc.impilo.pct.core.clinical;

import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

/**
 * Decides the confidentiality stamp a reproductive record carries: its category, its sensitivity
 * class, why, and under which policy version.
 *
 * <p>Pure and stateless — demographics and policy are passed in, never fetched here — so the whole
 * decision table is testable without a database, a clock or a network.
 *
 * <h2>Stamp the category, gate the class</h2>
 * <p>A CATEGORY is a content classification ("this record is SRH content") and asserts nothing about
 * protection, so it is decided and written today. A CLASS is a protection claim, and is only applied
 * when {@code classStampingEnabled} is true. Until the governance flip that flag is false, because
 * the PDP runs in SHADOW and grants no categories while
 * {@code SpeciallyProtectedVisibilityGuard} fails closed — so stamping the class now would make the
 * record invisible to the clinician who just wrote it.
 *
 * <h2>Stamping fails OPEN; reading fails CLOSED</h2>
 * <p>If the date of birth cannot be resolved, or no governed policy parameter is in force, the
 * category is still stamped and the class is left at {@code FULL_CLINICAL} with the reason recorded
 * ({@code AGE_UNRESOLVED} / {@code POLICY_UNAVAILABLE}). The asymmetry with the read guard is
 * deliberate: a false negative leaves a record readable by the care team, while a false positive
 * makes it invisible to them, and the guard already fails closed on read. Compounding a second
 * closed failure at stamp time would produce exactly the over-restriction the guard's own
 * documentation calls lethal.
 *
 * <h2>Age is taken at the act, not today</h2>
 * <p>A woman who was 17 when a loss occurred and is 19 now is still eligible: the record was created
 * under a promise of confidentiality, and that promise does not lapse on a birthday.
 */
public final class ConfidentialityStamper {

    public static final String CLASS_FULL_CLINICAL = "FULL_CLINICAL";
    public static final String CLASS_SPECIALLY_PROTECTED = "SPECIALLY_PROTECTED";

    public static final String BASIS_RECORD_TYPE_ALWAYS = "RECORD_TYPE_ALWAYS";
    public static final String BASIS_SUBJECT_UNDER_POLICY_AGE = "SUBJECT_UNDER_POLICY_AGE";
    public static final String BASIS_MATURE_MINOR_CAPACITY = "MATURE_MINOR_CAPACITY";
    public static final String BASIS_AGE_UNRESOLVED = "AGE_UNRESOLVED";
    public static final String BASIS_POLICY_UNAVAILABLE = "POLICY_UNAVAILABLE";

    private ConfidentialityStamper() {
    }

    /** The stamp to write onto a record. A null category means the record carries no stamp at all. */
    public record Stamp(String category, String sensitivityClass, String basis, String policyVersion) {

        /** Whether this stamp would withhold the record once class stamping is enabled. */
        public boolean protectedClass() {
            return CLASS_SPECIALLY_PROTECTED.equals(sensitivityClass);
        }
    }

    /** What the caller knows about the subject at the moment of the act. */
    public record SubjectContext(LocalDate dateOfBirth, boolean hasMatureMinorCapacity) {
    }

    /**
     * Decide the stamp for a record whose confidentiality does NOT depend on the subject's age —
     * termination authorisations and procedures, and a loss recorded as a termination. A termination
     * is confidential at 32 as much as at 15; the guardian question is not what makes it sensitive.
     */
    public static Stamp alwaysConfidential(ConfidentialityCategory category, ConfidentialCarePolicy policy) {
        String version = policy == null ? null : policy.contentVersion();
        return new Stamp(category.code(), CLASS_SPECIALLY_PROTECTED, BASIS_RECORD_TYPE_ALWAYS, version);
    }

    /**
     * Decide the stamp for a record confidential only for a young person below the governed age.
     *
     * @param category  the content category, always stamped
     * @param subject   demographics at the act; a null context or null date of birth degrades openly
     * @param actDate   the date of the clinical act, NOT today
     * @param policy    governed national policy; null or empty degrades openly
     */
    public static Stamp ageGated(ConfidentialityCategory category,
                                 SubjectContext subject,
                                 LocalDate actDate,
                                 ConfidentialCarePolicy policy) {

        String version = policy == null ? null : policy.contentVersion();

        if (policy == null) {
            return new Stamp(category.code(), CLASS_FULL_CLINICAL, BASIS_POLICY_UNAVAILABLE, null);
        }
        Optional<Integer> threshold = policy.confidentialFromGuardianAgeYears(category);
        if (threshold.isEmpty()) {
            // No governed age exists. There is no compiled-in default to fall back on, by design.
            return new Stamp(category.code(), CLASS_FULL_CLINICAL, BASIS_POLICY_UNAVAILABLE, version);
        }
        if (subject == null || subject.dateOfBirth() == null || actDate == null) {
            return new Stamp(category.code(), CLASS_FULL_CLINICAL, BASIS_AGE_UNRESOLVED, version);
        }

        int ageAtAct = Period.between(subject.dateOfBirth(), actDate).getYears();
        if (ageAtAct < threshold.get()) {
            return new Stamp(category.code(), CLASS_SPECIALLY_PROTECTED, BASIS_SUBJECT_UNDER_POLICY_AGE, version);
        }
        if (subject.hasMatureMinorCapacity()) {
            // Capacity ADDS eligibility and never removes it: a recorded determination can bring a
            // young person inside the protection, but LACKS_CAPACITY never strips an under-age
            // person of the protection the age rule already gave her (handled above, which returns
            // first).
            return new Stamp(category.code(), CLASS_SPECIALLY_PROTECTED, BASIS_MATURE_MINOR_CAPACITY, version);
        }
        // Old enough that the guardian question does not arise. The category is still recorded —
        // it is true content classification — but the record is ordinary clinical data.
        return new Stamp(category.code(), CLASS_FULL_CLINICAL, BASIS_SUBJECT_UNDER_POLICY_AGE, version);
    }

    /**
     * Apply the flip gate. Before the governance flip, a decided protection class is downgraded to
     * {@code FULL_CLINICAL} while the category and basis are preserved — so the record says what it
     * IS, and the shadow-withhold signal can report what WOULD have been withheld, without anything
     * actually disappearing from a clinician's screen.
     */
    public static Stamp gated(Stamp decided, boolean classStampingEnabled) {
        if (decided == null) {
            return null;
        }
        if (classStampingEnabled || !decided.protectedClass()) {
            return decided;
        }
        return new Stamp(decided.category(), CLASS_FULL_CLINICAL, decided.basis(), decided.policyVersion());
    }
}
