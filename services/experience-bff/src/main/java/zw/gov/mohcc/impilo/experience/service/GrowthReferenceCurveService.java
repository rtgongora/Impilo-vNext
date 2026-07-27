package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthEngine;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthIndicator;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthReferenceTables;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthStandard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Reference curve geometry for growth charts.
 *
 * <p>The engine has always been able to produce the value sitting on a given z-score curve;
 * nothing exposed it, so the chart component in the shell had no source for the curves it
 * requires and was never mounted. This serves them for the standard the child is actually
 * eligible for, along with the attribution that standard's licence obliges the chart to
 * display.</p>
 *
 * <p>Curves are generated from the published table points only. Where the standard stops,
 * the curve stops — it is not extended to make a chart look complete.</p>
 */
@Service
public class GrowthReferenceCurveService {

    /** The curves a clinician reads a growth chart against. */
    private static final double[] CURVE_Z_SCORES = {-3.0d, -2.0d, 0.0d, 2.0d, 3.0d};

    /**
     * WHO tables are daily, which is far more resolution than a chart can show. One point a
     * week draws the same curve at a fraction of the payload.
     */
    private static final int WHO_AXIS_STEP_DAYS = 7;

    private final GrowthEngine engine;

    public GrowthReferenceCurveService(ObjectMapper objectMapper) {
        this.engine = new GrowthEngine(objectMapper);
    }

    public record PatientGrowthContext(String dateOfBirth, String sex, Integer gestationalAgeWeeks) {}

    /**
     * Curves for one indicator, on the standard this child is currently read against.
     *
     * @throws IllegalArgumentException if the indicator is not one this system scores
     */
    public Map<String, Object> curvesFor(PatientGrowthContext context, String indicatorKey) {
        GrowthIndicator indicator = indicatorOf(indicatorKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("indicator", indicator.datasetKey());
        payload.put("unit", indicator.canonicalUnit());

        LocalDate dateOfBirth = parseDate(context.dateOfBirth());
        GrowthEngine.Selection selection = engine.standardFor(
                dateOfBirth, context.sex(), context.gestationalAgeWeeks(),
                OffsetDateTime.now(ZoneOffset.UTC));

        if (selection.standard() == null) {
            // No curves and a reason. The chart's own contract is that an empty curve set
            // means "say so", never "draw something".
            payload.put("standard", null);
            payload.put("curves", List.of());
            payload.put("unavailable_reason", selection.reason() != null ? selection.reason()
                    : "No growth standard applies to this child, so no reference curves can be drawn.");
            return payload;
        }

        GrowthStandard standard = selection.standard();
        GrowthReferenceTables tables = engine.reference(standard);
        payload.put("standard", standard.standardId());
        payload.put("standard_label", standard.label());
        payload.put("axis", standard.axis() == GrowthStandard.Axis.POSTMENSTRUAL_WEEKS
                ? "postmenstrual_weeks" : "age_days");
        payload.put("attribution", attribution(standard, tables));
        payload.put("current_axis_position", selection.axisPosition());

        if (!standard.covers(indicator)) {
            payload.put("curves", List.of());
            payload.put("unavailable_reason", standard.label() + " does not publish a reference for "
                    + readable(indicator) + ", so it cannot be charted for this child.");
            return payload;
        }

        String sex = context.sex();
        NavigableMap<Integer, ?> published = engine.table(standard, indicator, sex);
        if (published.isEmpty()) {
            payload.put("curves", List.of());
            payload.put("unavailable_reason", standard.label() + " has no "
                    + readable(indicator) + " table for this child's recorded sex.");
            return payload;
        }

        int step = standard.axis() == GrowthStandard.Axis.POSTMENSTRUAL_WEEKS ? 1 : WHO_AXIS_STEP_DAYS;
        List<Map<String, Object>> curves = new ArrayList<>();
        for (double z : CURVE_Z_SCORES) {
            List<Map<String, Object>> points = new ArrayList<>();
            for (int axisPosition = published.firstKey(); axisPosition <= published.lastKey(); axisPosition += step) {
                BigDecimal value = engine.valueAtZScore(standard, indicator, sex, axisPosition, z);
                if (value == null) {
                    continue;
                }
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("x", axisPosition);
                point.put("value", value.setScale(3, RoundingMode.HALF_UP));
                points.add(point);
            }
            Map<String, Object> curve = new LinkedHashMap<>();
            curve.put("z", z);
            curve.put("points", points);
            curves.add(curve);
        }
        payload.put("curves", curves);
        return payload;
    }

    /** Human-readable name of a stamped standard identifier, or null if unrecognised. */
    public String labelOf(String standardId) {
        GrowthStandard standard = GrowthStandard.fromId(standardId);
        return standard == null ? null : standard.label();
    }

    /**
     * The attribution a stored score carries. It travels with every measurement rather than
     * only with the chart, because the obligation follows the data, not the drawing.
     */
    public Map<String, Object> attributionOf(String standardId) {
        GrowthStandard standard = GrowthStandard.fromId(standardId);
        return standard == null ? null : attribution(standard, engine.reference(standard));
    }

    private Map<String, Object> attribution(GrowthStandard standard, GrowthReferenceTables tables) {
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put("label", standard.label());
        attribution.put("required_chart_label", standard.requiredChartLabel());
        attribution.put("citation", standard.citation());
        attribution.put("licence", standard.licence());
        attribution.put("approval_status", tables == null ? null : tables.approvalStatus());
        attribution.put("content_version", tables == null ? null : tables.contentVersion());
        return attribution;
    }

    /** Percentile for a stored z-score. Reading a score is not recomputing it. */
    public BigDecimal percentileOf(double z) {
        return BigDecimal.valueOf(100.0d * normalCdf(z)).setScale(1, RoundingMode.HALF_UP);
    }

    private static GrowthIndicator indicatorOf(String key) {
        if (key != null) {
            String normalised = key.trim().toLowerCase(Locale.ROOT);
            for (GrowthIndicator indicator : GrowthIndicator.values()) {
                if (indicator.datasetKey().equals(normalised) || indicator.name().equalsIgnoreCase(normalised)) {
                    return indicator;
                }
            }
        }
        throw new IllegalArgumentException("Unknown growth indicator: " + key);
    }

    private static String readable(GrowthIndicator indicator) {
        return switch (indicator) {
            case WEIGHT_FOR_AGE -> "weight-for-age";
            case LENGTH_HEIGHT_FOR_AGE -> "length/height-for-age";
            case BODY_MASS_INDEX_FOR_AGE -> "BMI-for-age";
            case HEAD_CIRCUMFERENCE_FOR_AGE -> "head-circumference-for-age";
        };
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

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
}
