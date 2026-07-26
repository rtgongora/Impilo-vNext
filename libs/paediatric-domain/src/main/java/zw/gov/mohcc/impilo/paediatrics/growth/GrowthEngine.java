package zw.gov.mohcc.impilo.paediatrics.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.paediatrics.age.AgeCalculator;
import zw.gov.mohcc.impilo.paediatrics.age.CorrectedAge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Growth z-score engine over the standards a child is actually eligible for.
 *
 * <p>Two standards are embedded. The WHO 2006 child growth standards score children from
 * birth to five years against age in days, using corrected age where the child was born
 * preterm. The Fenton 2013 preterm growth chart scores infants born before term against
 * completed postmenstrual weeks, from 22 weeks up to the end of its published range.</p>
 *
 * <p><strong>The engine chooses the standard; the caller cannot.</strong> A preterm infant
 * scored against the term chart looks severely malnourished while growing exactly as
 * expected — a 28-week baby two weeks old is roughly two-thirds of a term newborn's weight,
 * which the WHO tables read as a z-score below −6. That is not a finding, it is a category
 * error, and it would put a well baby into a malnutrition pathway.</p>
 *
 * <p>The engine is deliberately conservative: an indicator it cannot score returns no score
 * and a stated reason, never an approximation. Where a preterm infant's own reference is
 * missing, the measurement goes unscored — the term standard is never substituted for it.</p>
 *
 * <p>Every assessment carries the standard identifier and engine version so a stored z-score
 * can be re-derived, and so a later standard revision does not silently reinterpret
 * historical measurements.</p>
 *
 * <p>Thread-safe and immutable once constructed; the reference tables are read once.</p>
 */
public final class GrowthEngine {

    /** Upper age bound of the embedded WHO 2006 under-five standard, inclusive. */
    public static final int UNDER_FIVE_MAX_AGE_DAYS = 1856;

    /** Age at which measurement convention switches from recumbent length to standing height. */
    public static final int STATURE_MODE_SWITCH_AGE_DAYS = 731;

    /** First completed postmenstrual week the Fenton 2013 chart covers. */
    public static final int FENTON_MIN_POSTMENSTRUAL_WEEKS = 22;

    /**
     * Last completed postmenstrual week the Fenton 2013 chart publishes. Beyond it a
     * preterm infant is followed on the WHO standard at corrected age, which is the
     * transition the chart itself is drawn for: 50 weeks postmenstrual is 10 weeks
     * corrected, well inside the WHO tables, so the handover leaves no unscored gap.
     */
    public static final int FENTON_MAX_POSTMENSTRUAL_WEEKS = 49;

    private static final int DAYS_PER_WEEK = 7;

    /** Length and height differ systematically by ~0.7 cm in the same child. */
    private static final BigDecimal LENGTH_HEIGHT_DIFFERENCE_CM = BigDecimal.valueOf(0.7d);

    /**
     * Retained for callers that stamped this identifier before Fenton was added; new scores
     * take it from {@link GrowthStandard}.
     */
    public static final String STANDARD_ID = GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS.standardId();

    /**
     * Bumped from 1.0.0 when preterm infants stopped being scored on the term standard.
     * The version is stamped on every score, so a record written by either engine stays
     * interpretable as the thing it actually was.
     */
    public static final String ENGINE_VERSION = "2.0.0";

    private final Map<GrowthStandard, GrowthReferenceTables> references;

    public GrowthEngine() {
        this(new ObjectMapper());
    }

    public GrowthEngine(ObjectMapper objectMapper) {
        Map<GrowthStandard, GrowthReferenceTables> loaded = new EnumMap<>(GrowthStandard.class);
        // WHO is required: a deployment missing it can score nothing at all, and that is a
        // packaging fault worth failing loudly on. Fenton is optional so a deployment whose
        // licence or ratification is still pending starts, and says so, rather than either
        // refusing to boot or quietly scoring preterm babies on the term chart.
        loaded.put(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS,
                GrowthReferenceTables.load(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS, objectMapper, true));
        loaded.put(GrowthStandard.FENTON_2013_PRETERM_GROWTH,
                GrowthReferenceTables.load(GrowthStandard.FENTON_2013_PRETERM_GROWTH, objectMapper, false));
        this.references = loaded;
    }

