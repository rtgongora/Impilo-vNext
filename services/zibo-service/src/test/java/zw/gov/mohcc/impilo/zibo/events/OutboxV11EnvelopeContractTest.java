package zw.gov.mohcc.impilo.zibo.events;

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
 * Contract test verifying ZIBO (Ring 0 Terminology Service) outbox → v1.1 envelope.
 */
@DisplayName("ZIBO Outbox → v1.1 EventEnvelope contract")
class OutboxV11EnvelopeContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void cleanup() { System.clearProperty("EMIT_MODE"); }

    @Test
    @DisplayName("Artifact publish event produces valid v1.1 envelope")
    void artifactPublishEnvelope() throws Exception {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("zibo")
                .aggregateType("ARTIFACT")
                .aggregateId("art-001")
                .eventType("impilo.zibo.artifact.published.v1")
                .tenantId("tenant-zw")
                .podId("national")
                .correlationId("corr-1")
                .causationId("cause-1")
                .idempotencyKey("idem-1")
                .occurredAt(OffsetDateTime.now())
                .subjectType("Artifact")
                .subjectId("art-001")
                .deltaUpdate(
                        Map.of("status", "DRAFT"),
                        Map.of("status", "PUBLISHED"),
                        java.util.List.of("status"))
                .build();

        String json = CompanionOutboxPublisher.serializeEnvelope(envelope);
        JsonNode node = MAPPER.readTree(json);

        assertEquals("impilo.zibo.artifact.published.v1", node.get("event_type").asText());
        assertEquals("zibo", node.get("producer").asText());
        assertEquals("Artifact", node.get("subject_type").asText());
        assertTrue(node.get("schema_version").asInt() >= 1);
        assertEquals("UPDATE", node.get("payload").get("op").asText());
        assertNotNull(node.get("meta").get("partition_key"));
    }

    @Test
    @DisplayName("EventTopicRegistry produces correct ZIBO topics")
    void ziboTopicRegistry() {
        EventTopicRegistry registry = new EventTopicRegistry("zibo");
        assertEquals("impilo.zibo.artifact.created.v1", registry.eventType("artifact", "created"));
        assertEquals("impilo.zibo.artifact", registry.v11Topic("artifact"));
        assertEquals("impilo.zibo.snapshots", registry.snapshotTopic());
    }

    @Test
    @DisplayName("Partition key defaults to subject_id")
    void partitionKeyDefault() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("zibo")
                .aggregateType("ARTIFACT")
                .aggregateId("art-001")
                .eventType("impilo.zibo.artifact.created.v1")
                .tenantId("t1")
                .podId("p1")
                .payload(Map.of())
                .build();

        assertEquals("art-001", envelope.partitionKey());
    }

    @Test
    @DisplayName("EMIT_MODE DUAL emits both legacy and v1.1")
    void dualEmit() {
        DualEmitPolicy policy = new DualEmitPolicy("DUAL");
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }
}
