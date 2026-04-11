package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Loads WHO 2006 child growth LMS tables and computes under-5 z-scores.
 */
@Service
public class GrowthStandardsService {

    private static final int UNDER_FIVE_MAX_DAYS = 1856;
    private final Map<String, Map<String, NavigableMap<Integer, LmsPoint>>> standards;

    public GrowthStandardsService(ObjectMapper objectMapper) {
        this.standards = loadStandards(objectMapper);
    }

    public GrowthAssessment assess(PatientContext patient, GrowthMeasurement measurement) {
        Integer ageDays = ageDays(patient.dateOfBirth(), measurement.measuredAt());
        String sex = normalizeSex(patient.sex());

        BigDecimal normalizedStature = normalizeStature(ageDays, measurement);
        String statureModeUsed = determineStatureModeUsed(ageDays, measurement);
        BigDecimal statureAdjustment = statureAdjustment(ageDays, measurement);

        BigDecimal bmi = measurement.bmi();
        if (bmi == null && measurement.weightKg() != null && normalizedStature != null
                && normalizedStature.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = normalizedStature.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            bmi = measurement.weightKg().divide(heightM.multiply(heightM), 3, RoundingMode.HALF_UP);
        }

        boolean supported = ageDays != null && ageDays >= 0 && ageDays <= UNDER_FIVE_MAX_DAYS && sex != null;

        return new GrowthAssessment(
                ageDays,
                supported ? "WHO_2006_CHILD_GROWTH_STANDARDS" : null,
                bmi,
                normalizedStature,
                statureModeUsed,
                statureAdjustment,
                supported ? score("weight_for_age", sex, ageDays, measurement.weightKg()) : null,
                supported ? score("length_height_for_age", sex, ageDays, normalizedStature) : null,
                supported ? score("body_mass_index_for_age", sex, ageDays, bmi) : null,
                supported ? score("head_circumference_for_age", sex, ageDays, measurement.headCircumferenceCm()) : null
        );
    }

    public record PatientContext(LocalDate dateOfBirth, String sex) {}

    public record GrowthMeasurement(
            OffsetDateTime measuredAt,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal heightCm,
            BigDecimal headCircumferenceCm,
            BigDecimal bmi,
            String measurementMode
    ) {}

    public record GrowthAssessment(
            Integer ageDays,
            String standard,
            BigDecimal bmi,
            BigDecimal normalizedStatureCm,
            String normalizedStatureMode,
            BigDecimal statureAdjustmentCm,
            Score weightForAge,
            Score lengthHeightForAge,
            Score bodyMassIndexForAge,
            Score headCircumferenceForAge
    ) {}

    public record Score(BigDecimal zScore, BigDecimal percentile) {}

    private Map<String, Map<String, NavigableMap<Integer, LmsPoint>>> loadStandards(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("growth/who_under5_lms.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            Map<String, Map<String, NavigableMap<Integer, LmsPoint>>> loaded = new HashMap<>();

            JsonNode indicators = root.path("indicators");
            indicators.fields().forEachRemaining(indicatorEntry -> {
                Map<String, NavigableMap<Integer, LmsPoint>> bySex = new HashMap<>();
                indicatorEntry.getValue().fields().forEachRemaining(sexEntry -> {
                    NavigableMap<Integer, LmsPoint> values = new TreeMap<>();
                    for (JsonNode row : sexEntry.getValue()) {
                        values.put(
                                row.path("day").asInt(),
                                new LmsPoint(row.path("l").asDouble(), row.path("m").asDouble(), row.path("s").asDouble())
                        );
                    }
                    bySex.put(sexEntry.getKey(), values);
                });
                loaded.put(indicatorEntry.getKey(), bySex);
            });

            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load WHO growth standards", exception);
        }
    }

