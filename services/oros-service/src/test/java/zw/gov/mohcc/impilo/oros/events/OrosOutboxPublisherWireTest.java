package zw.gov.mohcc.impilo.oros.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What oros actually puts on Kafka after the CompanionOutboxPublisher conversion.
 *
 * <p>oros is the first converted service that fans one payload to <em>three</em> legacy
 * topics — the routed topic, a canonical companion topic for results, and the
 * core-transaction stream. The base class routes a single legacy topic, so the risk this
 * test exists to catch is a conversion that silently drops two of the three and
 * unsubscribes their consumers without any error.</p>
 */
class OrosOutboxPublisherWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PAYLOAD =
            "{\"orderId\":\"ORD-1\",\"tenantId\":\"00000000-0000-4000-8000-000000000001\",\"flag\":\"CRITICAL\"}";

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

    private OutboxPublisher publisher(boolean canonicalEnabled, boolean coreTransactionEnabled) {
        return new OutboxPublisher(repository, kafka, canonicalEnabled, coreTransactionEnabled,
                "core.transaction.events", "clinical.oros.result.available", null);
    }

    private EventOutboxEntity row(String aggregateType, String eventType) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setId(1L);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId("ORD-1");
        entity.setEventType(eventType);
        entity.setPayload(PAYLOAD);
        entity.setTenantId(TENANT);
        entity.setSubjectType(aggregateType);
        entity.setSubjectId("ORD-1");
        entity.setOccurredAt(java.time.OffsetDateTime.parse("2026-08-07T09:00:00Z"));
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

    @Test
    void allThreeLegacyTopicsStillReceiveTheRawPayload() {
        List<String[]> sent = drain(publisher(true, true), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).extracting(s -> s[0]).contains(
                "oros.result.available",          // routed
                "clinical.oros.result.available", // canonical companion
                "core.transaction.events");       // core transaction stream

        for (String topic : List.of("oros.result.available", "clinical.oros.result.available",
                "core.transaction.events")) {
            assertThat(bodyOn(sent, topic))
                    .as("%s must keep receiving the domain payload verbatim", topic)
                    .isEqualTo(PAYLOAD);
        }
    }

    @Test
    void theCanonicalCompanionTopicStaysOffWhenDisabled() {
        List<String[]> sent = drain(publisher(false, true), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).extracting(s -> s[0])
                .doesNotContain("clinical.oros.result.available")
                .contains("oros.result.available", "core.transaction.events");
    }

    @Test
    void theCoreTransactionStreamStaysOffWhenDisabled() {
        List<String[]> sent = drain(publisher(true, false), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).extracting(s -> s[0]).doesNotContain("core.transaction.events");
    }

    @Test
    void anEventOutsideTheCoreTransactionSetDoesNotReachThatStream() {
        List<String[]> sent = drain(publisher(true, true), row("WORKSTEP", "WORKSTEP_STARTED"));

        assertThat(sent).extracting(s -> s[0])
                .doesNotContain("core.transaction.events")
                .contains("oros.workstep.changed");
    }

    @Test
    void v11TopicCarriesACanonicalEventTypeAndRealFederationContext() throws Exception {
        List<String[]> sent = drain(publisher(true, true), row("RESULT", "RESULT_CRITICAL"));

        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.oros.result"));
        // The stored "RESULT_CRITICAL" is not a valid v1.1 event type; passing it through
        // unchanged would emit an envelope failing the event-type contract.
        assertThat(envelope.path("event_type").asText()).isEqualTo("impilo.oros.result.critical.v1");
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
        assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(PAYLOAD));
    }

    @Test
    void aCompoundActionKeepsItsMeaningInTheCanonicalEventType() {
        // ORDER_ACKNOWLEDGED_BY_LAB must not be truncated to its last segment ("lab").
        assertThat(OrosEventTypes.canonical("ORDER", "ORDER_ACKNOWLEDGED_BY_LAB"))
                .isEqualTo("impilo.oros.order.acknowledged_by_lab.v1");
        assertThat(OrosEventTypes.canonical("ORDER", "ORDER_PLACED"))
                .isEqualTo("impilo.oros.order.placed.v1");
        assertThat(OrosEventTypes.canonical("SPECIMEN", "SPECIMEN_COLLECTED"))
                .isEqualTo("impilo.oros.specimen.collected.v1");
    }

    @Test
    void v11TopicNeverCollidesWithAnyLegacyTopic() {
        List<String[]> sent = drain(publisher(true, true), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).extracting(s -> s[0]).doesNotHaveDuplicates();
        assertThat("impilo.oros.result")
                .isNotEqualTo("oros.result.available")
                .isNotEqualTo("clinical.oros.result.available")
                .isNotEqualTo("core.transaction.events");
    }

    @Test
    void legacyOnlyModeLeavesTheEstateExactlyAsItWas() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");

        List<String[]> sent = drain(publisher(true, true), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).extracting(s -> s[0])
                .containsExactlyInAnyOrder("oros.result.available",
                        "clinical.oros.result.available", "core.transaction.events");
    }

    @Test
    void thePartitionKeyStaysTheAggregateId() {
        List<String[]> sent = drain(publisher(true, true), row("RESULT", "RESULT_AVAILABLE"));

        assertThat(sent).allSatisfy(s -> assertThat(s[1]).isEqualTo("ORD-1"));
    }
}
