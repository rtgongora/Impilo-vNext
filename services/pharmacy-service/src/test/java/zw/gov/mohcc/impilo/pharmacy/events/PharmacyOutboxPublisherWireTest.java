package zw.gov.mohcc.impilo.pharmacy.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What pharmacy actually puts on Kafka after the CompanionOutboxPublisher conversion.
 *
 * <p>pharmacy is the canary. {@code InventoryEventConsumer.handleDispenseComplete} reads
 * {@code tenantId}, {@code facilityId}, {@code storeId} and {@code itemCode} at the JSON
 * <em>top level</em> of {@code pharmacy.dispense.complete}. Given an envelope it does not
 * throw — it logs "missing tenant/facility/store/item, skipping" and returns, so stock
 * silently stops being deducted and nothing anywhere goes red. That is why the legacy body
 * is asserted byte-for-byte rather than merely "parses".</p>
 */
class PharmacyOutboxPublisherWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Shaped like a real dispense-complete payload: every field the canary reads. */
    private static final String PAYLOAD = """
            {"tenantId":"11111111-1111-1111-1111-111111111111",\
            "facilityId":"22222222-2222-2222-2222-222222222222",\
            "storeId":"33333333-3333-3333-3333-333333333333",\
            "itemCode":"PARA-500","qty":12,"orderId":"RX-9001"}""";

    private EventOutboxRepository repository;
    private KafkaTemplate<String, String> kafka;
    private String savedEmitMode;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        savedEmitMode = System.getProperty("EMIT_MODE");
        System.setProperty("EMIT_MODE", "DUAL");
        repository = mock(EventOutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
    }

    @AfterEach
    void tearDown() {
        if (savedEmitMode == null) {
            System.clearProperty("EMIT_MODE");
        } else {
            System.setProperty("EMIT_MODE", savedEmitMode);
        }
    }

    private OutboxPublisher publisher(boolean coreTransactionEnabled) {
        return new OutboxPublisher(repository, kafka, coreTransactionEnabled,
                "core.transaction.events", null);
    }

    private EventOutboxEntity row(String aggregateType, String eventType) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setId(1L);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId("RX-9001");
        entity.setEventType(eventType);
        entity.setPayload(PAYLOAD);
        entity.setTenantId(TENANT);
        entity.setSubjectType(aggregateType);
        entity.setSubjectId("RX-9001");
        entity.setOccurredAt(OffsetDateTime.parse("2026-08-07T09:00:00Z"));
        entity.setIdempotencyKey("idem-1");
        return entity;
    }

    private List<String[]> drain(OutboxPublisher publisher, EventOutboxEntity entity) {
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        publisher.publishPendingEvents();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka, org.mockito.Mockito.atLeastOnce()).send(topic.capture(), key.capture(), value.capture());

        List<String[]> sent = new ArrayList<>();
        for (int i = 0; i < topic.getAllValues().size(); i++) {
            sent.add(new String[]{topic.getAllValues().get(i), key.getAllValues().get(i), value.getAllValues().get(i)});
        }
        return sent;
    }

    private String bodyOn(List<String[]> sent, String topic) {
        return sent.stream().filter(s -> s[0].equals(topic)).map(s -> s[2]).findFirst()
                .orElseThrow(() -> new AssertionError("nothing on " + topic
                        + "; got " + sent.stream().map(s -> s[0]).toList()));
    }

    /** Mirrors InventoryEventConsumer.text(event, "tenantId", "tenant_id"). */
    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }

    @Test
    void theCanaryTopicCarriesTheStoredPayloadByteForByte() {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(bodyOn(sent, "pharmacy.dispense.complete"))
                .as("any re-wrapping here stops stock being deducted, silently")
                .isEqualTo(PAYLOAD);
    }

    @Test
    void inventoryConsumersTopLevelReadsStillResolve() throws Exception {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        JsonNode event = MAPPER.readTree(bodyOn(sent, "pharmacy.dispense.complete"));
        assertThat(text(event, "tenantId", "tenant_id")).isEqualTo(TENANT.toString());
        assertThat(text(event, "facilityId", "facility_id")).isNotNull();
        assertThat(text(event, "storeId", "store_id")).isNotNull();
        assertThat(text(event, "itemCode", "item_code")).isEqualTo("PARA-500");
        assertThat(event.path("qty").asInt()).isEqualTo(12);
    }

    @Test
    void anEnvelopeOnTheCanaryTopicWouldFailThoseReads() throws Exception {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        // Negative control: this is the body the canary topic must never carry, and it is
        // exactly the read that returns null and makes the consumer skip without error.
        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.pharmacy.dispense"));
        assertThat(text(envelope, "facilityId", "facility_id")).isNull();
        assertThat(text(envelope, "itemCode", "item_code")).isNull();
        assertThat(envelope.path("qty").asInt()).isZero();
    }

    @Test
    void theStockMovementTopicIsAlsoReadTopLevelAndAlsoStaysRaw() {
        List<String[]> sent = drain(publisher(true), row("STOCK", "STOCK_MOVEMENT_RECORDED"));

        // handleStockMovementRecorded reads top level too — same failure mode.
        assertThat(bodyOn(sent, "pharmacy.stock.movement.recorded")).isEqualTo(PAYLOAD);
    }

    @Test
    void theCoreTransactionStreamStillReceivesTheRawPayload() {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(bodyOn(sent, "core.transaction.events")).isEqualTo(PAYLOAD);
    }

    @Test
    void theCoreTransactionStreamStaysOffWhenDisabled() {
        List<String[]> sent = drain(publisher(false), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(sent).extracting(s -> s[0])
                .doesNotContain("core.transaction.events")
                .contains("pharmacy.dispense.complete");
    }

    @Test
    void anEventOutsideTheCoreTransactionSetDoesNotReachThatStream() {
        List<String[]> sent = drain(publisher(true), row("STOCK", "WASTAGE_RECORDED"));

        assertThat(sent).extracting(s -> s[0]).doesNotContain("core.transaction.events");
    }

    @Test
    void v11TopicCarriesACanonicalEventTypeAndRealFederationContext() throws Exception {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.pharmacy.dispense"));
        assertThat(envelope.path("event_type").asText()).isEqualTo("impilo.pharmacy.dispense.completed.v1");
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
        assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(PAYLOAD));
    }

    @Test
    void aCompoundActionKeepsItsMeaningInTheCanonicalEventType() {
        assertThat(PharmacyEventTypes.canonical("PICKUP", "PICKUP_EXPIRED_RETURN"))
                .isEqualTo("impilo.pharmacy.pickup.expired_return.v1");
        assertThat(PharmacyEventTypes.canonical("DISPENSE", "DISPENSE_CLARIFICATION_REQUESTED"))
                .isEqualTo("impilo.pharmacy.dispense.clarification_requested.v1");
    }

    @Test
    void v11TopicNeverCollidesWithALegacyTopic() {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(sent).extracting(s -> s[0]).doesNotHaveDuplicates();
        assertThat("impilo.pharmacy.dispense")
                .isNotEqualTo("pharmacy.dispense.complete")
                .isNotEqualTo("core.transaction.events");
    }

    @Test
    void legacyOnlyModeLeavesTheEstateExactlyAsItWas() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");

        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(sent).extracting(s -> s[0])
                .containsExactlyInAnyOrder("pharmacy.dispense.complete", "core.transaction.events");
    }

    @Test
    void thePartitionKeyStaysTheAggregateId() {
        List<String[]> sent = drain(publisher(true), row("DISPENSE", "DISPENSE_COMPLETED"));

        assertThat(sent).allSatisfy(s -> assertThat(s[1]).isEqualTo("RX-9001"));
    }
}