    /**
     * Score a measurement against whichever standard the child is eligible for.
     *
     * @param patient     date of birth, sex, and gestational age at birth where known
     * @param measurement the anthropometry taken at this contact
     */
    public GrowthAssessment assess(PatientContext patient, GrowthMeasurement measurement) {
        Integer chronologicalAgeDays = AgeCalculator.ageDays(patient.dateOfBirth(), measurement.measuredAt());
        Integer correctedAgeDays = CorrectedAge.correctedAgeDays(chronologicalAgeDays, patient.gestationalAgeWeeks());
        boolean correctionApplied = CorrectedAge.correctionApplied(chronologicalAgeDays, patient.gestationalAgeWeeks());
        Integer postmenstrualAgeWeeks =
                postmenstrualAgeWeeks(chronologicalAgeDays, patient.gestationalAgeWeeks());
        String sex = normaliseSex(patient.sex());

        // Stature is normalised against age since birth: which way round a baby was measured
        // is a property of how it was done, not of which chart it is read on.
        Integer statureAgeDays = correctedAgeDays != null ? correctedAgeDays : chronologicalAgeDays;
        BigDecimal normalisedStature = normaliseStature(statureAgeDays, measurement);
        String statureModeUsed = normalisedStature == null ? null : expectedStatureMode(statureAgeDays);
        BigDecimal statureAdjustment = statureAdjustment(statureAgeDays, measurement);
        BigDecimal bmi = resolveBmi(measurement, normalisedStature);

        Selection selection = selectStandard(chronologicalAgeDays, correctedAgeDays,
                postmenstrualAgeWeeks, patient.gestationalAgeWeeks(), sex);

        Map<GrowthIndicator, Score> scores = new EnumMap<>(GrowthIndicator.class);
        Map<GrowthIndicator, String> gaps = new LinkedHashMap<>();

        if (selection.standard() != null) {
            GrowthReferenceTables tables = references.get(selection.standard());
            scoreInto(scores, gaps, tables, selection, sex, GrowthIndicator.WEIGHT_FOR_AGE, measurement.weightKg());
            scoreInto(scores, gaps, tables, selection, sex, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, normalisedStature);
            scoreInto(scores, gaps, tables, selection, sex, GrowthIndicator.BODY_MASS_INDEX_FOR_AGE, bmi);
            scoreInto(scores, gaps, tables, selection, sex, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE,
                    measurement.headCircumferenceCm());
        }

        boolean scored = !scores.isEmpty();
        return new GrowthAssessment(
                chronologicalAgeDays,
                correctedAgeDays,
                correctionApplied,
                postmenstrualAgeWeeks,
                scored ? selection.standard().standardId() : null,
                scored ? ENGINE_VERSION : null,
                bmi,
                normalisedStature,
                statureModeUsed,
                statureAdjustment,
                scores,
                gaps,
                scored ? null : selection.reason());
    }

    /**
     * Completed weeks since the mother's last menstrual period: gestational age at birth
     * plus completed weeks lived. Gestational age is recorded in completed weeks only, so
     * the days part of it is not available to add here.
     */
    public static Integer postmenstrualAgeWeeks(Integer chronologicalAgeDays, Integer gestationalAgeWeeks) {
        if (chronologicalAgeDays == null || chronologicalAgeDays < 0
                || gestationalAgeWeeks == null || gestationalAgeWeeks <= 0) {
            return null;
        }
        return gestationalAgeWeeks + (chronologicalAgeDays / DAYS_PER_WEEK);
    }

    /**
     * The measurement value that sits exactly on a given z-score curve, used to draw
     * reference curves. Returns null where the standard published no point there.
     *
     * @param axisPosition age in days for WHO, completed postmenstrual weeks for Fenton
     */
    public BigDecimal valueAtZScore(GrowthStandard standard, GrowthIndicator indicator,
                                    String sex, int axisPosition, double zScore) {
        GrowthReferenceTables tables = references.get(standard);
        if (tables == null) {
            return null;
        }
        LmsPoint point = tables.point(indicator, normaliseSex(sex), axisPosition);
        if (point == null) {
            return null;
        }
        Double value = valueAt(point, zScore);
        if (value == null) {
            return null;
        }
        BigDecimal inTableUnit = BigDecimal.valueOf(value);
        return toMeasurementUnit(inTableUnit, tables.unit(indicator), indicator).setScale(3, RoundingMode.HALF_UP);
    }

