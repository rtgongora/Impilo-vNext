package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes early warning scores on the server from observed vital signs.
 *
 * <p>Previously the total score arrived from the client and the server only banded it,
 * which meant the number that triggers an escalation was never actually calculated by the
 * platform. It also meant paediatric patients were scored against adult thresholds: a
 * heart rate of 150 is an emergency in a ten-year-old and unremarkable in a three-month-old,
 * and a single set of cut-offs cannot be right for both. This engine computes NEWS2 for
 * patients thirteen and over and an age-banded paediatric score below that.</p>
 *
 * <p>Two safety properties are structural:</p>
 * <ul>
 *   <li><strong>An unmeasured parameter is reported, not scored as zero.</strong> A score
 *       built from half the observations is marked incomplete and lists what is missing,
 *       because a low total from a partial observation set reads as reassurance and is the
 *       most dangerous possible output.</li>
 *   <li><strong>Thresholds are versioned content, not code.</strong> The band table is a
 *       classpath resource carrying its own version and provenance, so a governed change to
 *       the escalation thresholds is a content release and every stored score records which
 *       version produced it.</li>
 * </ul>
 *
 * <p><strong>Engineering seed:</strong> the threshold values require MoHCC and paediatric
 * specialist ratification before they drive escalation in production.</p>
 */
@Component
public class EwsCalculatorEngine {

    private static final Logger log = LoggerFactory.getLogger(EwsCalculatorEngine.class);

    public static final String SCORE_TYPE_NEWS2 = "NEWS2";
    public static final String SCORE_TYPE_PEWS = "PEWS";

    /** At and above this age the adult NEWS2 parameter set applies. */
    public static final int ADULT_SCORE_FROM_AGE_YEARS = 13;

    private static final String RESOURCE_PATH = "clinical/ews-thresholds.json";

    private final JsonNode content;
    private final String contentVersion;

    public EwsCalculatorEngine(ObjectMapper objectMapper) {
        this.content = load(objectMapper);
        this.contentVersion = content.path("version").asText("unknown");
    }

    public String contentVersion() {
        return contentVersion;
    }

    /**
     * Choose the score appropriate to the patient's age. Where age is unknown the adult
     * score is not assumed — the caller must say which score it wants, because silently
     * scoring a child as an adult is the failure this engine exists to prevent.
     */
    public String scoreTypeForAge(Integer ageDays) {
        if (ageDays == null) {
            return null;
        }
        return ageDays >= ADULT_SCORE_FROM_AGE_YEARS * 365 ? SCORE_TYPE_NEWS2 : SCORE_TYPE_PEWS;
    }

