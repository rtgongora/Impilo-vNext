package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventBuilderTest {

    @Test
    void buildsDeltaCreateEnvelope() {
        Map<String, Object> after = Map.of("name", "John", "status", "ACTIVE");

        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-123")
                .eventType("impilo.vito.client.created.v1")
                .tenantId("tenant-1")
                .podId("national")
                .correlationId("corr-1")
                .idempotencyKey("idem-1")
                .causationId("cause-1")
                .occurredAt(OffsetDateTime.now())
                .deltaCreate(after)
                .build();

        assertNotNull(envelope.eventId());
        assertEquals("impilo.vito.client.created.v1", envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals("vito", envelope.producer());
        assertEquals("tenant-1", envelope.tenantId());
        assertEquals("national", envelope.podId());
        assertEquals("corr-1", envelope.correlationId());
        assertEquals("idem-1", envelope.idempotencyKey());
        assertEquals("CLIENT", envelope.subjectType());
        assertEquals("cli-123", envelope.subjectId());

        // Payload should contain delta fields
        assertEquals("CREATE", envelope.payload().get("op"));
        assertNull(envelope.payload().get("before"));
        assertNotNull(envelope.payload().get("after"));
        assertNotNull(envelope.payload().get("changed_fields"));

        // Meta should contain partition_key
        assertEquals("cli-123", envelope.meta().get("partition_key"));
        assertEquals("cli-123", envelope.partitionKey());
    }

    @Test
    void buildsDeltaUpdateEnvelope() {
        Map<String, Object> before = Map.of("name", "John");
        Map<String, Object> after = Map.of("name", "Jane");

        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-123")
                .eventType("impilo.vito.client.updated.v1")
                .tenantId("t1")
                .podId("p1")
                .deltaUpdate(before, after, List.of("name"))
                .build();

        assertEquals("UPDATE", envelope.payload().get("op"));
        assertEquals(before, envelope.payload().get("before"));
        assertEquals(after, envelope.payload().get("after"));
        assertEquals(List.of("name"), envelope.payload().get("changed_fields"));
    }

    @Test
    void buildsDeltaDeleteEnvelope() {
        Map<String, Object> before = Map.of("name", "John");

        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-123")
                .eventType("impilo.vito.client.deleted.v1")
                .tenantId("t1")
                .podId("p1")
                .deltaDelete(before)
                .build();

        assertEquals("DELETE", envelope.payload().get("op"));
        assertEquals(before, envelope.payload().get("before"));
        assertNull(envelope.payload().get("after"));
    }

    @Test
    void customPartitionKeyOverridesDefault() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("msika")
                .aggregateType("CATALOG")
                .aggregateId("cat-1")
                .eventType("impilo.msika.catalog.created.v1")
                .tenantId("t1")
                .podId("p1")
                .partitionKey("custom-key")
                .payload(Map.of("data", "value"))
                .build();

        assertEquals("custom-key", envelope.partitionKey());
        assertEquals("custom-key", envelope.meta().get("partition_key"));
    }

    @Test
    void subjectDefaultsToAggregate() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("tuso")
                .aggregateType("FACILITY")
                .aggregateId("fac-1")
                .eventType("impilo.tuso.facility.created.v1")
                .tenantId("t1")
                .podId("p1")
                .payload(Map.of())
                .build();

        assertEquals("FACILITY", envelope.subjectType());
        assertEquals("fac-1", envelope.subjectId());
    }

    @Test
    void explicitSubjectOverridesAggregate() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("tuso")
                .aggregateType("FACILITY")
                .aggregateId("fac-1")
                .eventType("impilo.tuso.facility.created.v1")
                .tenantId("t1")
                .podId("p1")
                .subjectType("Facility")
                .subjectId("sub-1")
                .payload(Map.of())
                .build();

        assertEquals("Facility", envelope.subjectType());
        assertEquals("sub-1", envelope.subjectId());
    }

    @Test
    void autoGeneratesOptionalFields() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-1")
                .eventType("impilo.vito.client.created.v1")
                .payload(Map.of())
                .build();

        // Should auto-generate IDs and default to "unknown" for missing context
        assertNotNull(envelope.eventId());
        assertNotNull(envelope.correlationId());
        assertNotNull(envelope.causationId());
        assertNotNull(envelope.idempotencyKey());
        assertNotNull(envelope.emittedAt());
        assertNotNull(envelope.occurredAt());
        assertEquals("unknown", envelope.tenantId());
        assertEquals("unknown", envelope.podId());
    }

    @Test
    void rejectsNullProducer() {
        assertThrows(NullPointerException.class, () -> OutboxEventBuilder.forProducer(null));
    }

    @Test
    void extraMetaIsMerged() {
        EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
                .aggregateType("CLIENT")
                .aggregateId("cli-1")
                .eventType("impilo.vito.client.created.v1")
                .tenantId("t1")
                .podId("p1")
                .extraMeta(Map.of("trace_id", "abc123"))
                .payload(Map.of())
                .build();

        assertEquals("abc123", envelope.meta().get("trace_id"));
        assertNotNull(envelope.meta().get("partition_key"));
    }
}