    /** WHO curve value at an age in days; kept for callers predating multi-standard support. */
    public BigDecimal valueAtZScore(GrowthIndicator indicator, String sex, int ageDays, double zScore) {
        return valueAtZScore(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS, indicator, sex, ageDays, zScore);
    }

    /** Axis positions for which a standard has data for this indicator and sex, ascending. */
    public NavigableMap<Integer, LmsPoint> table(GrowthStandard standard, GrowthIndicator indicator, String sex) {
        GrowthReferenceTables tables = references.get(standard);
        return tables == null ? new java.util.TreeMap<>() : tables.table(indicator, normaliseSex(sex));
    }

    /** WHO table for this indicator and sex; kept for callers predating multi-standard support. */
    public NavigableMap<Integer, LmsPoint> table(GrowthIndicator indicator, String sex) {
        return table(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS, indicator, sex);
    }

    /** The loaded tables for a standard, including whether they are present at all. */
    public GrowthReferenceTables reference(GrowthStandard standard) {
        return references.get(standard);
    }

    /**
     * Which standard a child of this age and gestation would be scored against, without
     * needing a measurement. Surfaces use it to label a chart before anything is plotted.
     */
    public Selection standardFor(LocalDate dateOfBirth, String sex, Integer gestationalAgeWeeks, OffsetDateTime asOf) {
        Integer chronologicalAgeDays = AgeCalculator.ageDays(dateOfBirth, asOf);
        return selectStandard(chronologicalAgeDays,
                CorrectedAge.correctedAgeDays(chronologicalAgeDays, gestationalAgeWeeks),
                postmenstrualAgeWeeks(chronologicalAgeDays, gestationalAgeWeeks),
                gestationalAgeWeeks,
                normaliseSex(sex));
    }

    // --- standard selection ------------------------------------------------------------

    /**
     * The standard that applies, the axis position to read it at, and — when none applies —
     * the reason in words a clinician can act on.
     */
    public record Selection(GrowthStandard standard, Integer axisPosition, String reason) {

        static Selection of(GrowthStandard standard, int axisPosition) {
            return new Selection(standard, axisPosition, null);
        }

        static Selection none(String reason) {
            return new Selection(null, null, reason);
        }
    }

    private Selection selectStandard(Integer chronologicalAgeDays,
                                     Integer correctedAgeDays,
                                     Integer postmenstrualAgeWeeks,
                                     Integer gestationalAgeWeeks,
                                     String sex) {
        if (chronologicalAgeDays == null) {
            return Selection.none("Date of birth is not recorded, so age-based growth standards cannot be applied.");
        }
        if (chronologicalAgeDays < 0) {
            return Selection.none("The measurement is dated before the child's date of birth,"
                    + " so no age could be established. Check the measurement date.");
        }
        if (sex == null) {
            return Selection.none("Sex is not recorded; growth standards are sex-specific.");
        }

        boolean preterm = CorrectedAge.isPreterm(gestationalAgeWeeks);
        if (preterm && postmenstrualAgeWeeks != null
                && postmenstrualAgeWeeks <= FENTON_MAX_POSTMENSTRUAL_WEEKS) {
            return selectPretermStandard(postmenstrualAgeWeeks, gestationalAgeWeeks);
        }

        Integer scoringAgeDays = correctedAgeDays != null ? correctedAgeDays : chronologicalAgeDays;
        if (scoringAgeDays > UNDER_FIVE_MAX_AGE_DAYS) {
            return Selection.none("Child is older than five years. The WHO 5-19 year reference is not yet loaded,"
                    + " and the under-five standard must not be extrapolated.");
        }
        return Selection.of(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS, scoringAgeDays);
    }

