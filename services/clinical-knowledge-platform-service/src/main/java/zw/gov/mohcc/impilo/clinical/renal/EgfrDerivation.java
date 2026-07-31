package zw.gov.mohcc.impilo.clinical.renal;

import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.medicine.common.Sex;
import zw.gov.mohcc.impilo.medicine.renal.EgfrCalculator;
import zw.gov.mohcc.impilo.medicine.renal.SerumCreatinine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Derives an eGFR where a laboratory has reported a creatinine but not a filtration rate.
 *
 * <p>This closes a gap the engines have carried: several rules are keyed on an eGFR that nothing in
 * the platform ever produced, so they were silent whenever the calling system did not happen to send
 * one. A clinic that measures creatinine — the ordinary case — got no renal alert, no metformin gate
 * and a renal panel reading "not available".</p>
 *
 * <h2>Two rules this must not break</h2>
 * <p><strong>A supplied value always wins.</strong> If a caller sent an eGFR, that is what the
 * laboratory reported, possibly by an equation this platform does not implement, and it is not
 * second-guessed. Derivation only fills a hole.</p>
 *
 * <p><strong>A refusal leaves the hole.</strong> When the calculator declines — no creatinine, no
 * age, no sex, a value outside physiological range — nothing is written. Absent stays absent, so a
 * rule that cannot assess renal function continues to say so rather than falling silent against a
 * fabricated normal. That distinction is the whole reason the calculator refuses by name instead of
 * returning zero.</p>
 *
 * <p>Every derived value is labelled {@link #DERIVED_SOURCE} wherever the consuming surface has
 * somewhere to put it. A clinician reading an eGFR is entitled to know whether a laboratory measured
 * it or this platform inferred it from a creatinine.</p>
 */
public final class EgfrDerivation {

    /** The key an eGFR is carried under in derived-value maps and rule fact maps. */
    public static final String EGFR_KEY = "egfr";

    /** Marks a value this platform calculated rather than one a laboratory reported. */
    public static final String DERIVED_SOURCE = "CKD-EPI 2021 (derived by Impilo from serum creatinine)";

    private EgfrDerivation() {
    }

    /**
     * An eGFR derived from the first usable creatinine among {@code observations}.
     *
     * <p>An absent result means "could not derive", never "normal". Callers must leave their eGFR
     * absent rather than substituting a value.</p>
     *
     * @param observations interpreted observations to search for a creatinine
     * @param ageYears     age in completed years; absent yields no derivation
     * @param sex          the equation variable. UNKNOWN yields no derivation, because the 2021
     *                     equation applies sex-specific coefficients and there is no neutral one
     */
    public static Derived fromObservations(
            List<InterpretedObservation> observations,
            Integer ageYears,
            zw.gov.mohcc.impilo.clinical.interpretation.model.Sex sex) {
        return fromCreatinine(creatinineMicromolPerLitre(observations), ageYears, toDomainSex(sex));
    }

    /**
     * An eGFR from a creatinine already in µmol/L.
     *
     * <p>The calculator's refusal reason is carried on the result rather than discarded, so a
     * surface can explain why no figure appeared and a log can record that derivation was attempted
     * and why it did not produce one.</p>
     */
    public static Derived fromCreatinine(BigDecimal creatinineMicromolPerLitre,
                                         Integer ageYears,
                                         Sex sex) {
        if (creatinineMicromolPerLitre == null || ageYears == null || sex == null) {
            return Derived.notAttempted();
        }
        var result = EgfrCalculator.fromCreatinineMicromolPerLitre(
                creatinineMicromolPerLitre, ageYears, sex);
        if (!result.computed()) {
            return new Derived(null, null, null, result.reason().name(), result.explanation());
        }
        return new Derived(result.egfr(), result.kdigoStage().name(), result.contentVersion(),
                null, null);
    }

    /**
     * The first usable creatinine among the observations, in µmol/L.
     *
     * <p>An observation whose unit {@link SerumCreatinine} does not recognise is skipped rather than
     * assumed, and the search continues — a record may carry an unreadable historic value and a
     * readable current one.</p>
     */
    public static BigDecimal creatinineMicromolPerLitre(List<InterpretedObservation> observations) {
        if (observations == null) {
            return null;
        }
        for (InterpretedObservation o : observations) {
            if (o == null || !isCreatinine(o)) {
                continue;
            }
            BigDecimal value = SerumCreatinine.toMicromolPerLitre(o.value(), o.unit());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean isCreatinine(InterpretedObservation o) {
        return SerumCreatinine.isCreatinineCode(o.loincCode())
                || SerumCreatinine.isCreatinineName(o.displayName());
    }

    private static Sex toDomainSex(zw.gov.mohcc.impilo.clinical.interpretation.model.Sex sex) {
        if (sex == null) {
            return null;
        }
        return switch (sex) {
            case MALE -> Sex.MALE;
            case FEMALE -> Sex.FEMALE;
            case UNKNOWN -> null;
        };
    }

    /**
     * A derived eGFR, or the stated reason there is none.
     *
     * @param egfr           the rate in mL/min/1.73m²; null when nothing was derived
     * @param kdigoStage     the KDIGO GFR category; null when nothing was derived
     * @param contentVersion the calculator version that produced it; null when nothing was derived
     * @param refusalReason  the calculator's refusal code when it declined; null when it was never
     *                       asked, and null when it answered
     * @param refusalDetail  the calculator's explanation, on the same terms
     */
    public record Derived(BigDecimal egfr,
                          String kdigoStage,
                          String contentVersion,
                          String refusalReason,
                          String refusalDetail) {

        private static final Derived NOT_ATTEMPTED = new Derived(null, null, null, null, null);

        /** No creatinine, age or sex was available, so the calculator was not called. */
        static Derived notAttempted() {
            return NOT_ATTEMPTED;
        }

        /** True when a rate is present. False means the eGFR must be left absent. */
        public boolean present() {
            return egfr != null;
        }

        /** The rate as a double, for the maps that carry doubles. Null when absent. */
        public Double asDouble() {
            return egfr == null ? null : egfr.doubleValue();
        }
    }
}
