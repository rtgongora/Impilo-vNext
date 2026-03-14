package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.sharedkernel.events.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test verifying that VITO outbox rows produce v1.1 compliant EventEnvelope JSON.
 * Tests can run standalone (no Spring context needed).
 */
@DisplayName("VITO Outbox → v1.1 EventEnvelope contract")
class OutboxV11EnvelopeContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void cleanup() {
        System.clearProperty("EMIT_MODE");
    }

    @Test
    @DisplayName("Outbox row produces v1.1 envelope with all required fields")
    void outboxRowProducesValidEnvelope() throws Exception {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-001")
                .eventType("impilo.vito.client.created.v1")
                .schemaVersion(1)
                .tenantId("tenant-zw")
                .podId("national")
                .correlationId("corr-abc")
                .causationId("cause-def")
                .idempotencyKey("idem-123")
                .occurredAt(OffsetDateTime.now())
                .subjectType("Patient")
                .subjectId("cli-001")
                .deltaCreate(Map.of("given_name", "Jane", "family_name", "Doe"))
                .build();

        String json = CompanionOutboxPublisher.serializeEnvelope(envelope);
        JsonNode node = MAPPER.readTree(json);

        // All v1.1 required fields
        assertNotNull(node.get("event_id"), "event_id required");
        assertEquals("impilo.vito.client.created.v1", node.get("event_type").asText());
        assertTrue(node.get("schema_version").asInt() >= 1, "schema_version >= 1");
        assertEquals("corr-abc", node.get("correlation_id").asText());
        assertEquals("cause-def", node.get("causation_id").asText());
        assertEquals("idem-123", node.get("idempotency_key").asText());
        assertEquals("vito", node.get("producer").asText());
        assertEquals("tenant-zw", node.get("tenant_id").asText());
        assertEquals("national", node.get("pod_id").asText());
        assertNotNull(node.get("occurred_at"), "occurred_at required");
        assertNotNull(node.get("emitted_at"), "emitted_at required");
        assertEquals("Patient", node.get("subject_type").asText());
        assertEquals("cli-001", node.get("subject_id").asText());

        // Payload is DeltaPayload
        JsonNode payload = node.get("payload");
        assertEquals("CREATE", payload.get("op").asText());
        assertNotNull(payload.get("after"));
        assertNotNull(payload.get("changed_fields"));

        // Meta contains partition_key
        JsonNode meta = node.get("meta");
        assertNotNull(meta.get("partition_key"), "meta.partition_key required");
    }

    @Test
    @DisplayName("Event type follows naming convention")
    void eventTypeFollowsConvention() {
        EventTopicRegistry registry = new EventTopicRegistry("vito");
        String eventType = registry.eventType("client", "created");
        assertTrue(eventType.matches("impilo\\.vito\\.[a-z_]+\\.[a-z_]+\\.v\\d+"));
        assertTrue(registry.isValidEventType(eventType));
    }

    @Test
    @DisplayName("EMIT_MODE V1_1_ONLY skips legacy events")
    void v11OnlySkipsLegacy() {
        System.setProperty("EMIT_MODE", "V1_1_ONLY");
        DualEmitPolicy policy = new DualEmitPolicy();
        assertFalse(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    @DisplayName("EMIT_MODE LEGACY_ONLY skips v1.1 events")
    void legacyOnlySkipsV11() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        DualEmitPolicy policy = new DualEmitPolicy();
        assertTrue(policy.emitLegacy());
        assertFalse(policy.emitV11());
    }

    @Test
    @DisplayName("EMIT_MODE DUAL emits both")
    void dualEmitsBoth() {
        System.setProperty("EMIT_MODE", "DUAL");
        DualEmitPolicy policy = new DualEmitPolicy();
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    @DisplayName("Config fallback precedence: sys prop > env > yml > default")
    void configPrecedence() {
        // With yml fallback = V1_1_ONLY and system property = LEGACY_ONLY
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        DualEmitPolicy policy = new DualEmitPolicy("V1_1_ONLY");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
    }

    @Test
    @DisplayName("partition_key present in meta")
    void partitionKeyPresent() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-001")
                .eventType("impilo.vito.client.created.v1")
                .tenantId("t1")
                .podId("p1")
                .payload(Map.of())
                .build();

        assertNotNull(envelope.meta().get("partition_key"));
        assertEquals("cli-001", envelope.partitionKey());
    }
}