    private Score score(String indicator, String sex, Integer ageDays, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || ageDays == null || sex == null) {
            return null;
        }
        NavigableMap<Integer, LmsPoint> rows = standards.getOrDefault(indicator, Map.of()).get(sex);
        if (rows == null) {
            return null;
        }
        LmsPoint point = rows.get(ageDays);
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
        double percentile = 100.0d * normalCdf(z);

        return new Score(
                BigDecimal.valueOf(z).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(percentile).setScale(1, RoundingMode.HALF_UP)
        );
    }

    private Integer ageDays(LocalDate dateOfBirth, OffsetDateTime measuredAt) {
        if (dateOfBirth == null || measuredAt == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(dateOfBirth, measuredAt.toLocalDate());
        return days < 0 ? null : (int) days;
    }

    private String normalizeSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return null;
        }
        String normalized = sex.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("M") || normalized.equals("MALE") || normalized.equals("BOY")) {
            return "male";
        }
        if (normalized.equals("F") || normalized.equals("FEMALE") || normalized.equals("GIRL")) {
            return "female";
        }
        return null;
    }

    private String determineStatureModeUsed(Integer ageDays, GrowthMeasurement measurement) {
        BigDecimal normalizedStature = normalizeStature(ageDays, measurement);
        if (normalizedStature == null) {
            return null;
        }
        boolean expectedLength = ageDays != null && ageDays < 731;
        return expectedLength ? "LENGTH" : "HEIGHT";
    }

    private BigDecimal statureAdjustment(Integer ageDays, GrowthMeasurement measurement) {
        if (ageDays == null) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        String mode = normalizeMode(measurement.measurementMode(), measurement);
        if (mode == null || mode.equals("AUTO")) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        if (ageDays < 731 && mode.equals("HEIGHT")) {
            return BigDecimal.valueOf(0.7d).setScale(1, RoundingMode.HALF_UP);
        }
        if (ageDays >= 731 && mode.equals("LENGTH")) {
            return BigDecimal.valueOf(-0.7d).setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeStature(Integer ageDays, GrowthMeasurement measurement) {
        BigDecimal base = measurement.lengthCm() != null ? measurement.lengthCm() : measurement.heightCm();
        if (base == null) {
            return null;
        }
        if (ageDays == null) {
            return base;
        }
        String mode = normalizeMode(measurement.measurementMode(), measurement);
        if (ageDays < 731 && "HEIGHT".equals(mode)) {
            return base.add(BigDecimal.valueOf(0.7d)).setScale(3, RoundingMode.HALF_UP);
        }
        if (ageDays >= 731 && "LENGTH".equals(mode)) {
            return base.subtract(BigDecimal.valueOf(0.7d)).setScale(3, RoundingMode.HALF_UP);
        }
        return base.setScale(3, RoundingMode.HALF_UP);
    }

    private String normalizeMode(String requestedMode, GrowthMeasurement measurement) {
        if (requestedMode != null && !requestedMode.isBlank() && !requestedMode.equalsIgnoreCase("AUTO")) {
            return requestedMode.trim().toUpperCase(Locale.ROOT);
        }
        if (measurement.lengthCm() != null) {
            return "LENGTH";
        }
        if (measurement.heightCm() != null) {
            return "HEIGHT";
        }
        return null;
    }

    /**
     * Standard normal CDF using the Abramowitz-Stegun error-function approximation.
     */
    private double normalCdf(double z) {
        return 0.5d * (1.0d + erfApproximation(z / Math.sqrt(2.0d)));
    }

    private double erfApproximation(double x) {
        double sign = x < 0 ? -1.0d : 1.0d;
        double ax = Math.abs(x);
        double t = 1.0d / (1.0d + 0.3275911d * ax);
        double y = 1.0d - (((((1.061405429d * t - 1.453152027d) * t + 1.421413741d) * t - 0.284496736d) * t
                + 0.254829592d) * t * Math.exp(-ax * ax));
        return sign * y;
    }

    private record LmsPoint(double l, double m, double s) {}
}
