package zw.gov.mohcc.impilo.clinical.interpretation;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a governed FHIR R4 {@code ObservationDefinition} (as stored in ZIBO) into the resolver's
 * {@link QualifiedInterval} model. Standard R4 splits applicability into multiple
 * {@code qualifiedInterval} entries keyed by {@code category} ("reference" vs "critical") that share
 * the same gender/age/gestation context; this parser merges a matching reference + critical pair into a
 * single {@link QualifiedInterval} (with low/high from "reference" and criticalLow/High from "critical").
 *
 * <p>Fail-closed: anything it cannot parse is skipped (never guessed). The {@code artifactId}/
 * {@code version}/{@code canonicalUrl} provenance is injected by the caller from the ZIBO artifact.</p>
 */
public final class ObservationDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(ObservationDefinitionParser.class);

    private ObservationDefinitionParser() {
    }

    /** Parse one ObservationDefinition resource. Returns the (possibly empty) list of intervals. */
    public static List<QualifiedInterval> parse(JsonNode resource, String artifactId, String version, String canonicalUrl) {
        List<QualifiedInterval> out = new ArrayList<>();
        if (resource == null || !resource.hasNonNull("qualifiedInterval")) {
            return out;
        }
        JsonNode intervals = resource.get("qualifiedInterval");
        if (!intervals.isArray()) {
            return out;
        }

        // Group entries by applicability key so we can merge reference + critical.
        Map<String, Group> groups = new LinkedHashMap<>();
        for (JsonNode qi : intervals) {
            Sex sex = parseGender(text(qi, "gender"));
            Double ageLow = ageYears(qi.path("age").path("low"));
            Double ageHigh = ageYears(qi.path("age").path("high"));
            Integer gaLow = intVal(qi.path("gestationalAge").path("low"));
            Integer gaHigh = intVal(qi.path("gestationalAge").path("high"));
            boolean pregnancy = "pregnancy".equalsIgnoreCase(text(qi, "context"))
                    || qi.path("gestationalAge").isObject();
            String specimen = text(qi, "specimen");
            String category = firstNonBlank(text(qi, "category"), text(qi, "condition"), "reference")
                    .toLowerCase();

            String key = sex + "|" + ageLow + "|" + ageHigh + "|" + gaLow + "|" + gaHigh + "|" + specimen;
            Group g = groups.computeIfAbsent(key, k -> new Group(sex, ageLow, ageHigh, pregnancy, gaLow, gaHigh, specimen));

            JsonNode range = qi.get("range");
            if (range == null) {
                continue;
            }
            BigDecimal low = decimal(range.path("low").path("value"));
            BigDecimal high = decimal(range.path("high").path("value"));
            String unit = firstNonBlank(text(range.path("low"), "unit"), text(range.path("high"), "unit"), null);

            if (category.contains("critical")) {
                g.criticalLow = low;
                g.criticalHigh = high;
                if (g.unit == null) {
                    g.unit = unit;
                }
            } else { // reference / absolute / default
                g.low = low;
                g.high = high;
                if (unit != null) {
                    g.unit = unit;
                }
                g.condition = category;
            }
        }

        for (Group g : groups.values()) {
            if (g.unit == null || (g.low == null && g.high == null && g.criticalLow == null && g.criticalHigh == null)) {
                continue; // nothing usable — skip (fail-closed)
            }
            out.add(new QualifiedInterval(
                    g.low, g.high, g.unit, g.criticalLow, g.criticalHigh, g.sex,
                    g.ageLow, g.ageHigh, g.pregnancy, g.gaLow, g.gaHigh, g.specimen,
                    g.condition == null ? "reference" : g.condition,
                    artifactId, version, canonicalUrl));
        }
        return out;
    }

    /** Extract the LOINC code from {@code code.coding[]} (system contains "loinc"). */
    public static String loincOf(JsonNode resource) {
        return codeOf(resource, true);
    }

    /** Extract the first non-LOINC local code from {@code code.coding[]}. */
    public static String localCodeOf(JsonNode resource) {
        return codeOf(resource, false);
    }

    private static String codeOf(JsonNode resource, boolean wantLoinc) {
        if (resource == null) {
            return null;
        }
        JsonNode coding = resource.path("code").path("coding");
        if (!coding.isArray()) {
            return null;
        }
        for (JsonNode c : coding) {
            String system = text(c, "system");
            boolean isLoinc = system != null && system.toLowerCase().contains("loinc");
            if (isLoinc == wantLoinc) {
                String code = text(c, "code");
                if (code != null && !code.isBlank()) {
                    return code;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------

    private static final class Group {
        final Sex sex;
        final Double ageLow;
        final Double ageHigh;
        final boolean pregnancy;
        final Integer gaLow;
        final Integer gaHigh;
        final String specimen;
        BigDecimal low;
        BigDecimal high;
        BigDecimal criticalLow;
        BigDecimal criticalHigh;
        String unit;
        String condition;

        Group(Sex sex, Double ageLow, Double ageHigh, boolean pregnancy, Integer gaLow, Integer gaHigh, String specimen) {
            this.sex = sex;
            this.ageLow = ageLow;
            this.ageHigh = ageHigh;
            this.pregnancy = pregnancy;
            this.gaLow = gaLow;
            this.gaHigh = gaHigh;
            this.specimen = specimen;
        }
    }

    private static Sex parseGender(String g) {
        if (g == null) {
            return Sex.UNKNOWN;
        }
        return switch (g.trim().toLowerCase()) {
            case "male" -> Sex.MALE;
            case "female" -> Sex.FEMALE;
            default -> Sex.UNKNOWN;
        };
    }

    /** Convert a FHIR age Quantity to years (unit a/yr/mo/wk/d). Null when absent/unparseable. */
    private static Double ageYears(JsonNode q) {
        BigDecimal v = decimal(q.path("value"));
        if (v == null) {
            return null;
        }
        String unit = firstNonBlank(text(q, "unit"), text(q, "code"), "a").toLowerCase();
        return switch (unit) {
            case "a", "yr", "year", "years" -> v.doubleValue();
            case "mo", "month", "months" -> v.doubleValue() / 12.0;
            case "wk", "week", "weeks" -> v.doubleValue() / 52.0;
            case "d", "day", "days" -> v.doubleValue() / 365.25;
            default -> v.doubleValue();
        };
    }

    private static Integer intVal(JsonNode q) {
        BigDecimal v = decimal(q.path("value"));
        return v == null ? null : v.intValue();
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode n = parent.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
