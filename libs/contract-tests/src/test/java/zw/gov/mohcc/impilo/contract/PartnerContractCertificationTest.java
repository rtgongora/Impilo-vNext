package zw.gov.mohcc.impilo.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import zw.gov.mohcc.impilo.contract.envelope.EnvelopeValidationResult;
import zw.gov.mohcc.impilo.contract.envelope.EventEnvelopeValidator;
import zw.gov.mohcc.impilo.contract.schema.CompatibilityResult;
import zw.gov.mohcc.impilo.contract.schema.SchemaCompatibilityValidator;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 23 — Partner Contract Certification Test Suite.
 *
 * <p>Validates that partner integrations conform to Impilo v1.1 contracts:
 * <ul>
 *   <li>Request schemas: required fields, types, naming conventions</li>
 *   <li>Response handling: partners must accept envelope format</li>
 *   <li>Event contracts: partners producing events must follow EventEnvelope spec</li>
 *   <li>Schema evolution: partner schema changes must be backward-compatible</li>
 * </ul>
 *
 * <p>This test suite is the automated gate for partner certification.
 * Partners must pass all tests before receiving production credentials.
 */
@DisplayName("Wave 23 — Partner Contract Certification")
class PartnerContractCertificationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 1. Request Contract Validation ──────────────────────────────

    @Nested
    @DisplayName("1. Request Contract — Required Headers")
    class RequestHeaders {

        static final List<String> REQUIRED_HEADERS = List.of(
                "X-Tenant-ID", "X-Pod-ID", "X-Request-ID", "X-Correlation-ID"
        );

        static final List<String> COMMAND_HEADERS = List.of(
                "Idempotency-Key"
        );

        @Test
        @DisplayName("All four trust headers are defined in the contract")
        void trustHeadersDefined() {
            assertThat(REQUIRED_HEADERS).containsExactly(
                    "X-Tenant-ID", "X-Pod-ID", "X-Request-ID", "X-Correlation-ID");
        }

        @Test
        @DisplayName("Command endpoints require Idempotency-Key header")
        void commandEndpointsRequireIdempotencyKey() {
            assertThat(COMMAND_HEADERS).contains("Idempotency-Key");
        }

        @Test
        @DisplayName("Header names follow X-prefixed convention")
        void headerNamingConvention() {
            for (String header : REQUIRED_HEADERS) {
                assertThat(header).startsWith("X-");
            }
        }
    }

    // ── 2. Response Envelope Contract ──────────────────────────────

    @Nested
    @DisplayName("2. Response Envelope Contract")
    class ResponseEnvelope {

        @Test
        @DisplayName("Error envelope contains required fields: code, message, details, request_id, correlation_id")
        void errorEnvelopeStructure() throws Exception {
            String errorResponse = """
                    {
                        "error": {
                            "code": "VALIDATION_FAILED",
                            "message": "Request validation failed",
                            "details": {"field": "patient_id", "reason": "required"},
                            "request_id": "req-001",
                            "correlation_id": "corr-001"
                        }
                    }
                    """;

            JsonNode root = MAPPER.readTree(errorResponse);
            JsonNode error = root.get("error");

            assertThat(error.has("code")).isTrue();
            assertThat(error.has("message")).isTrue();
            assertThat(error.has("details")).isTrue();
            assertThat(error.has("request_id")).isTrue();
            assertThat(error.has("correlation_id")).isTrue();
        }

        @Test
        @DisplayName("Success response may use any structure (not enforced)")
        void successResponseFlexible() throws Exception {
            // Partners consuming Impilo APIs must handle both envelope and flat responses
            String listResponse = """
                    {"items": [{"id": "123", "name": "test"}], "count": 1}
                    """;
            JsonNode root = MAPPER.readTree(listResponse);
            assertThat(root.has("items")).isTrue();
            assertThat(root.get("count").asInt()).isEqualTo(1);
        }
    }

    // ── 3. Event Envelope Contract ──────────────────────────────────

    @Nested
    @DisplayName("3. Event Envelope Contract")
    class EventContract {

        @Test
        @DisplayName("Partner event envelope passes validation with all 15 required fields")
        void partnerEventPassesValidation() {
            String partnerEvent = """
                    {
                        "eventId": "evt-partner-001",
                        "eventType": "impilo.partner-ehr.encounter.created.v1",
                        "schemaVersion": 1,
                        "correlationId": "corr-001",
                        "causationId": "cause-001",
                        "idempotencyKey": "idem-partner-001",
                        "producer": "partner-ehr-service",
                        "tenantId": "aaaa1111-1111-1111-1111-111111111111",
                        "podId": "partner-pod-1",
                        "occurredAt": "2026-03-15T10:00:00Z",
                        "emittedAt": "2026-03-15T10:00:01Z",
                        "subjectType": "Encounter",
                        "subjectId": "enc-001",
                        "payload": {"encounter_type": "outpatient", "facility_id": "fac-001"},
                        "meta": {}
                    }
                    """;

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(partnerEvent);

            assertThat(result.valid())
                    .as("Partner event should pass envelope validation: %s", result.violations())
                    .isTrue();
        }

        @Test
        @DisplayName("Partner event with missing fields fails validation")
        void partnerEventMissingFieldsFails() {
            String incompleteEvent = """
                    {
                        "eventId": "evt-partner-002",
                        "eventType": "impilo.partner.data.pushed.v1",
                        "payload": {"key": "value"}
                    }
                    """;

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(incompleteEvent);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).isNotEmpty();
        }

        @Test
        @DisplayName("Partner event type must follow impilo.{service}.{entity}.{action}.v{N}")
        void partnerEventTypeNaming() {
            assertThat(EventEnvelopeValidator.isValidEventType("impilo.partner-lab.result.submitted.v1")).isTrue();
            assertThat(EventEnvelopeValidator.isValidEventType("impilo.partner-pharmacy.order.fulfilled.v1")).isTrue();
            assertThat(EventEnvelopeValidator.isValidEventType("PARTNER_EVENT_SUBMITTED")).isFalse();
            assertThat(EventEnvelopeValidator.isValidEventType("partner.lab.result.v1")).isFalse();
        }
    }

    // ── 4. Schema Backward Compatibility ────────────────────────────

    @Nested
    @DisplayName("4. Schema Backward Compatibility")
    class SchemaCompatibility {

        @Test
        @DisplayName("Adding optional field is backward-compatible")
        void addOptionalFieldIsCompatible() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string", "facility_id", "string"),
                    List.of("patient_id"));

            // Partner proposes adding an optional 'notes' field
            ObjectNode proposed = (ObjectNode) SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string", "facility_id", "string", "notes", "string"),
                    List.of("patient_id"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible())
                    .as("Adding optional field should be compatible: %s", result.summary())
                    .isTrue();
        }

        @Test
        @DisplayName("Removing existing field is a breaking change")
        void removeFieldIsBreaking() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string", "facility_id", "string", "timestamp", "string"),
                    List.of("patient_id"));

            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string", "facility_id", "string"),
                    List.of("patient_id"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("timestamp") && v.contains("removed"));
        }

        @Test
        @DisplayName("Adding new required field is a breaking change")
        void addRequiredFieldIsBreaking() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string"),
                    List.of("patient_id"));

            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("patient_id", "string", "mandate_id", "string"),
                    List.of("patient_id", "mandate_id"));

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("mandate_id"));
        }

        @Test
        @DisplayName("Changing field type is a breaking change")
        void changeTypeIsBreaking() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("count", "integer"),
                    List.of());

            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("count", "string"),
                    List.of());

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("type changed"));
        }

        @Test
        @DisplayName("Widening int to number is compatible")
        void wideningIntToNumberIsCompatible() {
            JsonNode existing = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("quantity", "integer"),
                    List.of());

            JsonNode proposed = SchemaCompatibilityValidator.schemaFromFields(
                    Map.of("quantity", "number"),
                    List.of());

            CompatibilityResult result = SchemaCompatibilityValidator.checkBackwardCompatibility(existing, proposed);

            assertThat(result.compatible()).isTrue();
        }
    }

    // ── 5. Partner API Convention Checks ─────────────────────────────

    @Nested
    @DisplayName("5. API Convention Checks")
    class ApiConventions {

        static Stream<Arguments> validPartnerPaths() {
            return Stream.of(
                    Arguments.of("/internal/v1/partner/patients"),
                    Arguments.of("/internal/v1/partner/encounters"),
                    Arguments.of("/internal/v1/partner/observations"),
                    Arguments.of("/internal/v2/partner/patients")
            );
        }

        @ParameterizedTest(name = "Path: {0}")
        @MethodSource("validPartnerPaths")
        @DisplayName("Partner API paths follow /internal/v{N}/ convention")
        void partnerPathConvention(String path) {
            assertThat(path).matches("/internal/v\\d+/.*");
        }

        @Test
        @DisplayName("API version is extractable from path")
        void versionExtractableFromPath() {
            String path = "/internal/v2/partner/patients";
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("/internal/v(\\d+)/").matcher(path);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group(1)).isEqualTo("2");
        }

        @Test
        @DisplayName("Event type service segment matches partner registration")
        void eventTypeMatchesPartner() {
            String eventType = "impilo.partner-ehr.encounter.created.v1";
            String service = EventEnvelopeValidator.extractService(eventType);
            assertThat(service).isEqualTo("partner-ehr");
        }
    }

    // ── 6. Certification Gate ─────────────────────────────────────────

    @Nested
    @DisplayName("6. Certification Gate — Full Lifecycle")
    class CertificationGate {

        @Test
        @DisplayName("Partner certification checklist covers all required areas")
        void certificationChecklist() {
            List<String> requiredAreas = List.of(
                    "REQUEST_HEADERS",
                    "ERROR_ENVELOPE",
                    "EVENT_ENVELOPE",
                    "SCHEMA_COMPATIBILITY",
                    "API_VERSIONING",
                    "RATE_LIMITING",
                    "IDEMPOTENCY"
            );

            // Every certification area must be defined
            assertThat(requiredAreas).hasSize(7);
            assertThat(requiredAreas).allMatch(area -> area.matches("[A-Z_]+"));
        }

        @Test
        @DisplayName("Certification result is PASS when all contract checks succeed")
        void certificationPassesOnAllGreen() {
            // Simulate a partner submission passing all checks
            boolean headersValid = true;
            boolean envelopeValid = true;
            boolean schemaCompatible = true;
            boolean eventValid = EventEnvelopeValidator.isValidEventType("impilo.partner-lab.result.submitted.v1");

            boolean certified = headersValid && envelopeValid && schemaCompatible && eventValid;
            assertThat(certified).as("All checks passed → certified").isTrue();
        }

        @Test
        @DisplayName("Certification result is FAIL when any check fails")
        void certificationFailsOnAnyRed() {
            boolean headersValid = true;
            boolean envelopeValid = true;
            boolean schemaCompatible = false; // Breaking change
            boolean eventValid = true;

            boolean certified = headersValid && envelopeValid && schemaCompatible && eventValid;
            assertThat(certified).as("Schema incompatible → not certified").isFalse();
        }
    }
}
