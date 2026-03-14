package zw.gov.mohcc.impilo.contract.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Validates schema compatibility between versions.
 *
 * <p>Rules for backward-compatible changes (ALLOWED):
 * <ul>
 *   <li>Adding new optional fields</li>
 *   <li>Adding new enum values</li>
 *   <li>Widening a type (e.g. int → long)</li>
 *   <li>Making a required field optional</li>
 * </ul>
 *
 * <p>Breaking changes (REJECTED):
 * <ul>
 *   <li>Removing a field that was present</li>
 *   <li>Renaming a field</li>
 *   <li>Changing a field's type incompatibly</li>
 *   <li>Adding a new required field</li>
 *   <li>Removing enum values</li>
 * </ul>
 */
public final class SchemaCompatibilityValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaCompatibilityValidator() {}

    /**
     * Check if {@code proposed} schema is backward-compatible with {@code existing}.
     * Consumers of the existing schema must be able to read messages produced with
     * the proposed schema without breaking.
     *
     * @param existing the current (baseline) schema as a JSON Schema object
     * @param proposed the new (candidate) schema as a JSON Schema object
     * @return a result indicating compatibility or listing violations
     */
    public static CompatibilityResult checkBackwardCompatibility(JsonNode existing, JsonNode proposed) {
        List<String> violations = new ArrayList<>();
        checkProperties(existing, proposed, "", violations);
        checkRequiredFields(existing, proposed, "", violations);

        if (violations.isEmpty()) {
            return CompatibilityResult.compatible();
        }
        return CompatibilityResult.incompatible(violations);
    }

    /**
     * Convenience overload accepting JSON strings.
     */
    public static CompatibilityResult checkBackwardCompatibility(String existingJson, String proposedJson) {
        try {
            JsonNode existing = MAPPER.readTree(existingJson);
            JsonNode proposed = MAPPER.readTree(proposedJson);
            return checkBackwardCompatibility(existing, proposed);
        } catch (Exception e) {
            return CompatibilityResult.incompatible(List.of("Failed to parse schema JSON: " + e.getMessage()));
        }
    }

    private static void checkProperties(JsonNode existing, JsonNode proposed, String path, List<String> violations) {
        JsonNode existingProps = existing.get("properties");
        JsonNode proposedProps = proposed.get("properties");

        if (existingProps == null) return;
        if (proposedProps == null) {
            violations.add(path + "properties: removed entirely in proposed schema");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = existingProps.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode existingField = entry.getValue();
            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;

            if (!proposedProps.has(fieldName)) {
                violations.add(fieldPath + ": field removed in proposed schema");
                continue;
            }

            JsonNode proposedField = proposedProps.get(fieldName);

            // Check type compatibility
            checkTypeCompatibility(existingField, proposedField, fieldPath, violations);

            // Recurse into nested objects
            if ("object".equals(getType(existingField)) && "object".equals(getType(proposedField))) {
                checkProperties(existingField, proposedField, fieldPath + ".", violations);
                checkRequiredFields(existingField, proposedField, fieldPath + ".", violations);
            }

            // Check enum narrowing
            checkEnumCompatibility(existingField, proposedField, fieldPath, violations);
        }
    }

    private static void checkTypeCompatibility(JsonNode existing, JsonNode proposed,
                                                String path, List<String> violations) {
        String existingType = getType(existing);
        String proposedType = getType(proposed);

        if (existingType == null || proposedType == null) return;

        if (!existingType.equals(proposedType) && !isWideningConversion(existingType, proposedType)) {
            violations.add(path + ": type changed from '" + existingType + "' to '" + proposedType + "'");
        }
    }

    private static void checkEnumCompatibility(JsonNode existing, JsonNode proposed,
                                                String path, List<String> violations) {
        JsonNode existingEnum = existing.get("enum");
        JsonNode proposedEnum = proposed.get("enum");

        if (existingEnum == null || !existingEnum.isArray()) return;
        if (proposedEnum == null || !proposedEnum.isArray()) {
            // Removing enum constraint entirely is widening — allowed
            return;
        }

        Set<String> proposedValues = new HashSet<>();
        proposedEnum.forEach(v -> proposedValues.add(v.asText()));

        for (JsonNode val : existingEnum) {
            if (!proposedValues.contains(val.asText())) {
                violations.add(path + ": enum value '" + val.asText() + "' removed in proposed schema");
            }
        }
    }

    private static void checkRequiredFields(JsonNode existing, JsonNode proposed,
                                             String path, List<String> violations) {
        JsonNode existingRequired = existing.get("required");
        JsonNode proposedRequired = proposed.get("required");

        // Adding new required fields is a breaking change
        if (proposedRequired != null && proposedRequired.isArray()) {
            Set<String> existingRequiredSet = new HashSet<>();
            if (existingRequired != null && existingRequired.isArray()) {
                existingRequired.forEach(r -> existingRequiredSet.add(r.asText()));
            }

            for (JsonNode req : proposedRequired) {
                String fieldName = req.asText();
                if (!existingRequiredSet.contains(fieldName)) {
                    // Check if this is a genuinely new field (not existing optional → required)
                    JsonNode existingProps = existing.get("properties");
                    if (existingProps != null && existingProps.has(fieldName)) {
                        violations.add(path + fieldName + ": changed from optional to required");
                    } else if (existingProps != null) {
                        violations.add(path + fieldName + ": new required field added (breaking for existing consumers)");
                    }
                }
            }
        }
    }

    private static String getType(JsonNode schema) {
        JsonNode type = schema.get("type");
        return type != null ? type.asText() : null;
    }

    private static boolean isWideningConversion(String from, String to) {
        return ("integer".equals(from) && "number".equals(to));
    }

    /**
     * Build a minimal JSON Schema from a map of field names to types.
     * Convenience for tests.
     */
    public static JsonNode schemaFromFields(Map<String, String> fields, List<String> required) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        for (Map.Entry<String, String> e : fields.entrySet()) {
            props.putObject(e.getKey()).put("type", e.getValue());
        }
        if (required != null && !required.isEmpty()) {
            var reqArray = schema.putArray("required");
            required.forEach(reqArray::add);
        }
        return schema;
    }
}
