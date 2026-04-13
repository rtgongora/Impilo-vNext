package zw.gov.mohcc.impilo.contract.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that event envelope JSON conforms to the Impilo v1.1 EventEnvelope contract.
 *
 * <p>Required fields (15):
 * eventId, eventType, schemaVersion, correlationId, causationId, idempotencyKey,
 * producer, tenantId, podId, occurredAt, emittedAt, subjectType, subjectId, payload, meta.
 *
 * <p>Also validates:
 * <ul>
 *   <li>eventType naming convention: {@code impilo.{service}.{entity}.{action}.v{N}}</li>
 *   <li>schemaVersion >= 1</li>
 *   <li>payload must be an object</li>
 *   <li>meta must be an object (may be empty)</li>
 * </ul>
 */
public final class EventEnvelopeValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Canonical event type pattern: impilo.{service}.{entity}.{action}.v{version}
     * Supports snapshot types: impilo.{service}.snapshot.{entity}.v{version}
     */
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile(
            "^impilo\\.[a-z][a-z0-9_-]*\\.[a-z][a-z0-9._]*\\.[a-z][a-z0-9_]*\\.v\\d+$"
    );

    private static final List<String> REQUIRED_FIELDS = List.of(
            "eventId", "eventType", "schemaVersion", "correlationId", "causationId",
            "idempotencyKey", "producer", "tenantId", "podId", "occurredAt",
            "emittedAt", "subjectType", "subjectId", "payload", "meta"
    );

    // Snake-case variants (for JSON serialized from outbox rows)
    private static final List<String> REQUIRED_FIELDS_SNAKE = List.of(
            "event_id", "event_type", "schema_version", "correlation_id", "causation_id",
            "idempotency_key", "producer", "tenant_id", "pod_id", "occurred_at",
            "emitted_at", "subject_type", "subject_id", "payload", "meta"
    );

    private EventEnvelopeValidator() {}

    /**
     * Validate an event envelope JSON node.
     *
     * @param envelope the JSON representation of an EventEnvelope
     * @return validation result with any violations
     */
    public static EnvelopeValidationResult validate(JsonNode envelope) {
        List<String> violations = new ArrayList<>();

        if (envelope == null || envelope.isNull()) {
            return EnvelopeValidationResult.invalid(List.of("envelope is null"));
        }

        // Detect casing convention
        boolean isSnakeCase = envelope.has("event_id") || envelope.has("event_type");
        List<String> fields = isSnakeCase ? REQUIRED_FIELDS_SNAKE : REQUIRED_FIELDS;

        // Check required fields
        for (String field : fields) {
            if (!envelope.has(field) || envelope.get(field).isNull()) {
                violations.add("missing required field: " + field);
            }
        }

        // Validate eventType naming
        String eventTypeField = isSnakeCase ? "event_type" : "eventType";
        if (envelope.has(eventTypeField) && !envelope.get(eventTypeField).isNull()) {
            String eventType = envelope.get(eventTypeField).asText();
            if (!EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
                violations.add("eventType '" + eventType + "' does not match pattern: impilo.{service}.{entity}.{action}.v{N}");
            }
        }

        // Validate schemaVersion >= 1
        String schemaVersionField = isSnakeCase ? "schema_version" : "schemaVersion";
        if (envelope.has(schemaVersionField) && !envelope.get(schemaVersionField).isNull()) {
            JsonNode sv = envelope.get(schemaVersionField);
            if (sv.isNumber() && sv.intValue() < 1) {
                violations.add("schemaVersion must be >= 1, was: " + sv.intValue());
            } else if (sv.isTextual()) {
                try {
                    int ver = Integer.parseInt(sv.asText());
                    if (ver < 1) violations.add("schemaVersion must be >= 1, was: " + ver);
                } catch (NumberFormatException e) {
                    violations.add("schemaVersion is not a valid integer: " + sv.asText());
                }
            }
        }

        // Validate payload is an object
        String payloadField = "payload";
        if (envelope.has(payloadField) && !envelope.get(payloadField).isNull()) {
            if (!envelope.get(payloadField).isObject()) {
                violations.add("payload must be a JSON object");
            }
        }

        // Validate meta is an object
        String metaField = "meta";
        if (envelope.has(metaField) && !envelope.get(metaField).isNull()) {
            if (!envelope.get(metaField).isObject()) {
                violations.add("meta must be a JSON object");
            }
        }

        if (violations.isEmpty()) {
            return EnvelopeValidationResult.passed();
        }
        return EnvelopeValidationResult.invalid(violations);
    }

    /**
     * Validate from JSON string.
     */
    public static EnvelopeValidationResult validate(String envelopeJson) {
        try {
            JsonNode node = MAPPER.readTree(envelopeJson);
            return validate(node);
        } catch (Exception e) {
            return EnvelopeValidationResult.invalid(List.of("Failed to parse JSON: " + e.getMessage()));
        }
    }

    /**
     * Validate that an event type string follows the naming convention.
     */
    public static boolean isValidEventType(String eventType) {
        return eventType != null && EVENT_TYPE_PATTERN.matcher(eventType).matches();
    }

    /**
     * Extract the service name from a valid event type.
     * For "impilo.vito.patient.created.v1" returns "vito".
     */
    public static String extractService(String eventType) {
        if (eventType == null) return null;
        String[] parts = eventType.split("\\.");
        return parts.length >= 2 ? parts[1] : null;
    }
}
