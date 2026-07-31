package zw.gov.mohcc.impilo.medicine.anthropometry;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Body mass index with the WHO adult classification.
 *
 * <p><strong>Source.</strong> WHO. <em>Obesity: preventing and managing the global epidemic.</em>
 * WHO Technical Report Series 894, Geneva, 2000, table 2.1. The primary text is not vendored under
 * {@code docs/reference}; the cut-points are transcribed engineering seeds pending MoHCC
 * ratification.</p>
 *
 * <p><strong>Adults only.</strong> A child's BMI means nothing against these bands: it must be read
 * against age- and sex-specific growth references, which live in {@code libs/paediatric-domain} and
 * are a different instrument entirely. An age below {@link #ADULT_FROM_AGE_YEARS} is refused rather
 * than banded, because a 7-year-old with a BMI of 16 is not "underweight" — that is a normal
 * childhood value, and recording it as underweight would manufacture a nutritional diagnosis.</p>
 *
 * <p><strong>What the bands do not know.</strong> BMI is a ratio of mass to height squared and
 * cannot distinguish muscle from fat, or peripheral from central adiposity. It over-reads in the
 * muscular and under-reads where lean mass is low, which includes people who have lost weight
 * through chronic illness. The WHO's own report notes that the cut-points were derived largely in
 * European populations, and that health risk in some Asian populations rises at lower values; WHO
 * publishes additional trigger points rather than a replacement classification, and this class
 * reports the standard bands without applying an ethnic adjustment it has no data to make. In
 * pregnancy the index is not interpretable at all, and this class has no way to know — a surface
 * that files a BMI in pregnancy must carry that caveat itself.</p>
 */
public final class BodyMassIndex {

    /** Bumped whenever a cut-point, bound or rounding rule changes a published band. */
    public static final String CONTENT_VERSION = "impilo-bmi-who-2000-1.0.0";

    /** The unit every index in this class is expressed in. */
    public static final String UNIT = "kg/m2";

    /** Below this age the WHO adult bands do not apply and the classification is refused. */
    public static final int ADULT_FROM_AGE_YEARS = 20;

    /**
     * Plausibility bounds on height in centimetres. Transcription guards, not reference ranges: a
     * height of 1.7 is a metres/centimetres mix-up, and squaring it produces an index in the
     * thousands that no band would catch.
     */
    public static final double MIN_HEIGHT_CM = 100.0d;

    /** @see #MIN_HEIGHT_CM */
    public static final double MAX_HEIGHT_CM = 250.0d;

    /** Plausibility bounds on weight in kilograms. A weight of 0.7 is a grams/kilograms mix-up. */
    public static final double MIN_WEIGHT_KG = 20.0d;

    /** @see #MIN_WEIGHT_KG */
    public static final double MAX_WEIGHT_KG = 400.0d;

    private BodyMassIndex() {
    }

    /**
     * The WHO adult classification of body mass index.
     *
     * <p>The boundaries are inclusive at the lower end, as published: a BMI of exactly 25.0 is
     * {@link #OVERWEIGHT}, not {@link #NORMAL}. The obesity classes are reported separately rather
     * than as one band, because the WHO defines them separately and the distinction carries
     * different management.</p>
     */
    public enum Category {

        /** Below 18.50. */
        UNDERWEIGHT("Underweight", "Below 18.50 kg/m²"),

        /** 18.50 to 24.99. */
        NORMAL("Normal range", "18.50 to 24.99 kg/m²"),

        /** 25.00 to 29.99. */
        OVERWEIGHT("Overweight (pre-obese)", "25.00 to 29.99 kg/m²"),

        /** 30.00 to 34.99. */
        OBESE_CLASS_I("Obese class I", "30.00 to 34.99 kg/m²"),

        /** 35.00 to 39.99. */
        OBESE_CLASS_II("Obese class II", "35.00 to 39.99 kg/m²"),

        /** 40.00 and above. */
        OBESE_CLASS_III("Obese class III", "40.00 kg/m² and above");

        private final String label;
        private final String range;

        Category(String label, String range) {
            this.label = label;
            this.range = range;
        }

        /** The band name as WHO publishes it. */
        public String label() {
            return label;
        }

        /** The published interval, so a surface can show where a value sits rather than only its name. */
        public String range() {
            return range;
        }
    }

    /**
     * Body mass index and its WHO band from height, weight and age.
     *
     * @param heightCm standing height in centimetres; null is refused, not defaulted
     * @param weightKg body weight in kilograms; null is refused, not defaulted
     * @param ageYears age in completed years; null is refused rather than assumed adult, because
     *                 assuming adulthood is what turns a normal childhood value into a diagnosis
     */
    public static Bmi of(BigDecimal heightCm, BigDecimal weightKg, Integer ageYears) {
        if (heightCm == null || weightKg == null) {
            String missing = heightCm == null && weightKg == null ? "Height and weight are"
                    : heightCm == null ? "Height is" : "Weight is";
            return refused(null, RefusalReason.INPUT_MISSING,
                    missing + " not recorded, so body mass index cannot be calculated.");
        }
        if (ageYears == null) {
            return refused(null, RefusalReason.INPUT_MISSING,
                    "Age is not recorded. The WHO bands are adult bands, and a child's index has to be"
                            + " read against age- and sex-specific growth references instead, so the"
                            + " classification cannot be issued without knowing which applies.");
        }

        double h = heightCm.doubleValue();
        double w = weightKg.doubleValue();

        if (h < MIN_HEIGHT_CM || h > MAX_HEIGHT_CM) {
            return refused(null, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A height of " + heightCm.toPlainString() + " cm is outside the plausible range "
                            + MIN_HEIGHT_CM + "–" + MAX_HEIGHT_CM + " cm. This is usually a"
                            + " metres/centimetres mix-up, and squaring it would produce an index no"
                            + " band would catch.");
        }
        if (w < MIN_WEIGHT_KG || w > MAX_WEIGHT_KG) {
            return refused(null, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A weight of " + weightKg.toPlainString() + " kg is outside the plausible range "
                            + MIN_WEIGHT_KG + "–" + MAX_WEIGHT_KG + " kg. This is usually a"
                            + " grams/kilograms mix-up; the value is not extrapolated.");
        }

        BigDecimal heightMetres = heightCm.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal index = weightKg.divide(heightMetres.multiply(heightMetres), 1, RoundingMode.HALF_UP);

        if (ageYears < ADULT_FROM_AGE_YEARS) {
            // The index itself is arithmetic and is returned; only the band is withheld, because the
            // band is the part that would be wrong. A paediatric surface can plot the value against
            // the growth reference it owns.
            return new Bmi(index, null, RefusalReason.INSTRUMENT_NOT_APPLICABLE,
                    "The index is " + index.toPlainString() + " " + UNIT + ", but the WHO adult bands"
                            + " do not apply at " + ageYears + " years. A child's index must be read"
                            + " against age- and sex-specific growth references; banding it here would"
                            + " record a normal childhood value as a nutritional diagnosis.",
                    CONTENT_VERSION);
        }

        return new Bmi(index, classify(index), null, null, CONTENT_VERSION);
    }

    /**
     * The WHO band for an index already calculated elsewhere.
     *
     * <p>Exposed so a surface holding a recorded BMI can band it without re-deriving one from a
     * height and weight it may not have. There is no age parameter and so no adult check: a caller
     * using this is asserting the value is an adult's.</p>
     */
    public static Category classify(BigDecimal bmi) {
        double value = bmi.doubleValue();
        if (value < 18.50d) {
            return Category.UNDERWEIGHT;
        }
        if (value < 25.00d) {
            return Category.NORMAL;
        }
        if (value < 30.00d) {
            return Category.OVERWEIGHT;
        }
        if (value < 35.00d) {
            return Category.OBESE_CLASS_I;
        }
        if (value < 40.00d) {
            return Category.OBESE_CLASS_II;
        }
        return Category.OBESE_CLASS_III;
    }

    private static Bmi refused(BigDecimal index, RefusalReason reason, String explanation) {
        return new Bmi(index, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A body mass index and its band, or a stated reason there is no band.
     *
     * <p>Note the asymmetry: {@link #bmi()} may be present while {@link #category()} is not. That is
     * the paediatric case — the arithmetic is valid, the classification is not — and it is why
     * {@link #computed()} tests the band rather than the number.</p>
     *
     * @param bmi            the index in {@link #UNIT} to one decimal; null when it could not be
     *                       calculated at all
     * @param category       the WHO band; null when refused
     * @param reason         machine-readable refusal code; null when banded
     * @param explanation    clinician-readable refusal text; null when banded
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Bmi(BigDecimal bmi,
                      Category category,
                      RefusalReason reason,
                      String explanation,
                      String contentVersion) {

        /** True when a band is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return category != null;
        }

        /** True when the arithmetic succeeded, whether or not the band could be issued. */
        public boolean hasIndex() {
            return bmi != null;
        }
    }
}
