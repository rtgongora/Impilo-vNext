package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.sharedkernel.events.*;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test verifying TUSO (Ring 0 Facility Registry) outbox → v1.1 envelope.
 */
@DisplayName("TUSO Outbox → v1.1 EventEnvelope contract")
class OutboxV11EnvelopeContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void cleanup() { System.clearProperty("EMIT_MODE"); }

    @Test
    @DisplayName("Facility create event produces valid v1.1 envelope")
    void facilityCreateEnvelope() throws Exception {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("tuso")
                .aggregateType("FACILITY")
                .aggregateId("fac-001")
                .eventType("impilo.tuso.facility.created.v1")
                .tenantId("tenant-zw")
                .podId("national")
                .correlationId("corr-1")
                .causationId("cause-1")
                .idempotencyKey("idem-1")
                .occurredAt(OffsetDateTime.now())
                .subjectType("Facility")
                .subjectId("fac-001")
                .deltaCreate(Map.of("name", "Harare Central", "type", "HOSPITAL"))
                .build();

        String json = CompanionOutboxPublisher.serializeEnvelope(envelope);
        JsonNode node = MAPPER.readTree(json);

        assertEquals("impilo.tuso.facility.created.v1", node.get("event_type").asText());
        assertEquals("tuso", node.get("producer").asText());
        assertEquals("Facility", node.get("subject_type").asText());
        assertTrue(node.get("schema_version").asInt() >= 1);
        assertNotNull(node.get("meta").get("partition_key"));
        assertEquals("CREATE", node.get("payload").get("op").asText());
    }

    @Test
    @DisplayName("EventTopicRegistry produces correct TUSO topics")
    void tusoTopicRegistry() {
        EventTopicRegistry registry = new EventTopicRegistry("tuso");
        assertEquals("impilo.tuso.facility.created.v1", registry.eventType("facility", "created"));
        assertEquals("impilo.tuso.facility", registry.v11Topic("facility"));
        assertEquals("impilo.tuso.snapshots", registry.snapshotTopic());
        assertEquals("impilo.tuso.snapshot.facility.v1", registry.snapshotEventType("facility"));
    }

    @Test
    @DisplayName("EMIT_MODE changes behavior for TUSO publisher")
    void emitModeChangesBehavior() {
        System.setProperty("EMIT_MODE", "V1_1_ONLY");
        DualEmitPolicy policy = new DualEmitPolicy("DUAL"); // config says DUAL
        assertEquals(EmitMode.V1_1_ONLY, policy.mode()); // sys prop wins
        assertFalse(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }
}
