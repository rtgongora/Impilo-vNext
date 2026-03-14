package zw.gov.mohcc.impilo.contract.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("Valid envelopes")
    class ValidEnvelopes {

        @Test
        @DisplayName("Complete camelCase envelope passes validation")
        void validCamelCaseEnvelope() {
            ObjectNode envelope = buildValidEnvelope(false);

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("Complete snake_case envelope passes validation")
        void validSnakeCaseEnvelope() {
            ObjectNode envelope = buildValidEnvelope(true);

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingFields {

        @Test
        @DisplayName("Missing eventId is a violation")
        void missingEventId() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.remove("eventId");

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("eventId"));
        }

        @Test
        @DisplayName("Missing payload is a violation")
        void missingPayload() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.remove("payload");

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("payload"));
        }

        @Test
        @DisplayName("Null envelope is invalid")
        void nullEnvelope() {
            EnvelopeValidationResult result = EventEnvelopeValidator.validate((String) null);

            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Event type naming convention")
    class EventTypeNaming {

        @ParameterizedTest
        @ValueSource(strings = {
                "impilo.vito.patient.created.v1",
                "impilo.tuso.encounter.started.v1",
                "impilo.offline-edge.vital.captured.v1",
                "impilo.zibo.order.submitted.v2",
                "impilo.audit-ledger.record.appended.v1",
                "impilo.data-governance.policy.created.v1",
                "impilo.vito.snapshot.client.v1"
        })
        @DisplayName("Valid event type patterns")
        void validEventTypes(String eventType) {
            assertThat(EventEnvelopeValidator.isValidEventType(eventType)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not.impilo.format",
                "impilo.vito",
                "impilo.vito.patient",
                "impilo.vito.patient.created",
                "IMPILO.VITO.PATIENT.CREATED.V1",
                "impilo.vito.Patient.created.v1",
                ""
        })
        @DisplayName("Invalid event type patterns")
        void invalidEventTypes(String eventType) {
            assertThat(EventEnvelopeValidator.isValidEventType(eventType)).isFalse();
        }

        @Test
        @DisplayName("Invalid eventType in envelope produces violation")
        void invalidEventTypeInEnvelope() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.put("eventType", "bad_event_name");

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("does not match pattern"));
        }
    }

    @Nested
    @DisplayName("Schema version validation")
    class SchemaVersion {

        @Test
        @DisplayName("Schema version 0 is invalid")
        void schemaVersionZero() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.put("schemaVersion", 0);

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("schemaVersion"));
        }

        @Test
        @DisplayName("Negative schema version is invalid")
        void negativeSchemaVersion() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.put("schemaVersion", -1);

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Payload and meta validation")
    class PayloadMeta {

        @Test
        @DisplayName("Non-object payload is invalid")
        void nonObjectPayload() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.put("payload", "not-an-object");

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("payload must be a JSON object"));
        }

        @Test
        @DisplayName("Empty meta object is valid")
        void emptyMetaIsValid() {
            ObjectNode envelope = buildValidEnvelope(false);
            envelope.set("meta", MAPPER.createObjectNode());

            EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);

            assertThat(result.valid()).isTrue();
        }
    }

    @Test
    @DisplayName("extractService returns correct service name")
    void extractService() {
        assertThat(EventEnvelopeValidator.extractService("impilo.vito.patient.created.v1")).isEqualTo("vito");
        assertThat(EventEnvelopeValidator.extractService("impilo.audit-ledger.record.appended.v1")).isEqualTo("audit-ledger");
        assertThat(EventEnvelopeValidator.extractService(null)).isNull();
    }

    // ── Helper ──

    private static ObjectNode buildValidEnvelope(boolean snakeCase) {
        ObjectNode envelope = MAPPER.createObjectNode();
        if (snakeCase) {
            envelope.put("event_id", "evt-001");
            envelope.put("event_type", "impilo.vito.patient.created.v1");
            envelope.put("schema_version", 1);
            envelope.put("correlation_id", "corr-001");
            envelope.put("causation_id", "cause-001");
            envelope.put("idempotency_key", "idem-001");
            envelope.put("producer", "vito");
            envelope.put("tenant_id", "moh-zw");
            envelope.put("pod_id", "national");
            envelope.put("occurred_at", "2026-03-14T10:00:00Z");
            envelope.put("emitted_at", "2026-03-14T10:00:01Z");
            envelope.put("subject_type", "Patient");
            envelope.put("subject_id", "CPID-12345");
        } else {
            envelope.put("eventId", "evt-001");
            envelope.put("eventType", "impilo.vito.patient.created.v1");
            envelope.put("schemaVersion", 1);
            envelope.put("correlationId", "corr-001");
            envelope.put("causationId", "cause-001");
            envelope.put("idempotencyKey", "idem-001");
            envelope.put("producer", "vito");
            envelope.put("tenantId", "moh-zw");
            envelope.put("podId", "national");
            envelope.put("occurredAt", "2026-03-14T10:00:00Z");
            envelope.put("emittedAt", "2026-03-14T10:00:01Z");
            envelope.put("subjectType", "Patient");
            envelope.put("subjectId", "CPID-12345");
        }
        envelope.set("payload", MAPPER.createObjectNode().put("action", "test"));
        envelope.set("meta", MAPPER.createObjectNode());
        return envelope;
    }
}
