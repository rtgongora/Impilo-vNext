package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed form of {@code deid_release_policy.quasi_identifier_rules}.
 *
 * <p>JSON shape:</p>
 * <pre>{@code
 * {
 *   "columns": {
 *     "dob": "AGE_BAND_5",        // renamed to "age_band" in the output
 *     "visit_date": "MONTH",
 *     "encounter_date": "DATE_SHIFT",
 *     "location": "DISTRICT",
 *     "diagnosis": "RARE_TO_OTHER"
 *   },
 *   "quasiColumns": ["age_band", "district"],   // k-anonymity equivalence-class columns
 *   "rareThreshold": 5,
 *   "maxDateShiftDays": 7
 * }
 * }</pre>
 */
public record GeneralisationRules(
        Map<String, String> columns,
        List<String> quasiColumns,
        int rareThreshold,
        int maxDateShiftDays
) {

    public static final String RULE_AGE_BAND_5 = "AGE_BAND_5";
    public static final String RULE_MONTH = "MONTH";
    public static final String RULE_DATE_SHIFT = "DATE_SHIFT";
    public static final String RULE_DISTRICT = "DISTRICT";
    public static final String RULE_RARE_TO_OTHER = "RARE_TO_OTHER";

    public static final int DEFAULT_RARE_THRESHOLD = 5;
    public static final int DEFAULT_MAX_DATE_SHIFT_DAYS = 7;

    public static GeneralisationRules empty() {
        return new GeneralisationRules(Map.of(), List.of(),
                DEFAULT_RARE_THRESHOLD, DEFAULT_MAX_DATE_SHIFT_DAYS);
    }

    public static GeneralisationRules fromJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, String> columns = new LinkedHashMap<>();
            JsonNode columnsNode = root.path("columns");
            columnsNode.fields().forEachRemaining(e -> columns.put(e.getKey(), e.getValue().asText()));

            List<String> quasiColumns = new ArrayList<>();
            for (JsonNode col : root.path("quasiColumns")) {
                quasiColumns.add(col.asText());
            }

            int rareThreshold = root.path("rareThreshold").asInt(DEFAULT_RARE_THRESHOLD);
            int maxDateShiftDays = root.path("maxDateShiftDays").asInt(DEFAULT_MAX_DATE_SHIFT_DAYS);
            return new GeneralisationRules(Map.copyOf(columns), List.copyOf(quasiColumns),
                    rareThreshold, maxDateShiftDays);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid quasi_identifier_rules JSON", e);
        }
    }
}
