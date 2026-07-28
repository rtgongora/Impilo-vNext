package zw.gov.mohcc.impilo.medicine.cardio;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ten-year cardiovascular risk banding in the shape of the WHO/ISH charts for the AFR-E region.
 *
 * <h2>READ THIS BEFORE USING OR CITING THIS CLASS</h2>
 *
 * <p><strong>The WHO chart cell values are not in this repository and are not in this class.</strong>
 * Nothing is vendored under {@code docs/reference} for cardiovascular risk. What is implemented here
 * is the <em>documented risk-factor model</em> the charts are built from — the same variables, the
 * same age, blood-pressure, cholesterol and BMI bands, the same laboratory/non-laboratory split, the
 * same five output bands — combined by an additive points model written by Impilo engineering. The
 * points are <strong>not</strong> WHO's. They are an ordinal severity index, calibrated only so that
 * the model is monotone in every risk factor and lands on the correct band at the two corners of the
 * chart that are qualitatively unambiguous (a 40-year-old non-smoking, non-diabetic woman with
 * optimal blood pressure and cholesterol is in the lowest band; a 70-year-old male smoker with
 * diabetes, severe hypertension and the highest cholesterol column is in the highest).</p>
 *
 * <p><strong>This class therefore does not, and will not, produce a percentage.</strong> There is no
 * accessor for one anywhere in this package, by design. A number like "23%" carries an implied
 * precision this model has not earned, and once written to a record it becomes indistinguishable
 * from a real WHO chart reading. The existing {@code cvdRisk10yrPercent} field in the medicine CDS
 * surface cannot be populated from this calculator: it must stay clinician-entered, or be replaced
 * by a band, until the WHO charts are vendored and the cell lookup implemented.</p>
 *
 * <p>{@link Basis} is stamped on every result and is currently always
 * {@link Basis#IMPILO_ENGINEERING_SEED_MODEL}. When the AFR-E chart cells are vendored and read
 * directly, that value becomes {@link Basis#WHO_CHART_CELL} and any surface that renders the seed
 * caveat can stop. Until then a caller that hides {@link Result#caveat()} is misrepresenting the
 * result. {@link #CHART_CELLS_VENDORED} exists so a guard can assert this in a build.</p>
 *
 * <h2>What is faithful</h2>
 *
 * <p>Sources for the model's <em>structure</em>: WHO CVD Risk Chart Working Group, Lancet Glob Health
 * 2019;7:e1332–45; WHO <em>HEARTS technical package — risk-based CVD management</em> (2020); and the
 * earlier WHO/ISH 2007 regional charts from which the AFR-E region name comes. From those, faithfully
 * reproduced here:</p>
 * <ul>
 *   <li>Two chart variants. The laboratory variant uses age, sex, smoking, systolic blood pressure,
 *       total cholesterol and diabetes. The non-laboratory variant uses age, sex, smoking, systolic
 *       blood pressure and body mass index, and <strong>deliberately omits diabetes</strong>, because
 *       diagnosing diabetes needs the laboratory the variant exists to do without. Adding a diabetes
 *       term to the non-laboratory model would defeat its purpose.</li>
 *   <li>Five-year age rows from 40 to 74. The charts publish nothing outside that span.</li>
 *   <li>Systolic blood pressure columns at &lt;120, 120–139, 140–159, 160–179 and ≥180 mmHg.</li>
 *   <li>Total cholesterol columns at &lt;4, 4–4.9, 5–5.9, 6–6.9 and ≥7 mmol/L.</li>
 *   <li>Body mass index columns at &lt;20, 20–24, 25–29, 30–35 and &gt;35 kg/m².</li>
 *   <li>Applicability only to people without established cardiovascular disease.</li>
 *   <li>The five output bands of {@link CvdRiskBand}.</li>
 * </ul>
 *
 * <h2>What this class will not do</h2>
 *
 * <p>It bands a risk; it does not act on one. Which band starts a statin, which band warrants a
 * blood-pressure target, which band triggers recall — all policy, all governed CKP content. The
 * band boundaries themselves are the chart's published legend and so are arithmetic.</p>
 */
public final class CvdRiskCalculator {

    /**
     * Bumped whenever a points value or band cut-point changes, because a stored band is not
     * comparable across such a change for the same patient.
     */
    public static final String CONTENT_VERSION = "impilo-cvd-risk-seed-1.0.0";

    /** The WHO region this model is nominally scoped to, from the WHO/ISH regional chart naming. */
    public static final String WHO_REGION = "AFR-E";

    /**
     * False, and it is meant to be read in a build guard. Flip it only in the commit that vendors
     * the WHO AFR-E chart cells under {@code docs/reference} and replaces the points model with a
     * cell lookup. While it is false, every result carries a caveat and no percentage exists.
     */
    public static final boolean CHART_CELLS_VENDORED = false;

    /** First age row the charts publish. */
    public static final int MIN_AGE_YEARS = 40;

    /** Last age row the charts publish. */
    public static final int MAX_AGE_YEARS = 74;

    /** Transcription guards on systolic blood pressure, in mmHg. Not a clinical normal range. */
    public static final double MIN_SYSTOLIC_MMHG = 70.0d;

    /** @see #MIN_SYSTOLIC_MMHG */
    public static final double MAX_SYSTOLIC_MMHG = 300.0d;

    /** Transcription guards on total cholesterol, in mmol/L. A value of 200 is an mg/dL mix-up. */
    public static final double MIN_TOTAL_CHOLESTEROL_MMOL_PER_L = 1.0d;

    /** @see #MIN_TOTAL_CHOLESTEROL_MMOL_PER_L */
    public static final double MAX_TOTAL_CHOLESTEROL_MMOL_PER_L = 20.0d;

    /** Transcription guards on body mass index, in kg/m². */
    public static final double MIN_BMI_KG_PER_M2 = 10.0d;

    /** @see #MIN_BMI_KG_PER_M2 */
    public static final double MAX_BMI_KG_PER_M2 = 70.0d;

    // --- the seed points model ---------------------------------------------------------------
    // Every table below is Impilo engineering, not WHO. They are public so that a reviewer can
    // read the whole model without reading the code, and so a test can assert monotonicity.

    /** Points for each five-year age row from 40–44 up to 70–74. Impilo seed, not WHO. */
    public static final int[] AGE_ROW_POINTS = {0, 2, 4, 6, 8, 10, 12};

    /** Points added for male sex. Impilo seed, not WHO. */
    public static final int MALE_POINTS = 2;

    /** Points added for current smoking. Impilo seed, not WHO. */
    public static final int CURRENT_SMOKER_POINTS = 4;

    /** Points added for diabetes, laboratory variant only. Impilo seed, not WHO. */
    public static final int DIABETES_POINTS = 4;

    /** Points for the five systolic blood-pressure columns, ascending. Impilo seed, not WHO. */
    public static final int[] SYSTOLIC_COLUMN_POINTS = {0, 2, 4, 6, 8};

    /** Points for the five total-cholesterol columns, ascending. Impilo seed, not WHO. */
    public static final int[] CHOLESTEROL_COLUMN_POINTS = {0, 1, 2, 3, 4};

    /** Points for the five body-mass-index columns, ascending. Impilo seed, not WHO. */
    public static final int[] BMI_COLUMN_POINTS = {0, 0, 1, 2, 3};

    /**
     * Highest points obtainable on the laboratory variant: oldest row, male, smoker, diabetic,
     * top blood-pressure and top cholesterol column.
     */
    public static final int LABORATORY_MAX_POINTS = 12 + MALE_POINTS + CURRENT_SMOKER_POINTS
            + DIABETES_POINTS + 8 + 4;

    /** Highest points obtainable on the non-laboratory variant. */
    public static final int NON_LABORATORY_MAX_POINTS = 12 + MALE_POINTS + CURRENT_SMOKER_POINTS + 8 + 3;

    /**
     * Lower bound (inclusive) of each band on the laboratory points scale, ascending by band.
     * Impilo seed. Chosen so the two chart corners land in the right bands and the intermediate
     * spacing is even; there is no claim that any interior cell matches WHO.
     */
    public static final int[] LABORATORY_BAND_FLOORS = {0, 9, 14, 20, 25};

    /** As {@link #LABORATORY_BAND_FLOORS}, on the shorter non-laboratory points scale. */
    public static final int[] NON_LABORATORY_BAND_FLOORS = {0, 8, 13, 18, 22};

    private static final String SEED_CAVEAT =
            "Estimated by the Impilo seed risk-factor model, not read from a WHO chart. The WHO AFR-E"
                    + " chart cell values are not vendored in this system, so this band reproduces the"
                    + " published model's variables and direction but not its calibration. Treat it as"
                    + " an indication to check the printed chart, not as a substitute for it. No"
                    + " percentage is produced, deliberately.";

    private CvdRiskCalculator() {
    }

    /**
     * Laboratory-based 10-year risk band: age, sex, smoking, systolic blood pressure, total
     * cholesterol and diabetes.
     *
     * <p>Model structure from the WHO 2019 CVD risk charts (Lancet Glob Health 2019;7:e1332–45) and
     * the WHO HEARTS risk-based CVD management module. Chart cells <strong>not vendored</strong>
     * under {@code docs/reference}; the combination is an Impilo engineering seed pending MoHCC
     * ratification. See the class javadoc before citing the output.</p>
     *
     * @param ageYears                   age in completed years; refused outside 40–74, the published rows
     * @param sex                        equation variable, not recorded gender
     * @param currentSmoker              null is refused, not read as a non-smoker
     * @param diabetes                   null is refused; the laboratory chart has separate diabetes panels
     * @param systolicBpMmHg             the reading the chart is entered with
     * @param totalCholesterolMmolPerL   total, not LDL, cholesterol
     * @param establishedCvd             TRUE refuses: the charts estimate a first event only
     */
    public static Result laboratory(Integer ageYears,
                                    Sex sex,
                                    Boolean currentSmoker,
                                    Boolean diabetes,
                                    BigDecimal systolicBpMmHg,
                                    BigDecimal totalCholesterolMmolPerL,
                                    Boolean establishedCvd) {
        Result common = validateCommon(Variant.LABORATORY, ageYears, sex, currentSmoker,
                systolicBpMmHg, establishedCvd);
        if (common != null) {
            return common;
        }
        if (diabetes == null) {
            return refused(Variant.LABORATORY, RefusalReason.INPUT_MISSING,
                    "Diabetes status is not recorded. The laboratory chart is published as separate"
                            + " panels for people with and without diabetes, so there is no cell to read"
                            + " without it.");
        }
        if (totalCholesterolMmolPerL == null) {
            return refused(Variant.LABORATORY, RefusalReason.INPUT_MISSING,
                    "Total cholesterol is not recorded. Use the non-laboratory chart instead of"
                            + " assuming a value.");
        }
        double cholesterol = totalCholesterolMmolPerL.doubleValue();
        if (cholesterol < MIN_TOTAL_CHOLESTEROL_MMOL_PER_L || cholesterol > MAX_TOTAL_CHOLESTEROL_MMOL_PER_L) {
            return refused(Variant.LABORATORY, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Total cholesterol of " + totalCholesterolMmolPerL.toPlainString() + " mmol/L is"
                            + " outside the plausible range " + MIN_TOTAL_CHOLESTEROL_MMOL_PER_L + "–"
                            + MAX_TOTAL_CHOLESTEROL_MMOL_PER_L + " mmol/L. A value in the hundreds is"
                            + " mg/dL entered as mmol/L.");
        }

        int points = ageRowPoints(ageYears)
                + (sex == Sex.MALE ? MALE_POINTS : 0)
                + (Boolean.TRUE.equals(currentSmoker) ? CURRENT_SMOKER_POINTS : 0)
                + (Boolean.TRUE.equals(diabetes) ? DIABETES_POINTS : 0)
                + SYSTOLIC_COLUMN_POINTS[systolicColumn(systolicBpMmHg.doubleValue())]
                + CHOLESTEROL_COLUMN_POINTS[cholesterolColumn(cholesterol)];

        return computed(Variant.LABORATORY, points);
    }

    /**
     * Non-laboratory 10-year risk band: age, sex, smoking, systolic blood pressure and body mass
     * index. No cholesterol, and — following the published chart — no diabetes term.
     *
     * <p>Model structure from the WHO 2019 CVD risk charts (Lancet Glob Health 2019;7:e1332–45).
     * Chart cells <strong>not vendored</strong> under {@code docs/reference}; the combination is an
     * Impilo engineering seed pending MoHCC ratification. See the class javadoc before citing.</p>
     *
     * <p>This variant exists for the clinic that has a tape measure, a scale and a blood-pressure
     * cuff and no laboratory, which describes most primary-care contacts in Zimbabwe. It is not a
     * degraded version of the laboratory chart to be used when someone forgot to order bloods; the
     * WHO publishes it as its own instrument.</p>
     */
    public static Result nonLaboratory(Integer ageYears,
                                       Sex sex,
                                       Boolean currentSmoker,
                                       BigDecimal systolicBpMmHg,
                                       BigDecimal bmiKgPerM2,
                                       Boolean establishedCvd) {
        Result common = validateCommon(Variant.NON_LABORATORY, ageYears, sex, currentSmoker,
                systolicBpMmHg, establishedCvd);
        if (common != null) {
            return common;
        }
        if (bmiKgPerM2 == null) {
            return refused(Variant.NON_LABORATORY, RefusalReason.INPUT_MISSING,
                    "Body mass index is not recorded, and it is the non-laboratory chart's substitute"
                            + " for cholesterol. Record weight and height, or use the laboratory chart.");
        }
        double bmi = bmiKgPerM2.doubleValue();
        if (bmi < MIN_BMI_KG_PER_M2 || bmi > MAX_BMI_KG_PER_M2) {
            return refused(Variant.NON_LABORATORY, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A body mass index of " + bmiKgPerM2.toPlainString() + " kg/m² is outside the"
                            + " plausible range " + MIN_BMI_KG_PER_M2 + "–" + MAX_BMI_KG_PER_M2
                            + " kg/m². Check the height, which is usually the field entered in the"
                            + " wrong unit.");
        }

        int points = ageRowPoints(ageYears)
                + (sex == Sex.MALE ? MALE_POINTS : 0)
                + (Boolean.TRUE.equals(currentSmoker) ? CURRENT_SMOKER_POINTS : 0)
                + SYSTOLIC_COLUMN_POINTS[systolicColumn(systolicBpMmHg.doubleValue())]
                + BMI_COLUMN_POINTS[bmiColumn(bmi)];

        return computed(Variant.NON_LABORATORY, points);
    }

    /**
     * Body mass index in kg/m² from weight in kilograms and height in centimetres.
     *
     * <p>Plain arithmetic, {@code weight / height²}, offered here so the non-laboratory chart's
     * input can be derived without a second implementation drifting from this one. Returns null
     * when either measurement is absent or not positive — there is no defaulted body.</p>
     */
    public static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null
                || weightKg.signum() <= 0 || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightMetres = heightCm.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return weightKg.divide(heightMetres.multiply(heightMetres), 1, RoundingMode.HALF_UP);
    }

    // --- validation shared by both variants ----------------------------------------------------

    private static Result validateCommon(Variant variant,
                                         Integer ageYears,
                                         Sex sex,
                                         Boolean currentSmoker,
                                         BigDecimal systolicBpMmHg,
                                         Boolean establishedCvd) {
        if (Boolean.TRUE.equals(establishedCvd)) {
            return refused(variant, RefusalReason.INSTRUMENT_NOT_APPLICABLE,
                    "This person already has established cardiovascular disease. The WHO risk charts"
                            + " estimate the 10-year risk of a first cardiovascular event and do not"
                            + " apply once one has occurred; secondary prevention is not a risk score.");
        }
        if (ageYears == null) {
            return refused(variant, RefusalReason.INPUT_MISSING,
                    "Age is not recorded, and the chart is entered on an age row.");
        }
        if (ageYears < MIN_AGE_YEARS || ageYears > MAX_AGE_YEARS) {
            return refused(variant, RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                    "The WHO cardiovascular risk charts publish rows for ages " + MIN_AGE_YEARS + " to "
                            + MAX_AGE_YEARS + " only, and this person is " + ageYears + ". The chart is"
                            + " not extrapolated: below 40 the 10-year estimate is low in almost everyone"
                            + " and hides a high lifetime risk, and above 74 the model was not fitted.");
        }
        if (sex == null) {
            return refused(variant, RefusalReason.INPUT_MISSING,
                    "Sex is not recorded. The charts are published as separate male and female panels.");
        }
        if (currentSmoker == null) {
            return refused(variant, RefusalReason.INPUT_MISSING,
                    "Smoking status is not recorded. It is not assumed to be non-smoking: that"
                            + " assumption moves most people down a band.");
        }
        if (systolicBpMmHg == null) {
            return refused(variant, RefusalReason.INPUT_MISSING,
                    "Systolic blood pressure is not recorded, and it is a chart column.");
        }
        double systolic = systolicBpMmHg.doubleValue();
        if (systolic < MIN_SYSTOLIC_MMHG || systolic > MAX_SYSTOLIC_MMHG) {
            return refused(variant, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A systolic blood pressure of " + systolicBpMmHg.toPlainString() + " mmHg is outside"
                            + " the plausible range " + MIN_SYSTOLIC_MMHG + "–" + MAX_SYSTOLIC_MMHG
                            + " mmHg. Check whether the diastolic reading has been entered here.");
        }
        return null;
    }

    // --- banding -------------------------------------------------------------------------------

    private static int ageRowPoints(int ageYears) {
        int row = (ageYears - MIN_AGE_YEARS) / 5;
        if (row < 0) {
            row = 0;
        }
        if (row >= AGE_ROW_POINTS.length) {
            row = AGE_ROW_POINTS.length - 1;
        }
        return AGE_ROW_POINTS[row];
    }

    /** Index into the five published systolic columns: &lt;120, 120–139, 140–159, 160–179, ≥180. */
    static int systolicColumn(double systolic) {
        if (systolic < 120.0d) {
            return 0;
        }
        if (systolic < 140.0d) {
            return 1;
        }
        if (systolic < 160.0d) {
            return 2;
        }
        if (systolic < 180.0d) {
            return 3;
        }
        return 4;
    }

    /** Index into the five published cholesterol columns: &lt;4, 4–4.9, 5–5.9, 6–6.9, ≥7 mmol/L. */
    static int cholesterolColumn(double totalCholesterolMmolPerL) {
        if (totalCholesterolMmolPerL < 4.0d) {
            return 0;
        }
        if (totalCholesterolMmolPerL < 5.0d) {
            return 1;
        }
        if (totalCholesterolMmolPerL < 6.0d) {
            return 2;
        }
        if (totalCholesterolMmolPerL < 7.0d) {
            return 3;
        }
        return 4;
    }

    /** Index into the five published BMI columns: &lt;20, 20–24, 25–29, 30–35, &gt;35 kg/m². */
    static int bmiColumn(double bmi) {
        if (bmi < 20.0d) {
            return 0;
        }
        if (bmi < 25.0d) {
            return 1;
        }
        if (bmi < 30.0d) {
            return 2;
        }
        if (bmi <= 35.0d) {
            return 3;
        }
        return 4;
    }

    private static Result computed(Variant variant, int points) {
        int[] floors = variant == Variant.LABORATORY ? LABORATORY_BAND_FLOORS : NON_LABORATORY_BAND_FLOORS;
        int max = variant == Variant.LABORATORY ? LABORATORY_MAX_POINTS : NON_LABORATORY_MAX_POINTS;
        CvdRiskBand[] bands = CvdRiskBand.values();
        CvdRiskBand band = bands[0];
        for (int i = 0; i < floors.length; i++) {
            if (points >= floors[i]) {
                band = bands[i];
            }
        }
        return new Result(band, variant, points, max, Basis.IMPILO_ENGINEERING_SEED_MODEL,
                SEED_CAVEAT, null, null, WHO_REGION, CONTENT_VERSION);
    }

    private static Result refused(Variant variant, RefusalReason reason, String explanation) {
        return new Result(null, variant, null, null, Basis.IMPILO_ENGINEERING_SEED_MODEL,
                SEED_CAVEAT, reason, explanation, WHO_REGION, CONTENT_VERSION);
    }

    // --- value types ---------------------------------------------------------------------------

    /** Which of the two published charts was entered. */
    public enum Variant {

        /** Age, sex, smoking, systolic blood pressure, total cholesterol, diabetes. */
        LABORATORY,

        /** Age, sex, smoking, systolic blood pressure, body mass index. No diabetes term, by design. */
        NON_LABORATORY
    }

    /** Where the band actually came from. Stamped on every result so it cannot be assumed. */
    public enum Basis {

        /**
         * Read from a vendored WHO AFR-E chart cell. Not yet reachable — no chart is vendored.
         * The value exists so the enum does not have to change shape when one is.
         */
        WHO_CHART_CELL,

        /**
         * Produced by the Impilo additive points model described in
         * {@link CvdRiskCalculator}. Faithful to the published model's variables and direction;
         * not calibrated to WHO's cell values.
         */
        IMPILO_ENGINEERING_SEED_MODEL
    }

    /**
     * A risk band, or a stated reason there is none.
     *
     * <p>There is no percentage component and none may be added while {@link #CHART_CELLS_VENDORED}
     * is false. {@link #points()} is exposed for auditing the seed model, not for display: it is an
     * index on an arbitrary scale and means nothing to a clinician.</p>
     *
     * @param band           the 10-year risk band; null when refused
     * @param variant        which chart was entered
     * @param points         the seed model's ordinal index; null when refused. Not a risk figure.
     * @param maxPoints      the highest index the variant can produce, for context on {@code points}
     * @param basis          where the band came from — always the seed model today
     * @param caveat         the sentence a surface must show alongside a seed-model band
     * @param reason         machine-readable refusal code; null when computed
     * @param explanation    clinician-readable refusal text; null when computed
     * @param whoRegion      the WHO region the model is scoped to
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Result(CvdRiskBand band,
                         Variant variant,
                         Integer points,
                         Integer maxPoints,
                         Basis basis,
                         String caveat,
                         RefusalReason reason,
                         String explanation,
                         String whoRegion,
                         String contentVersion) {

        /** True when a band is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return band != null;
        }

        /**
         * True while the band is a seed-model estimate rather than a chart reading, and so must be
         * displayed with {@link #caveat()}.
         */
        public boolean requiresSeedCaveat() {
            return basis == Basis.IMPILO_ENGINEERING_SEED_MODEL;
        }
    }
}
