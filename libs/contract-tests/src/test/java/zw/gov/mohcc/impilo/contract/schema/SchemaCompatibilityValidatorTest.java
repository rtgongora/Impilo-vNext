package zw.gov.mohcc.impilo.contract.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCompatibilityValidatorTest {

    @Nested
    @DisplayName("Backward-compatible changes (ALLOWED)")
    class AllowedChanges {

        @Test
        @DisplayName("Adding optional field is backward-compatible")
        void addOptionalField() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"),
                    List.of("name")
            );
            Map<String, String> proposedFields = new LinkedHashMap<>();
            proposedFields.put("name", "string");
            proposedFields.put("age", "integer");
            proposedFields.put("email", "string");
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    proposedFields, List.of("name")
            );

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("Widening integer to number is backward-compatible")
        void widenIntegerToNumber() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("count", "integer"), List.of("count"));
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("count", "number"), List.of("count"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isTrue();
        }

        @Test
        @DisplayName("Making required field optional is backward-compatible")
        void requiredToOptional() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"),
                    List.of("name", "age")
            );
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"),
                    List.of("name")
            );

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isTrue();
        }

        @Test
        @DisplayName("Identical schemas are compatible")
        void identicalSchemas() {
            JsonNode schema = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string"), List.of("name"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(schema, schema);

            assertThat(result.compatible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Breaking changes (REJECTED)")
    class BreakingChanges {

        @Test
        @DisplayName("Removing a field is breaking")
        void removeField() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"), List.of("name"));
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string"), List.of("name"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("age") && v.contains("removed"));
        }

        @Test
        @DisplayName("Changing field type incompatibly is breaking")
        void changeType() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string"), List.of("name"));
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "integer"), List.of("name"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("name") && v.contains("type changed"));
        }

        @Test
        @DisplayName("Adding a new required field is breaking")
        void addRequiredField() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string"), List.of("name"));
            Map<String, String> proposedFields = new LinkedHashMap<>();
            proposedFields.put("name", "string");
            proposedFields.put("email", "string");
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    proposedFields, List.of("name", "email"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("email") && v.contains("required"));
        }

        @Test
        @DisplayName("Making optional field required is breaking")
        void optionalToRequired() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"), List.of("name"));
            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("name", "string", "age", "integer"), List.of("name", "age"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("age") && v.contains("optional to required"));
        }
    }

    @Nested
    @DisplayName("Enum compatibility")
    class EnumCompat {

        @Test
        @DisplayName("Adding enum value is backward-compatible")
        void addEnumValue() {
            String existing = """
                    {
                      "type": "object",
                      "properties": {
                        "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE"] }
                      }
                    }
                    """;
            String proposed = """
                    {
                      "type": "object",
                      "properties": {
                        "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE", "PENDING"] }
                      }
                    }
                    """;

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isTrue();
        }

        @Test
        @DisplayName("Removing enum value is breaking")
        void removeEnumValue() {
            String existing = """
                    {
                      "type": "object",
                      "properties": {
                        "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE", "PENDING"] }
                      }
                    }
                    """;
            String proposed = """
                    {
                      "type": "object",
                      "properties": {
                        "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE"] }
                      }
                    }
                    """;

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("PENDING") && v.contains("removed"));
        }
    }

    @Test
    @DisplayName("CompatibilityResult.summary() describes outcome")
    void summaryMessages() {
        assertThat(CompatibilityResult.compatible().summary()).isEqualTo("COMPATIBLE");

        CompatibilityResult bad = CompatibilityResult.incompatible(List.of("field removed"));
        assertThat(bad.summary()).startsWith("INCOMPATIBLE:");
    }
}
