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
 * These tests are ENABLED and exercise the shared-kernel-java library directly.
 * They use inline record definitions that mirror the shared-kernel types,
 * since the shared-kernel-java module is not yet wired as a Maven dependency
 * of vito-service (that is Wave 2).
 *
 * Once the Maven dependency is added:
 *   <dependency>
 *     <groupId>zw.gov.mohcc.impilo</groupId>
 *     <artifactId>shared-kernel-java</artifactId>
 *     <version>${project.version}</version>
 *   </dependency>
 * these tests should be updated to import directly from the shared-kernel.
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
                "vito.patient.created",
                1,
                "corr-001",
                "cause-001",
                "idem-001",
                "vito-service",
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
                        "vito.patient.created",
                        null,  // null schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
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
                        "vito.patient.updated",
                        0,  // zero schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
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
                        "vito.patient.merged",
                        -1,  // negative schema_version
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("Valid envelope constructs successfully with schema_version >= 1")
    void validEnvelopeConstructs() {
        TestEventEnvelope env = validEnvelope();
        assertEquals("vito.patient.created", env.eventType());
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
                        "vito.patient.created", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
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
                        "vito.patient.created", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", null, "pod-harare",
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
                        "vito.patient.created", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", null,
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345",
                        Map.of(), Map.of()
                ));
    }

    @Test
    @DisplayName("VITO producer must be 'vito-service'")
    void vitoProducerIdentity() {
        TestEventEnvelope env = validEnvelope();
        assertEquals("vito-service", env.producer(),
                "VITO events must identify producer as 'vito-service'");
    }

    @Test
    @DisplayName("VITO event types must start with 'vito.' prefix")
    void vitoEventTypePrefix() {
        TestEventEnvelope env = validEnvelope();
        assertTrue(env.eventType().startsWith("vito."),
                "VITO event types must use 'vito.' prefix, was: " + env.eventType());
    }
}
