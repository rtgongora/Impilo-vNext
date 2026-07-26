package zw.gov.mohcc.impilo.paediatrics.age;

import java.time.LocalDate;

/**
 * Recommends the paediatric pathway for a child from age and presentation context.
 *
 * <p>The router is pure and deterministic: the same inputs always yield the same
 * recommendation, and every recommendation carries the age facts it was derived from so
 * a clinician can see why a pathway was proposed. It recommends — it never forces. A
 * clinician may open any pathway; the recommendation exists so the correct age-specific
 * assessment is the default rather than something to hunt for.</p>
 *
 * <p>Where the child was born preterm, growth and development are read against corrected
 * age, but <em>pathway routing uses chronological age</em>: a 40-day-old born at 30 weeks
 * is still a sick young infant by the calendar, and the infection risk that drives that
 * pathway does not wait for corrected age to catch up.</p>
 */
public final class PaediatricAgeRouter {

    /** Upper bound of the sick-young-infant pathway, inclusive. */
    public static final int SICK_YOUNG_INFANT_MAX_AGE_DAYS = 59;

    /** Upper bound of the IMNCI under-five pathway, inclusive (59 completed months). */
    public static final int UNDER_FIVE_MAX_AGE_DAYS = PaediatricAgeBand.YOUNG_CHILD.maxAgeDaysInclusive();

    private PaediatricAgeRouter() {
    }

    /** The band an age in days falls into, or null when age is unknown. */
    public static PaediatricAgeBand band(Integer ageDays) {
        if (ageDays == null || ageDays < 0) {
            return null;
        }
        for (PaediatricAgeBand candidate : PaediatricAgeBand.values()) {
            if (candidate.covers(ageDays)) {
                return candidate;
            }
        }
        return PaediatricAgeBand.ADULT_TRANSITION;
    }

    /** True for the 0–59 day window in which any illness is treated as potentially severe. */
    public static boolean isSickYoungInfantAge(Integer ageDays) {
        return ageDays != null && ageDays >= 0 && ageDays <= SICK_YOUNG_INFANT_MAX_AGE_DAYS;
    }

    public static boolean isUnderFive(Integer ageDays) {
        return ageDays != null && ageDays >= 0 && ageDays <= UNDER_FIVE_MAX_AGE_DAYS;
    }

    public static PaediatricRouting route(LocalDate dateOfBirth, LocalDate encounterDate, PresentationContext context) {
        return route(AgeCalculator.ageDays(dateOfBirth, encounterDate), null, context);
    }

    /**
     * Recommend a pathway.
     *
     * @param ageDays              chronological age in days; null when unknown
     * @param gestationalAgeWeeks  gestational age at birth in completed weeks; null when unknown
     * @param context              how the child is presenting; null is treated as {@link PresentationContext#ROUTINE}
     */
    public static PaediatricRouting route(Integer ageDays, Integer gestationalAgeWeeks, PresentationContext context) {
        PresentationContext presentation = context == null ? PresentationContext.ROUTINE : context;
        PaediatricAgeBand band = band(ageDays);
        Integer correctedAgeDays = CorrectedAge.correctedAgeDays(ageDays, gestationalAgeWeeks);
        boolean preterm = CorrectedAge.isPreterm(gestationalAgeWeeks);

        if (presentation == PresentationContext.BIRTH) {
            return routing(PaediatricPathway.BIRTH_EPISODE, band, ageDays, correctedAgeDays, preterm,
                    "Delivery presentation: newborn record opens from the birth episode.");
        }
        if (band == null) {
            return routing(PaediatricPathway.UNDETERMINED, null, null, null, preterm,
                    "Date of birth is not recorded, so no age-specific pathway can be recommended.");
        }

        if (isSickYoungInfantAge(ageDays)) {
            if (presentation == PresentationContext.ACUTE || presentation == PresentationContext.EMERGENCY) {
                return routing(PaediatricPathway.SICK_YOUNG_INFANT, band, ageDays, correctedAgeDays, preterm,
                        "Infant is " + ageDays + " days old: any illness in the first 59 days is assessed"
                                + " on the sick young infant pathway.");
            }
            return routing(PaediatricPathway.NEWBORN_ROUTINE, band, ageDays, correctedAgeDays, preterm,
                    "Infant is " + ageDays + " days old and presenting routinely: newborn assessment.");
        }

        if (isUnderFive(ageDays)) {
            if (presentation == PresentationContext.ACUTE || presentation == PresentationContext.EMERGENCY) {
                return routing(PaediatricPathway.IMNCI_UNDER_FIVE, band, ageDays, correctedAgeDays, preterm,
                        "Child is under five and unwell: IMNCI assessment covers danger signs, the main"
                                + " symptoms, nutrition and immunisation in one encounter.");
            }
            return routing(PaediatricPathway.WELL_CHILD, band, ageDays, correctedAgeDays, preterm,
                    "Child is under five and presenting routinely: growth, feeding, development and"
                            + " immunisation are reviewed together.");
        }

        return switch (band) {
            case SCHOOL_AGE -> routing(
                    presentation == PresentationContext.ROUTINE
                            ? PaediatricPathway.WELL_CHILD
                            : PaediatricPathway.GENERAL_PAEDIATRIC,
                    band, ageDays, correctedAgeDays, preterm,
                    "School-age child: full age-appropriate clerking rather than symptom classification.");
            case ADOLESCENT_YOUNG, ADOLESCENT_OLDER -> routing(PaediatricPathway.ADOLESCENT, band, ageDays,
                    correctedAgeDays, preterm,
                    "Adolescent: confidential encounter with psychosocial and safety assessment.");
            case ADULT_TRANSITION -> routing(PaediatricPathway.TRANSITION_TO_ADULT, band, ageDays, correctedAgeDays,
                    preterm, "Patient has reached adult age: care transitions to adult services.");
            default -> routing(PaediatricPathway.GENERAL_PAEDIATRIC, band, ageDays, correctedAgeDays, preterm,
                    "General paediatric assessment.");
        };
    }

    private static PaediatricRouting routing(PaediatricPathway pathway, PaediatricAgeBand band, Integer ageDays,
                                             Integer correctedAgeDays, boolean preterm, String rationale) {
        return new PaediatricRouting(
                pathway,
                band,
                ageDays,
                correctedAgeDays,
                preterm,
                isSickYoungInfantAge(ageDays),
                isUnderFive(ageDays),
                rationale);
    }

    /** How the child is presenting; shapes which pathway of the age band applies. */
    public enum PresentationContext {
        /** The delivery itself. */
        BIRTH,
        /** Scheduled or preventive contact. */
        ROUTINE,
        /** Unwell, presenting for assessment. */
        ACUTE,
        /** Emergency presentation requiring triage first. */
        EMERGENCY
    }

    /**
     * A pathway recommendation together with the age facts that produced it.
     *
     * @param sickYoungInfantWindow true when the child is 0–59 days old, whatever pathway was recommended
     */
    public record PaediatricRouting(
            PaediatricPathway pathway,
            PaediatricAgeBand ageBand,
            Integer ageDays,
            Integer correctedAgeDays,
            boolean preterm,
            boolean sickYoungInfantWindow,
            boolean underFive,
            String rationale) {
    }
}