    /**
     * @param scoreType NEWS2 or PEWS; when null it is chosen from {@code ageDays}
     * @param ageDays   patient age in days, required for PEWS band selection
     * @param observations parameter name to observed value (numeric or coded string)
     */
    public EwsCalculation calculate(String scoreType, Integer ageDays, Map<String, Object> observations) {
        String resolvedType = scoreType != null && !scoreType.isBlank()
                ? scoreType.trim().toUpperCase(Locale.ROOT)
                : scoreTypeForAge(ageDays);

        if (resolvedType == null) {
            return EwsCalculation.notCalculable(
                    "Neither a score type nor the patient's age was supplied, so no score could be calculated.",
                    contentVersion);
        }

        JsonNode definition = content.path("scoreTypes").path(resolvedType);
        if (definition.isMissingNode()) {
            return EwsCalculation.notCalculable(
                    "No threshold content is loaded for score type " + resolvedType + ".", contentVersion);
        }

        JsonNode numericParameters;
        String ageBandId = null;
        if (SCORE_TYPE_PEWS.equals(resolvedType)) {
            JsonNode band = resolveAgeBand(definition, ageDays);
            if (band == null) {
                return EwsCalculation.notCalculable(
                        "A paediatric score needs the patient's age to choose the right thresholds,"
                                + " and no age band matched.", contentVersion);
            }
            ageBandId = band.path("id").asText(null);
            numericParameters = band.path("parameters");
        } else {
            numericParameters = definition.path("parameters");
        }

        Map<String, Integer> contributions = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        int total = 0;

        Iterator<Map.Entry<String, JsonNode>> numeric = numericParameters.fields();
        while (numeric.hasNext()) {
            Map.Entry<String, JsonNode> entry = numeric.next();
            Double value = numeric(observations.get(entry.getKey()));
            if (value == null) {
                missing.add(entry.getKey());
                continue;
            }
            Integer points = pointsForValue(entry.getValue(), value);
            if (points == null) {
                missing.add(entry.getKey());
                continue;
            }
            contributions.put(entry.getKey(), points);
            total += points;
        }

        JsonNode coded = definition.path("codedParameters");
        Iterator<Map.Entry<String, JsonNode>> codedFields = coded.fields();
        while (codedFields.hasNext()) {
            Map.Entry<String, JsonNode> entry = codedFields.next();
            String value = text(observations.get(entry.getKey()));
            if (value == null) {
                missing.add(entry.getKey());
                continue;
            }
            JsonNode points = entry.getValue().path(value.toUpperCase(Locale.ROOT));
            if (points.isMissingNode()) {
                missing.add(entry.getKey());
                continue;
            }
            contributions.put(entry.getKey(), points.asInt());
            total += points.asInt();
        }

        if (contributions.isEmpty()) {
            return EwsCalculation.notCalculable(
                    "No observations were supplied, so no score could be calculated.", contentVersion);
        }

        String riskLevel = riskLevel(definition, total);
        return new EwsCalculation(true, resolvedType, ageBandId, total, riskLevel,
                contributions, List.copyOf(missing), contentVersion, null);
    }

    private JsonNode resolveAgeBand(JsonNode definition, Integer ageDays) {
        if (ageDays == null) {
            return null;
        }
        for (JsonNode band : definition.path("ageBands")) {
            int min = band.path("minAgeDays").asInt(0);
            int max = band.path("maxAgeDays").asInt(Integer.MAX_VALUE);
            if (ageDays >= min && ageDays <= max) {
                return band;
            }
        }
        return null;
    }

    private Integer pointsForValue(JsonNode bands, double value) {
        for (JsonNode band : bands) {
            boolean aboveMin = !band.has("min") || value >= band.path("min").asDouble();
            boolean belowMax = !band.has("max") || value <= band.path("max").asDouble();
            if (aboveMin && belowMax) {
                return band.path("points").asInt();
            }
        }
        return null;
    }

    private String riskLevel(JsonNode definition, int total) {
        for (JsonNode band : definition.path("riskBands")) {
            if (total >= band.path("min").asInt()) {
                return band.path("level").asText("NONE");
            }
        }
        return "NONE";
    }

    private JsonNode load(ObjectMapper objectMapper) {
        try (InputStream stream = EwsCalculatorEngine.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Early warning threshold content not found: " + RESOURCE_PATH);
            }
            JsonNode loaded = objectMapper.readTree(stream);
            log.info("Loaded early warning threshold content version={} status={}",
                    loaded.path("version").asText("unknown"), loaded.path("status").asText("unknown"));
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load early warning threshold content", e);
        }
    }

    private static Double numeric(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    /**
     * @param calculable      false when the inputs could not support a score at all
     * @param missingParameters observations that were not supplied; a score missing these is
     *                          incomplete and must not be read as reassurance
     */
    public record EwsCalculation(
            boolean calculable,
            String scoreType,
            String ageBandId,
            Integer totalScore,
            String riskLevel,
            Map<String, Integer> contributions,
            List<String> missingParameters,
            String contentVersion,
            String reason) {

        static EwsCalculation notCalculable(String reason, String contentVersion) {
            return new EwsCalculation(false, null, null, null, null, Map.of(), List.of(), contentVersion, reason);
        }

        /** True when some parameters of the score were never observed. */
        public boolean incomplete() {
            return calculable && !missingParameters.isEmpty();
        }
    }
}