    /**
     * A baby born preterm is read on the preterm chart or not at all. Falling back to the
     * term standard here is the single most dangerous thing this engine could do, because
     * the resulting z-score looks entirely plausible and is wrong by several standard
     * deviations in the direction that triggers referral.
     */
    private Selection selectPretermStandard(int postmenstrualAgeWeeks, Integer gestationalAgeWeeks) {
        GrowthReferenceTables fenton = references.get(GrowthStandard.FENTON_2013_PRETERM_GROWTH);
        String pretermContext = "This infant was born at " + gestationalAgeWeeks + " weeks and is now "
                + postmenstrualAgeWeeks + " weeks postmenstrual age.";

        if (fenton == null || !fenton.available()) {
            return Selection.none(pretermContext + " " + (fenton == null ? "The preterm growth reference is not loaded."
                    : fenton.unavailableReason())
                    + " The measurement is recorded but not scored: WHO term standards are not valid for a preterm"
                    + " infant and are deliberately not substituted.");
        }
        if (postmenstrualAgeWeeks < FENTON_MIN_POSTMENSTRUAL_WEEKS) {
            return Selection.none(pretermContext + " The Fenton 2013 preterm chart begins at "
                    + FENTON_MIN_POSTMENSTRUAL_WEEKS + " weeks postmenstrual age, so this measurement"
                    + " falls before any published reference. Check the recorded gestational age.");
        }
        return Selection.of(GrowthStandard.FENTON_2013_PRETERM_GROWTH, postmenstrualAgeWeeks);
    }

    // --- scoring -----------------------------------------------------------------------

    private void scoreInto(Map<GrowthIndicator, Score> scores,
                           Map<GrowthIndicator, String> gaps,
                           GrowthReferenceTables tables,
                           Selection selection,
                           String sex,
                           GrowthIndicator indicator,
                           BigDecimal measuredValue) {
        if (measuredValue == null || measuredValue.compareTo(BigDecimal.ZERO) <= 0) {
            // Nothing was measured. That is the caller's business to report, not a gap in
            // the standard, so it is left unrecorded here.
            return;
        }
        GrowthStandard standard = selection.standard();
        if (!standard.covers(indicator)) {
            gaps.put(indicator, standard.label() + " does not publish a " + readable(indicator)
                    + " reference, so this measurement cannot be scored on it.");
            return;
        }
        LmsPoint point = tables.point(indicator, sex, selection.axisPosition());
        if (point == null) {
            gaps.put(indicator, standard.label() + " has no published " + readable(indicator)
                    + " point at " + axisDescription(standard, selection.axisPosition()) + ".");
            return;
        }

        BigDecimal inTableUnit = toTableUnit(measuredValue, tables.unit(indicator), indicator);
        Double raw = zScore(point, inTableUnit.doubleValue());
        if (raw == null) {
            gaps.put(indicator, "The recorded " + readable(indicator)
                    + " could not be scored against " + standard.label() + ".");
            return;
        }

        double z = raw;
        if (standard == GrowthStandard.FENTON_2013_PRETERM_GROWTH
                && indicator == GrowthIndicator.WEIGHT_FOR_AGE) {
            z = applyExtremeTailCorrection(point, inTableUnit.doubleValue(), z);
        }

        scores.put(indicator, new Score(
                BigDecimal.valueOf(z).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(100.0d * normalCdf(z)).setScale(1, RoundingMode.HALF_UP)));
    }

    private static Double zScore(LmsPoint point, double measurement) {
        double z;
        if (Math.abs(point.l()) < 1e-12) {
            z = Math.log(measurement / point.m()) / point.s();
        } else {
            z = (Math.pow(measurement / point.m(), point.l()) - 1.0d) / (point.l() * point.s());
        }
        return Double.isFinite(z) ? z : null;
    }

    private static Double valueAt(LmsPoint point, double zScore) {
        double value;
        if (Math.abs(point.l()) < 1e-12) {
            value = point.m() * Math.exp(point.s() * zScore);
        } else {
            value = point.m() * Math.pow(1.0d + point.l() * point.s() * zScore, 1.0d / point.l());
        }
        return Double.isFinite(value) && value > 0 ? value : null;
    }

