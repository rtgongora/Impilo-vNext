package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CompanionOutboxPublisherTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("EMIT_MODE");
    }

    // ── Test harness: in-memory publisher ──

    static class TestOutboxRow implements CompanionOutboxPublisher.OutboxRow {
        Long id;
        String aggregateType, aggregateId, eventType, payloadJson;
        String tenantId, podId, correlationId, causationId, idempotencyKey;
        String subjectType, subjectId, partitionKey;
        OffsetDateTime occurredAt, publishedAt;
        int schemaVersion = 1;

        @Override public Long id() { return id; }
        @Override public String aggregateType() { return aggregateType; }
        @Override public String aggregateId() { return aggregateId; }
        @Override public String eventType() { return eventType; }
        @Override public String payloadJson() { return payloadJson; }
        @Override public OffsetDateTime occurredAt() { return occurredAt; }
        @Override public OffsetDateTime publishedAt() { return publishedAt; }
        @Override public String tenantId() { return tenantId; }
        @Override public String podId() { return podId; }
        @Override public String correlationId() { return correlationId; }
        @Override public String causationId() { return causationId; }
        @Override public String idempotencyKey() { return idempotencyKey; }
        @Override public int schemaVersion() { return schemaVersion; }
        @Override public String subjectType() { return subjectType != null ? subjectType : aggregateType; }
        @Override public String subjectId() { return subjectId != null ? subjectId : aggregateId; }
        @Override public String partitionKey() { return partitionKey != null ? partitionKey : aggregateId; }
    }

    static class TestPublisher extends CompanionOutboxPublisher {
        List<TestOutboxRow> rows = new ArrayList<>();
        List<String> sentTopics = new ArrayList<>();
        List<String> sentKeys = new ArrayList<>();
        List<String> sentValues = new ArrayList<>();
        Set<Long> publishedIds = new HashSet<>();
        Map<Long, String> failedIds = new HashMap<>();

        TestPublisher(String emitMode) {
            super(new DualEmitPolicy(emitMode), new EventTopicRegistry("test"));
        }

        @Override protected List<OutboxRow> fetchUnpublished() {
            return new ArrayList<>(rows);
        }
        @Override protected void sendToKafka(String topic, String key, String value) {
            sentTopics.add(topic);
            sentKeys.add(key);
            sentValues.add(value);
        }
        @Override protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
            publishedIds.add(row.id());
        }
        @Override protected void markFailed(OutboxRow row, String errorMessage) {
            failedIds.put(row.id(), errorMessage);
        }
        @Override protected String resolveLegacyTopic(OutboxRow row) {
            return "test.legacy." + row.aggregateType().toLowerCase();
        }
    }

    private TestOutboxRow createRow(long id, String aggType, String aggId) {
        TestOutboxRow row = new TestOutboxRow();
        row.id = id;
        row.aggregateType = aggType;
        row.aggregateId = aggId;
        row.eventType = "impilo.test." + aggType.toLowerCase() + ".created.v1";
        row.payloadJson = "{\"name\":\"test\"}";
        row.tenantId = "tenant-1";
        row.podId = "national";
        row.correlationId = "corr-1";
        row.causationId = "cause-1";
        row.idempotencyKey = "idem-" + id;
        row.occurredAt = OffsetDateTime.now();
        return row;
    }

    // ── Tests ──

    @Test
    void dualModeEmitsBothLegacyAndV11() {
        TestPublisher pub = new TestPublisher("DUAL");
        pub.rows.add(createRow(1L, "WIDGET", "w-1"));

        int count = pub.publishPendingEvents();

        assertEquals(1, count);
        // DUAL emits 2 messages: legacy + v1.1
        assertEquals(2, pub.sentTopics.size());
        assertEquals("test.legacy.widget", pub.sentTopics.get(0));
        assertEquals("impilo.test.widget", pub.sentTopics.get(1));
        assertTrue(pub.publishedIds.contains(1L));
    }

    @Test
    void legacyOnlyEmitsOnlyLegacy() {
        TestPublisher pub = new TestPublisher("LEGACY_ONLY");
        pub.rows.add(createRow(1L, "WIDGET", "w-1"));

        pub.publishPendingEvents();

        assertEquals(1, pub.sentTopics.size());
        assertEquals("test.legacy.widget", pub.sentTopics.get(0));
    }

    @Test
    void v11OnlyEmitsOnlyV11() {
        TestPublisher pub = new TestPublisher("V1_1_ONLY");
        pub.rows.add(createRow(1L, "WIDGET", "w-1"));

        pub.publishPendingEvents();

        assertEquals(1, pub.sentTopics.size());
        assertEquals("impilo.test.widget", pub.sentTopics.get(0));
    }

    @Test
    void systemPropertyOverridesConfigFallback() {
        System.setProperty("EMIT_MODE", "V1_1_ONLY");
        TestPublisher pub = new TestPublisher("LEGACY_ONLY"); // config says LEGACY
        pub.rows.add(createRow(1L, "WIDGET", "w-1"));

        pub.publishPendingEvents();

        // System property wins → V1_1_ONLY
        assertEquals(1, pub.sentTopics.size());
        assertEquals("impilo.test.widget", pub.sentTopics.get(0));
    }

    @Test
    void v11EnvelopeContainsRequiredFields() throws Exception {
        TestPublisher pub = new TestPublisher("V1_1_ONLY");
        pub.rows.add(createRow(1L, "WIDGET", "w-1"));

        pub.publishPendingEvents();

        String json = pub.sentValues.get(0);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = mapper.readTree(json);

        // All v1.1 required fields present
        assertNotNull(envelope.get("event_id"));
        assertEquals("impilo.test.widget.created.v1", envelope.get("event_type").asText());
        assertTrue(envelope.get("schema_version").asInt() >= 1);
        assertNotNull(envelope.get("correlation_id"));
        assertNotNull(envelope.get("causation_id"));
        assertNotNull(envelope.get("idempotency_key"));
        assertEquals("test", envelope.get("producer").asText());
        assertEquals("tenant-1", envelope.get("tenant_id").asText());
        assertEquals("national", envelope.get("pod_id").asText());
        assertNotNull(envelope.get("occurred_at"));
        assertNotNull(envelope.get("emitted_at"));
        assertNotNull(envelope.get("subject_type"));
        assertNotNull(envelope.get("subject_id"));
        assertNotNull(envelope.get("payload"));

        // Meta contains partition_key
        assertNotNull(envelope.get("meta"));
        assertNotNull(envelope.get("meta").get("partition_key"));
    }

    @Test
    void partitionKeyUsedAsKafkaMessageKey() {
        TestPublisher pub = new TestPublisher("V1_1_ONLY");
        TestOutboxRow row = createRow(1L, "WIDGET", "w-1");
        row.partitionKey = "custom-pk";
        pub.rows.add(row);

        pub.publishPendingEvents();

        assertEquals("custom-pk", pub.sentKeys.get(0));
    }

    @Test
    void emptyBatchReturnsZero() {
        TestPublisher pub = new TestPublisher("DUAL");
        assertEquals(0, pub.publishPendingEvents());
    }

    @Test
    void multipleRowsPublishedInOrder() {
        TestPublisher pub = new TestPublisher("LEGACY_ONLY");
        pub.rows.add(createRow(1L, "A", "a-1"));
        pub.rows.add(createRow(2L, "B", "b-1"));
        pub.rows.add(createRow(3L, "C", "c-1"));

        int count = pub.publishPendingEvents();

        assertEquals(3, count);
        assertEquals(3, pub.publishedIds.size());
    }

    @Test
    void effectiveEmitModeReflectsConfig() {
        TestPublisher pub = new TestPublisher("V1_1_ONLY");
        assertEquals(EmitMode.V1_1_ONLY, pub.effectiveEmitMode());
    }

    @Test
    void defaultEmitModeIsDual() {
        TestPublisher pub = new TestPublisher(null);
        assertEquals(EmitMode.DUAL, pub.effectiveEmitMode());
    }

    @Test
    void v11PayloadCanContainDelta() throws Exception {
        TestPublisher pub = new TestPublisher("V1_1_ONLY");
        TestOutboxRow row = createRow(1L, "WIDGET", "w-1");
        row.payloadJson = "{\"delta\":{\"op\":\"UPDATE\",\"changed_fields\":[\"name\"]},\"full\":{\"name\":\"test\"}}";
        pub.rows.add(row);

        pub.publishPendingEvents();

        String json = pub.sentValues.get(0);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = mapper.readTree(json);
        var payload = envelope.get("payload");

        assertNotNull(payload.get("delta"));
        assertEquals("UPDATE", payload.get("delta").get("op").asText());
        assertEquals("test", payload.get("full").get("name").asText());
    }
}
