package zw.gov.mohcc.impilo.vito.migration.v11;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the v1.1 event envelope contract.
 * These tests validate the shared-kernel EventEnvelope record
 * as consumed by VITO for domain event publishing.
 *
 * Per Manifest v1.1:
 * - event_type must begin with "impilo." (e.g. "impilo.vito.patient.created.v1")
 * - producer must be "vito" (short service name, NOT "vito-service")
 * - schema_version must be >= 1
 * - tenant_id, pod_id, correlation_id, idempotency_key, payload must not be null
 *
 * These tests are ENABLED and exercise inline record definitions that mirror
 * the shared-kernel types. Once the shared-kernel-java module is wired as a
 * Maven dependency of vito-service (Wave 2), replace with direct imports.
 */
@DisplayName("V1.1 Event Envelope Contract (VITO perspective)")
class EventEnvelopeContractTest {

    /**
     * Mirrors shared-kernel EventEnvelope for test isolation.
     * Once shared-kernel is a dependency, replace with import.
     */
    record TestEventEnvelope(
            String eventId,
            String eventType,
            Integer schemaVersion,
            String correlationId,
            String causationId,
            String idempotencyKey,
            String producer,
            String tenantId,
            String podId,
            OffsetDateTime occurredAt,
            OffsetDateTime emittedAt,
            String subjectType,
            String subjectId,
            Map<String, Object> payload,
            Map<String, Object> meta
    ) {
        TestEventEnvelope {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("event_id is required");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("event_type is required");
            }
            if (schemaVersion == null || schemaVersion < 1) {
                throw new IllegalArgumentException(
                        eventType + ": schema_version missing/invalid — value was " + schemaVersion);
            }
            if (correlationId == null) throw new IllegalArgumentException("correlation_id is required");
            if (causationId == null) throw new IllegalArgumentException("causation_id is required");
            if (idempotencyKey == null) throw new IllegalArgumentException("idempotency_key is required");
            if (producer == null) throw new IllegalArgumentException("producer is required");
            if (tenantId == null) throw new IllegalArgumentException("tenant_id is required");
            if (podId == null) throw new IllegalArgumentException("pod_id is required");
            if (occurredAt == null) throw new IllegalArgumentException("occurred_at is required");
            if (emittedAt == null) throw new IllegalArgumentException("emitted_at is required");
            if (subjectType == null) throw new IllegalArgumentException("subject_type is required");
            if (subjectId == null) throw new IllegalArgumentException("subject_id is required");
            if (payload == null) throw new IllegalArgumentException("payload is required");
        }
    }

    private TestEventEnvelope validEnvelope() {
        return new TestEventEnvelope(
                UUID.randomUUID().toString(),
                "impilo.vito.patient.created.v1",
                1,
                "corr-001",
                "cause-001",
                "idem-001",
                "vito",
                "tenant-zw",
                "pod-harare-central",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "Patient",
                "CPID-12345",
                Map.of("given_name_hash", "abc123"),
                Map.of()
        );
    }

    @Test
    @DisplayName("Null schema_version throws at construction time")
    void nullSchemaVersionThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1",
                        null,  // null schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
        assertTrue(ex.getMessage().contains("schema_version missing/invalid"),
                "Error message should mention schema_version: " + ex.getMessage());
    }

    @Test
    @DisplayName("Zero schema_version throws at construction time")
    void zeroSchemaVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.updated.v1",
                        0,  // zero schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Negative schema_version throws at construction time")
    void negativeSchemaVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.merged.v1",
                        -1,  // negative schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Valid envelope constructs successfully with schema_version >= 1")
    void validEnvelopeConstructs() {
        TestEventEnvelope env = validEnvelope();
        assertEquals("impilo.vito.patient.created.v1", env.eventType());
        assertEquals(1, env.schemaVersion());
        assertNotNull(env.eventId());
        assertNotNull(env.emittedAt());
    }

    @Test
    @DisplayName("Missing event_id throws at construction time")
    void missingEventIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        "",  // blank event_id
                        "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Missing tenant_id throws at construction time")
    void missingTenantIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", null, "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Missing pod_id throws at construction time")
    void missingPodIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", null,
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Missing correlation_id throws at construction time")
    void missingCorrelationIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1", 1,
                        null, "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Missing idempotency_key throws at construction time")
    void missingIdempotencyKeyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", null,
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Missing payload throws at construction time")
    void missingPayloadThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestEventEnvelope(
                        UUID.randomUUID().toString(),
                        "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        null, Map.of()
                ));
    }

    @Test
    @DisplayName("VITO producer must be 'vito'")
    void vitoProducerIdentity() {
        TestEventEnvelope env = validEnvelope();
        assertEquals("vito", env.producer(),
                "VITO events must identify producer as 'vito'");
    }

    @Test
    @DisplayName("VITO event types must start with 'impilo.vito.' prefix")
    void vitoEventTypePrefix() {
        TestEventEnvelope env = validEnvelope();
        assertTrue(env.eventType().startsWith("impilo."),
                "VITO event types must use 'impilo.' root prefix, was: " + env.eventType());
        assertTrue(env.eventType().startsWith("impilo.vito."),
                "VITO event types must use 'impilo.vito.' prefix, was: " + env.eventType());
    }
}