    /**
     * The WHO tail correction, as applied by the Fenton chart's own published calculator.
     *
     * <p>Beyond three standard deviations the Box-Cox curve is fitted to almost no data and
     * a small difference in weight produces an implausibly large change in z. Outside that
     * range the score is instead measured in units of the distance between the second and
     * third standard deviation. This matters more for preterm infants than for any other
     * group, because a great many of them genuinely sit in the tails.</p>
     */
    private static double applyExtremeTailCorrection(LmsPoint point, double measurement, double z) {
        if (z > 3.0d) {
            Double sd3 = valueAt(point, 3.0d);
            Double sd2 = valueAt(point, 2.0d);
            if (sd3 == null || sd2 == null || sd3 <= sd2) {
                return z;
            }
            return 3.0d + (measurement - sd3) / (sd3 - sd2);
        }
        if (z < -3.0d) {
            Double sd3 = valueAt(point, -3.0d);
            Double sd2 = valueAt(point, -2.0d);
            if (sd3 == null || sd2 == null || sd2 <= sd3) {
                return z;
            }
            return -3.0d + (measurement - sd3) / (sd2 - sd3);
        }
        return z;
    }

    // --- units, stature, helpers -------------------------------------------------------

    private static BigDecimal toTableUnit(BigDecimal measured, String tableUnit, GrowthIndicator indicator) {
        if ("g".equalsIgnoreCase(tableUnit) && "kg".equals(indicator.canonicalUnit())) {
            return measured.multiply(BigDecimal.valueOf(1000));
        }
        return measured;
    }

    private static BigDecimal toMeasurementUnit(BigDecimal inTableUnit, String tableUnit, GrowthIndicator indicator) {
        if ("g".equalsIgnoreCase(tableUnit) && "kg".equals(indicator.canonicalUnit())) {
            return inTableUnit.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }
        return inTableUnit;
    }

    private static String readable(GrowthIndicator indicator) {
        return switch (indicator) {
            case WEIGHT_FOR_AGE -> "weight-for-age";
            case LENGTH_HEIGHT_FOR_AGE -> "length/height-for-age";
            case BODY_MASS_INDEX_FOR_AGE -> "BMI-for-age";
            case HEAD_CIRCUMFERENCE_FOR_AGE -> "head-circumference-for-age";
        };
    }

    private static String axisDescription(GrowthStandard standard, Integer axisPosition) {
        return standard.axis() == GrowthStandard.Axis.POSTMENSTRUAL_WEEKS
                ? axisPosition + " weeks postmenstrual age"
                : "day " + axisPosition;
    }

    private BigDecimal resolveBmi(GrowthMeasurement measurement, BigDecimal normalisedStature) {
        if (measurement.bmi() != null) {
            return measurement.bmi();
        }
        if (measurement.weightKg() == null || normalisedStature == null
                || normalisedStature.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightMetres = normalisedStature.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return measurement.weightKg().divide(heightMetres.multiply(heightMetres), 3, RoundingMode.HALF_UP);
    }

    private String expectedStatureMode(Integer ageDays) {
        return ageDays != null && ageDays < STATURE_MODE_SWITCH_AGE_DAYS ? "LENGTH" : "HEIGHT";
    }

    /**
     * WHO standards below two years are built on recumbent length and above two years on
     * standing height. When the measurement was taken the other way round the value is
     * adjusted by 0.7 cm so the child is scored against the right curve.
     */
    private BigDecimal normaliseStature(Integer ageDays, GrowthMeasurement measurement) {
        BigDecimal base = measurement.lengthCm() != null ? measurement.lengthCm() : measurement.heightCm();
        if (base == null) {
            return null;
        }
        if (ageDays == null) {
            return base;
        }
        String mode = resolveMode(measurement);
        if (ageDays < STATURE_MODE_SWITCH_AGE_DAYS && "HEIGHT".equals(mode)) {
            return base.add(LENGTH_HEIGHT_DIFFERENCE_CM).setScale(3, RoundingMode.HALF_UP);
        }
        if (ageDays >= STATURE_MODE_SWITCH_AGE_DAYS && "LENGTH".equals(mode)) {
            return base.subtract(LENGTH_HEIGHT_DIFFERENCE_CM).setScale(3, RoundingMode.HALF_UP);
        }
        return base.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal statureAdjustment(Integer ageDays, GrowthMeasurement measurement) {
        BigDecimal none = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        if (ageDays == null) {
            return none;
        }
        String mode = resolveMode(measurement);
        if (mode == null) {
            return none;
        }
        if (ageDays < STATURE_MODE_SWITCH_AGE_DAYS && "HEIGHT".equals(mode)) {
            return LENGTH_HEIGHT_DIFFERENCE_CM.setScale(1, RoundingMode.HALF_UP);
        }
        if (ageDays >= STATURE_MODE_SWITCH_AGE_DAYS && "LENGTH".equals(mode)) {
            return LENGTH_HEIGHT_DIFFERENCE_CM.negate().setScale(1, RoundingMode.HALF_UP);
        }
        return none;
    }

    private String resolveMode(GrowthMeasurement measurement) {
        String requested = measurement.measurementMode();
        if (requested != null && !requested.isBlank() && !requested.equalsIgnoreCase("AUTO")) {
            return requested.trim().toUpperCase(Locale.ROOT);
        }
        if (measurement.lengthCm() != null) {
            return "LENGTH";
        }
        if (measurement.heightCm() != null) {
            return "HEIGHT";
        }
        return null;
    }

    private static String normaliseSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return null;
        }
        return switch (sex.trim().toUpperCase(Locale.ROOT)) {
            case "M", "MALE", "BOY" -> "male";
            case "F", "FEMALE", "GIRL" -> "female";
            default -> null;
        };
    }

