package zw.gov.mohcc.impilo.paediatrics.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The LMS tables of one growth standard, loaded once from a classpath resource.
 *
 * <p>A standard's data may legitimately be absent from a deployment: a reference can be
 * unpublished, unlicensed for a jurisdiction, or awaiting ratification. Absence is
 * therefore a state this class can hold and describe ({@link #available()},
 * {@link #unavailableReason()}) rather than a failure that stops the service starting —
 * except where the caller declares the standard required, which is how a packaging mistake
 * that drops the WHO tables still fails loudly at start-up instead of quietly producing a
 * system that scores nothing.</p>
 *
 * <p>Values are held exactly as published. The tables are read at whole points on the
 * standard's axis with no interpolation between them: an interpolated LMS value is a number
 * the publishing authority never issued.</p>
 */
public final class GrowthReferenceTables {

    private final GrowthStandard standard;
    private final Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> tables;
    private final Map<GrowthIndicator, String> units;
    private final String contentVersion;
    private final String approvalStatus;
    private final String source;
    private final String unavailableReason;

    private GrowthReferenceTables(GrowthStandard standard,
                                  Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> tables,
                                  Map<GrowthIndicator, String> units,
                                  String contentVersion,
                                  String approvalStatus,
                                  String source,
                                  String unavailableReason) {
        this.standard = standard;
        this.tables = tables;
        this.units = units;
        this.contentVersion = contentVersion;
        this.approvalStatus = approvalStatus;
        this.source = source;
        this.unavailableReason = unavailableReason;
    }

    /**
     * Load a standard's tables.
     *
     * @param required when true a missing or unreadable resource throws, because the
     *                 deployment cannot do its job without it; when false the absence is
     *                 recorded and reported to every caller that asks for a score
     */
    public static GrowthReferenceTables load(GrowthStandard standard, ObjectMapper objectMapper, boolean required) {
        try (InputStream inputStream =
                     GrowthReferenceTables.class.getClassLoader().getResourceAsStream(standard.resourcePath())) {
            if (inputStream == null) {
                if (required) {
                    throw new IllegalStateException(
                            "Growth standard resource not found: " + standard.resourcePath());
                }
                return unavailable(standard, standard.label()
                        + " reference data is not loaded in this deployment.");
            }
            return parse(standard, objectMapper.readTree(inputStream));
        } catch (IOException exception) {
            if (required) {
                throw new IllegalStateException("Failed to load growth standard " + standard.standardId(), exception);
            }
            return unavailable(standard, standard.label()
                    + " reference data could not be read: " + exception.getMessage());
        }
    }

    private static GrowthReferenceTables unavailable(GrowthStandard standard, String reason) {
        return new GrowthReferenceTables(standard, Map.of(), Map.of(), null, null, null, reason);
    }

    private static GrowthReferenceTables parse(GrowthStandard standard, JsonNode root) {
        String axisKey = standard.axis() == GrowthStandard.Axis.POSTMENSTRUAL_WEEKS ? "week" : "day";

        Map<String, Map<String, NavigableMap<Integer, LmsPoint>>> byDatasetKey = new HashMap<>();
        root.path("indicators").fields().forEachRemaining(indicatorEntry -> {
            Map<String, NavigableMap<Integer, LmsPoint>> bySex = new HashMap<>();
            indicatorEntry.getValue().fields().forEachRemaining(sexEntry -> {
                NavigableMap<Integer, LmsPoint> rows = new TreeMap<>();
                for (JsonNode row : sexEntry.getValue()) {
                    // A row that does not carry all three parameters is not a usable point;
                    // treating a missing L as zero would silently score against a different
                    // distribution shape than the one that was published.
                    if (!row.hasNonNull("l") || !row.hasNonNull("m") || !row.hasNonNull("s")) {
                        continue;
                    }
                    rows.put(row.path(axisKey).asInt(),
                            new LmsPoint(row.path("l").asDouble(), row.path("m").asDouble(), row.path("s").asDouble()));
                }
                bySex.put(sexEntry.getKey(), rows);
            });
            byDatasetKey.put(indicatorEntry.getKey(), bySex);
        });

        Map<GrowthIndicator, Map<String, NavigableMap<Integer, LmsPoint>>> tables =
                new EnumMap<>(GrowthIndicator.class);
        Map<GrowthIndicator, String> units = new EnumMap<>(GrowthIndicator.class);
        for (GrowthIndicator indicator : GrowthIndicator.values()) {
            tables.put(indicator, byDatasetKey.getOrDefault(indicator.datasetKey(), Map.of()));
            String unit = root.path("units").path(indicator.datasetKey()).asText(null);
            units.put(indicator, unit == null || unit.isBlank() ? indicator.canonicalUnit() : unit);
        }

        return new GrowthReferenceTables(
                standard,
                tables,
                units,
                root.path("content_version").asText(null),
                root.path("approval_status").asText(null),
                root.path("source").asText(null),
                null);
    }

    public GrowthStandard standard() {
        return standard;
    }

    /** True when this deployment actually holds usable tables for this standard. */
    public boolean available() {
        return unavailableReason == null && tables.values().stream().anyMatch(bySex -> !bySex.isEmpty());
    }

    /** Why no score can be produced against this standard here, or null when it is loaded. */
    public String unavailableReason() {
        if (unavailableReason != null) {
            return unavailableReason;
        }
        return available() ? null : standard.label() + " reference data is empty in this deployment.";
    }

    public String contentVersion() {
        return contentVersion;
    }

    public String approvalStatus() {
        return approvalStatus;
    }

    public String source() {
        return source;
    }

    /**
     * The unit the table's median is expressed in, which is not always the unit the
     * measurement arrives in — Fenton publishes weight in grams.
     */
    public String unit(GrowthIndicator indicator) {
        String unit = units.get(indicator);
        return unit == null ? indicator.canonicalUnit() : unit;
    }

    /** The rows for one indicator and sex, ascending by axis position; never null. */
    public NavigableMap<Integer, LmsPoint> table(GrowthIndicator indicator, String sex) {
        Map<String, NavigableMap<Integer, LmsPoint>> bySex = tables.get(indicator);
        if (bySex == null || sex == null) {
            return new TreeMap<>();
        }
        NavigableMap<Integer, LmsPoint> rows = bySex.get(sex);
        return rows == null ? new TreeMap<>() : rows;
    }

    /** The published point at exactly this axis position, or null where none was published. */
    public LmsPoint point(GrowthIndicator indicator, String sex, Integer axisPosition) {
        if (indicator == null || sex == null || axisPosition == null) {
            return null;
        }
        return table(indicator, sex).get(axisPosition);
    }

    public boolean covers(GrowthIndicator indicator, String sex, Integer axisPosition) {
        return point(indicator, sex, axisPosition) != null;
    }
}
