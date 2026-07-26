package zw.gov.mohcc.impilo.paediatrics.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.paediatrics.age.AgeCalculator;
import zw.gov.mohcc.impilo.paediatrics.age.CorrectedAge;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * WHO 2006 child growth standards z-score engine.
 *
 * <p>Scores a measurement against the sex- and age-specific LMS tables using the
 * standard Box-Cox transformation. The engine is deliberately conservative: an
 * indicator it cannot score returns null rather than an approximation, and no score is
 * produced outside the age range the embedded standard actually covers
 * ({@value #UNDER_FIVE_MAX_AGE_DAYS} days). Scores older children only once the WHO
 * 5–19 year reference is added — never by extrapolating the under-five curves.</p>
 *
 * <p>Every assessment carries the standard identifier and engine version so a stored
 * z-score can be re-derived, and so a later standard revision does not silently
 * reinterpret historical measurements.</p>
 *
 * <p>Thread-safe and immutable once constructed; the LMS tables are read once.</p>
 */
public final class GrowthEngine {

    /** Upper age bound of the embedded WHO 2006 under-five standard, inclusive. */
    public static final int UNDER_FIVE_MAX_AGE_DAYS = 1856;

    /** Age at which measurement convention switches from recumbent length to standing height. */
    public static final int STATURE_MODE_SWITCH_AGE_DAYS = 731;

    /** Length and height differ systematically by ~0.7 cm in the same child. */
    private static final BigDecimal LENGTH_HEIGHT_DIFFERENCE_CM = BigDecimal.valueOf(0.7d);

    public static final String STANDARD_ID = "WHO_2006_CHILD_GROWTH_STANDARDS";
    public static final String ENGINE_VERSION = "1.0.0";

    private static final String RESOURCE_PATH = "growth/who_under5_lms.json";

    private final Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> standards;

    public GrowthEngine() {
        this(new ObjectMapper());
    }

    public GrowthEngine(ObjectMapper objectMapper) {
        this.standards = loadStandards(objectMapper);
    }

    /**
     * Score a measurement.
     *
     * @param patient     date of birth, sex, and gestational age at birth where known
     * @param measurement the anthropometry taken at this contact
     */
    public GrowthAssessment assess(PatientContext patient, GrowthMeasurement measurement) {
        Integer chronologicalAgeDays = AgeCalculator.ageDays(patient.dateOfBirth(), measurement.measuredAt());
        Integer correctedAgeDays = CorrectedAge.correctedAgeDays(chronologicalAgeDays, patient.gestationalAgeWeeks());
        boolean correctionApplied = CorrectedAge.correctionApplied(chronologicalAgeDays, patient.gestationalAgeWeeks());

        // Growth is read against corrected age for preterm children: a baby born at 30 weeks
        // is not failing to grow simply because they have not yet reached term-equivalent age.
        Integer scoringAgeDays = correctedAgeDays != null ? correctedAgeDays : chronologicalAgeDays;
        String sex = normaliseSex(patient.sex());

        BigDecimal normalisedStature = normaliseStature(scoringAgeDays, measurement);
        String statureModeUsed = normalisedStature == null ? null : expectedStatureMode(scoringAgeDays);
        BigDecimal statureAdjustment = statureAdjustment(scoringAgeDays, measurement);
        BigDecimal bmi = resolveBmi(measurement, normalisedStature);

        boolean scorable = scoringAgeDays != null
                && scoringAgeDays >= 0
                && scoringAgeDays <= UNDER_FIVE_MAX_AGE_DAYS
                && sex != null;

        Map<GrowthIndicator, Score> scores = new EnumMap<>(GrowthIndicator.class);
        if (scorable) {
            putIfPresent(scores, GrowthIndicator.WEIGHT_FOR_AGE,
                    score(GrowthIndicator.WEIGHT_FOR_AGE, sex, scoringAgeDays, measurement.weightKg()));
            putIfPresent(scores, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE,
                    score(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, sex, scoringAgeDays, normalisedStature));
            putIfPresent(scores, GrowthIndicator.BODY_MASS_INDEX_FOR_AGE,
                    score(GrowthIndicator.BODY_MASS_INDEX_FOR_AGE, sex, scoringAgeDays, bmi));
            putIfPresent(scores, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE,
                    score(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, sex, scoringAgeDays, measurement.headCircumferenceCm()));
        }

        return new GrowthAssessment(
                chronologicalAgeDays,
                correctedAgeDays,
                correctionApplied,
                scorable ? STANDARD_ID : null,
                scorable ? ENGINE_VERSION : null,
                bmi,
                normalisedStature,
                statureModeUsed,
                statureAdjustment,
                scores,
                unsupportedReason(chronologicalAgeDays, scoringAgeDays, sex));
    }

    /**
     * The measurement value that sits exactly on a given z-score curve, used to draw
     * reference curves. Returns null where the standard does not cover the age.
     */
    public BigDecimal valueAtZScore(GrowthIndicator indicator, String sex, int ageDays, double zScore) {
        LmsPoint point = lmsPoint(indicator, normaliseSex(sex), ageDays);
        if (point == null) {
            return null;
        }
        double value;
        if (Math.abs(point.l()) < 1e-12) {
            value = point.m() * Math.exp(point.s() * zScore);
        } else {
            value = point.m() * Math.pow(1.0d + point.l() * point.s() * zScore, 1.0d / point.l());
        }
        if (!Double.isFinite(value) || value <= 0) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    /** Ages (in days) for which the standard has data for this indicator and sex, ascending. */
    public NavigableMap<Integer, LmsPoint> table(GrowthIndicator indicator, String sex) {
        Map<String, NavigableMap<Integer, LmsPoint>> bySex = standards.get(indicator);
        if (bySex == null) {
            return new TreeMap<>();
        }
        NavigableMap<Integer, LmsPoint> rows = bySex.get(normaliseSex(sex));
        return rows == null ? new TreeMap<>() : rows;
    }

    // --- internals -------------------------------------------------------------------

    private static void putIfPresent(Map<GrowthIndicator, Score> target, GrowthIndicator indicator, Score score) {
        if (score != null) {
            target.put(indicator, score);
        }
    }

    private String unsupportedReason(Integer chronologicalAgeDays, Integer scoringAgeDays, String sex) {
        if (chronologicalAgeDays == null) {
            return "Date of birth is not recorded, so age-based growth standards cannot be applied.";
        }
        if (sex == null) {
            return "Sex is not recorded; WHO growth standards are sex-specific.";
        }
        if (scoringAgeDays != null && scoringAgeDays > UNDER_FIVE_MAX_AGE_DAYS) {
            return "Child is older than five years. The WHO 5-19 year reference is not yet loaded,"
                    + " and the under-five standard must not be extrapolated.";
        }
        return null;
    }

    private Score score(GrowthIndicator indicator, String sex, Integer ageDays, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        LmsPoint point = lmsPoint(indicator, sex, ageDays);
        if (point == null) {
            return null;
        }

        double measurement = value.doubleValue();
        double z;
        if (Math.abs(point.l()) < 1e-12) {
            z = Math.log(measurement / point.m()) / point.s();
        } else {
            z = (Math.pow(measurement / point.m(), point.l()) - 1.0d) / (point.l() * point.s());
        }
        if (!Double.isFinite(z)) {
            return null;
        }

        return new Score(
                BigDecimal.valueOf(z).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(100.0d * normalCdf(z)).setScale(1, RoundingMode.HALF_UP));
    }

    private LmsPoint lmsPoint(GrowthIndicator indicator, String sex, Integer ageDays) {
        if (indicator == null || sex == null || ageDays == null || ageDays < 0) {
            return null;
        }
        Map<String, NavigableMap<Integer, LmsPoint>> bySex = standards.get(indicator);
        if (bySex == null) {
            return null;
        }
        NavigableMap<Integer, LmsPoint> rows = bySex.get(sex);
        return rows == null ? null : rows.get(ageDays);
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

    private Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> loadStandards(ObjectMapper objectMapper) {
        try (InputStream inputStream = GrowthEngine.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("WHO growth standards resource not found: " + RESOURCE_PATH);
            }
            JsonNode root = objectMapper.readTree(inputStream);
            Map<String, NavigableMap<Integer, LmsPoint>> empty = Map.of();
            Map<String, Map<String, NavigableMap<Integer, LmsPoint>>> byKey = new HashMap<>();

            root.path("indicators").fields().forEachRemaining(indicatorEntry -> {
                Map<String, NavigableMap<Integer, LmsPoint>> bySex = new HashMap<>();
                indicatorEntry.getValue().fields().forEachRemaining(sexEntry -> {
                    NavigableMap<Integer, LmsPoint> rows = new TreeMap<>();
                    for (JsonNode row : sexEntry.getValue()) {
                        rows.put(row.path("day").asInt(),
                                new LmsPoint(row.path("l").asDouble(), row.path("m").asDouble(), row.path("s").asDouble()));
                    }
                    bySex.put(sexEntry.getKey(), rows);
                });
                byKey.put(indicatorEntry.getKey(), bySex);
            });

            Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> loaded =
                    new EnumMap<>(GrowthIndicator.class);
            for (GrowthIndicator indicator : GrowthIndicator.values()) {
                loaded.put(indicator, byKey.getOrDefault(indicator.datasetKey(), empty));
            }
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load WHO growth standards", exception);
        }
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
     * @param unsupportedReason plain-language explanation when no scores could be produced;
     *                          null when the measurement was scored
     */
    public record GrowthAssessment(
            Integer ageDays,
            Integer correctedAgeDays,
            boolean correctedAgeApplied,
            String standard,
            String engineVersion,
            BigDecimal bmi,
            BigDecimal normalisedStatureCm,
            String normalisedStatureMode,
            BigDecimal statureAdjustmentCm,
            Map<GrowthIndicator, Score> scores,
            String unsupportedReason) {

        public Score score(GrowthIndicator indicator) {
            return scores == null ? null : scores.get(indicator);
        }

        public BigDecimal zScore(GrowthIndicator indicator) {
            Score score = score(indicator);
            return score == null ? null : score.zScore();
        }
    }

    public record Score(BigDecimal zScore, BigDecimal percentile) {
    }

    public record LmsPoint(double l, double m, double s) {
    }
}