    /** Standard normal CDF via the Abramowitz-Stegun error-function approximation. */
    private static double normalCdf(double z) {
        return 0.5d * (1.0d + erf(z / Math.sqrt(2.0d)));
    }

    private static double erf(double x) {
        double sign = x < 0 ? -1.0d : 1.0d;
        double ax = Math.abs(x);
        double t = 1.0d / (1.0d + 0.3275911d * ax);
        double y = 1.0d - (((((1.061405429d * t - 1.453152027d) * t + 1.421413741d) * t - 0.284496736d) * t
                + 0.254829592d) * t * Math.exp(-ax * ax));
        return sign * y;
    }

    // --- value types -----------------------------------------------------------------

    public record PatientContext(LocalDate dateOfBirth, String sex, Integer gestationalAgeWeeks) {
        public PatientContext(LocalDate dateOfBirth, String sex) {
            this(dateOfBirth, sex, null);
        }
    }

    public record GrowthMeasurement(
            OffsetDateTime measuredAt,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal heightCm,
            BigDecimal headCircumferenceCm,
            BigDecimal bmi,
            String measurementMode) {
    }

    /**
     * @param postmenstrualAgeWeeks completed weeks since the last menstrual period, where
     *                              gestational age is known; the axis the preterm chart is read on
     * @param unavailableIndicators per-indicator explanations of why something measured was
     *                              not scored — a stated gap rather than a silent omission
     * @param unsupportedReason     plain-language explanation when no scores could be produced;
     *                              null when the measurement was scored
     */
    public record GrowthAssessment(
            Integer ageDays,
            Integer correctedAgeDays,
            boolean correctedAgeApplied,
            Integer postmenstrualAgeWeeks,
            String standard,
            String engineVersion,
            BigDecimal bmi,
            BigDecimal normalisedStatureCm,
            String normalisedStatureMode,
            BigDecimal statureAdjustmentCm,
            Map<GrowthIndicator, Score> scores,
            Map<GrowthIndicator, String> unavailableIndicators,
            String unsupportedReason) {

        public Score score(GrowthIndicator indicator) {
            return scores == null ? null : scores.get(indicator);
        }

        public BigDecimal zScore(GrowthIndicator indicator) {
            Score score = score(indicator);
            return score == null ? null : score.zScore();
        }

        /** The standard these scores were produced against, or null when nothing was scored. */
        public GrowthStandard growthStandard() {
            return GrowthStandard.fromId(standard);
        }
    }

    public record Score(BigDecimal zScore, BigDecimal percentile) {
    }
}
