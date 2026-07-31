package zw.gov.mohcc.impilo.medicine.thrombosis;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wells score for pulmonary embolism.
 *
 * <p><strong>Source.</strong> Wells PS, Anderson DR, Rodger M, et al. <em>Derivation of a simple
 * clinical model to categorize patients probability of pulmonary embolism.</em> Thromb Haemost
 * 2000;83:416–420, with the two-level simplification in common use. The primary texts are not
 * vendored under {@code docs/reference}; the item weights are transcribed engineering seeds pending
 * MoHCC ratification.</p>
 *
 * <p><strong>Half-point weights are real and must not be rounded away.</strong> Four items carry 1.5
 * and two carry 3. Storing the total as an integer loses the distinction between a 4 and a 4.5, which
 * straddles the three-level moderate/high boundary. The total here is a {@link BigDecimal} for that
 * reason, and callers that persist it must preserve the scale.</p>
 *
 * <p><strong>Both banding models are returned, and they disagree by design.</strong> The two-level
 * model splits at 4 into likely and unlikely; the three-level model splits at 2 and 6 into low,
 * moderate and high. A surface must state which model it is presenting. Showing "moderate" from one
 * model beside a decision rule written for the other is how a score gets misread.</p>
 *
 * <p><strong>What this class will not do.</strong> It sums the items and names both bands. It does
 * not order imaging, does not apply PERC, does not interpret a D-dimer, and does not rule out PE.</p>
 */
public final class WellsPeScore {

    /** Bumped whenever an item weight or banding rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-wells-pe-2000-1.0.0";

    private WellsPeScore() {
    }

    /** The scored items with their published fractional weights. */
    public enum Criterion {

        /** Clinical signs and symptoms of DVT: leg swelling and pain with palpation of deep veins. */
        CLINICAL_SIGNS_OF_DVT("3", "Clinical signs and symptoms of DVT"),

        /** PE is the most likely diagnosis, or equally likely to the alternatives. */
        PE_MOST_LIKELY_DIAGNOSIS("3", "PE is the most likely diagnosis, or equally likely"),

        /** Heart rate above 100 beats per minute. */
        HEART_RATE_OVER_100("1.5", "Heart rate >100/min"),

        /** Immobilisation for 3 days or more, or surgery within the previous 4 weeks. */
        IMMOBILISATION_OR_RECENT_SURGERY("1.5",
                "Immobilisation ≥3 days or surgery within the previous 4 weeks"),

        /** Previously objectively diagnosed PE or DVT. */
        PREVIOUS_PE_OR_DVT("1.5", "Previously objectively diagnosed PE or DVT"),

        /** Haemoptysis. */
        HAEMOPTYSIS("1", "Haemoptysis"),

        /** Malignancy with treatment within 6 months, or palliative. */
        MALIGNANCY("1", "Malignancy (treated within 6 months, or palliative)");

        private final BigDecimal points;
        private final String label;

        Criterion(String points, String label) {
            this.points = new BigDecimal(points);
            this.label = label;
        }

        /** The item's published weight, which may be fractional. */
        public BigDecimal points() {
            return points;
        }

        /** The item wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /** The two-level model: likely or unlikely. Splits at 4. */
    public enum TwoLevelProbability {

        /** Total above 4: PE likely. */
        LIKELY,

        /** Total 4 or below: PE unlikely. */
        UNLIKELY
    }

    /** The three-level model: low, moderate or high. Splits at 2 and 6. */
    public enum ThreeLevelProbability {

        /** Total below 2. */
        LOW,

        /** Total 2 to 6 inclusive. */
        MODERATE,

        /** Total above 6. */
        HIGH
    }

    /**
     * Wells PE from the set of criteria the clinician has confirmed present.
     *
     * @param present the criteria found present; an empty set is a valid answer meaning none were
     *                found, but null is refused because "none present" and "not assessed" are
     *                different claims
     */
    public static Scoring of(Set<Criterion> present) {
        if (present == null) {
            return new Scoring(null, null, null, null, RefusalReason.INPUT_MISSING,
                    "No Wells criteria were recorded. An empty assessment is not the same as an"
                            + " assessment that found nothing, and the score is not calculated from"
                            + " one.",
                    CONTENT_VERSION);
        }

        Set<Criterion> confirmed = present.isEmpty()
                ? EnumSet.noneOf(Criterion.class)
                : EnumSet.copyOf(present);

        BigDecimal total = confirmed.stream()
                .map(Criterion::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.UNNECESSARY);

        TwoLevelProbability twoLevel = total.compareTo(BigDecimal.valueOf(4)) > 0
                ? TwoLevelProbability.LIKELY
                : TwoLevelProbability.UNLIKELY;

        ThreeLevelProbability threeLevel = total.compareTo(BigDecimal.valueOf(2)) < 0
                ? ThreeLevelProbability.LOW
                : total.compareTo(BigDecimal.valueOf(6)) <= 0
                        ? ThreeLevelProbability.MODERATE
                        : ThreeLevelProbability.HIGH;

        return new Scoring(total, twoLevel, threeLevel, new LinkedHashSet<>(confirmed), null, null,
                CONTENT_VERSION);
    }

    /**
     * A Wells PE score, or a stated reason there is none.
     *
     * <p>Both banding models are present. A surface must name which one it is displaying; they
     * disagree at the boundaries and that disagreement is a property of the published instrument,
     * not an inconsistency to be smoothed over.</p>
     *
     * @param score          the total at scale 1, preserving the half-point weights; null when refused
     * @param twoLevel       the likely/unlikely band; null when refused
     * @param threeLevel     the low/moderate/high band; null when refused
     * @param criteriaMet    the criteria that contributed, carried so a surface can show its working;
     *                       null when refused
     * @param reason         machine-readable refusal code; null when computed
     * @param explanation    clinician-readable refusal text; null when computed
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(BigDecimal score,
                          TwoLevelProbability twoLevel,
                          ThreeLevelProbability threeLevel,
                          Set<Criterion> criteriaMet,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return score != null;
        }
    }
}
