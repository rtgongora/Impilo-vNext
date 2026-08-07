package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;

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
 * What PCT actually puts on Kafka after the CompanionOutboxPublisher conversion.
 *
 * <p>PCT is the largest and last conversion, and the one with the most ways to go wrong: it
 * fans a single payload to as many as three legacy topics, and its stored event types come in
 * two flavours — UPPER_SNAKE and already-dotted — both of which {@code routeTopic} switches
 * on. Routing on the canonical v1.1 type instead of the stored one would silently drop every
 * event onto the {@code pct.events} catch-all while still reporting success.</p>
 */
@DisplayName("PCT outbox wire contract")
class PctOutboxPublisherWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PAYLOAD =
            "{\"journeyId\":\"J-1\",\"facilityId\":\"FAC-1\",\"newState\":\"IN_CONSULT\"}";

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
                "core.transaction.events", "clinical.pct.journey.completed",
                "clinical.pct.encounter.completed", "clinical.pct.death.recorded", null);
    }

    private EventOutboxEntity row(String aggregateType, String eventType) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setId(1L);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId("J-1");
        entity.setEventType(eventType);
        entity.setPayload(PAYLOAD);
        entity.setTenantId(TENANT);
        entity.setSubjectType(aggregateType);
        entity.setSubjectId("J-1");
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

    @Test
    @DisplayName("all three legacy topics still receive the raw payload")
    void allThreeLegacyTopicsReceiveTheRawPayload() {
        List<String[]> sent = drain(publisher(true, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        for (String topic : List.of("pct.journey.state_changed",
                "clinical.pct.journey.completed", "core.transaction.events")) {
            assertThat(bodyOn(sent, topic))
                    .as("%s must keep receiving the domain payload verbatim", topic)
                    .isEqualTo(PAYLOAD);
        }
    }

    @Test
    @DisplayName("routing keys off the stored event type, not the canonical one")
    void routingUsesTheStoredEventType() {
        List<String[]> sent = drain(publisher(false, false), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        // Had routing used row.eventType() — now impilo.pct.journey.state_changed.v1 — this
        // would have matched no case and fallen onto the catch-all instead.
        assertThat(sent).extracting(s -> s[0])
                .contains("pct.journey.state_changed")
                .doesNotContain("pct.events");
    }

    @Test
    @DisplayName("a dotted legacy event type routes and canonicalises correctly")
    void dottedEventTypeIsHandled() throws Exception {
        List<String[]> sent = drain(publisher(false, false),
                row("OBSERVATION", "pct.observation.recorded"));

        assertThat(sent).extracting(s -> s[0]).contains("pct.observation.recorded");
        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.pct.observation"));
        assertThat(envelope.path("event_type").asText())
                .isEqualTo("impilo.pct.observation.recorded.v1");
    }

    @Test
    @DisplayName("the canonical companion topic stays off when disabled")
    void canonicalCompanionTopicRespectsItsFlag() {
        List<String[]> sent = drain(publisher(false, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        assertThat(sent).extracting(s -> s[0])
                .doesNotContain("clinical.pct.journey.completed")
                .contains("pct.journey.state_changed", "core.transaction.events");
    }

    @Test
    @DisplayName("the core-transaction stream stays off when disabled")
    void coreTransactionStreamRespectsItsFlag() {
        List<String[]> sent = drain(publisher(true, false), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        assertThat(sent).extracting(s -> s[0]).doesNotContain("core.transaction.events");
    }

    @Test
    @DisplayName("an event outside the core-transaction set does not reach that stream")
    void nonCoreTransactionEventStaysOff() {
        List<String[]> sent = drain(publisher(true, true), row("TASK", "TASK_UPDATED"));

        assertThat(sent).extracting(s -> s[0]).doesNotContain("core.transaction.events");
    }

    @Test
    @DisplayName("v1.1 topic carries a canonical event type and real federation context")
    void v11TopicCarriesValidEnvelope() throws Exception {
        List<String[]> sent = drain(publisher(true, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.pct.journey"));
        assertThat(envelope.path("event_type").asText())
                .isEqualTo("impilo.pct.journey.state_changed.v1");
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
        assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(PAYLOAD));
    }

    @Test
    @DisplayName("compound actions keep their meaning in the canonical event type")
    void compoundActionsSurvive() {
        assertThat(PctEventTypes.canonical("QUEUE_ITEM", "QUEUE_ITEM_TRANSFERRED"))
                .isEqualTo("impilo.pct.queue_item.transferred.v1");
        assertThat(PctEventTypes.canonical("ENCOUNTER", "ENCOUNTER_PATHWAY_PROTOCOL_UPDATED"))
                .isEqualTo("impilo.pct.encounter.pathway_protocol_updated.v1");
        assertThat(PctEventTypes.canonical("OBSERVATION", "pct.newborn.episode.opened"))
                .isEqualTo("impilo.pct.newborn.episode.opened.v1");
    }

    @Test
    @DisplayName("v1.1 topics never collide with any legacy topic")
    void v11TopicNeverCollides() {
        List<String[]> sent = drain(publisher(true, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        assertThat(sent).extracting(s -> s[0]).doesNotHaveDuplicates();
        assertThat("impilo.pct.journey")
                .isNotEqualTo("pct.journey.state_changed")
                .isNotEqualTo("clinical.pct.journey.completed")
                .isNotEqualTo("core.transaction.events");
    }

    @Test
    @DisplayName("LEGACY_ONLY leaves the estate exactly as it was")
    void legacyOnlyModeIsUnchanged() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");

        List<String[]> sent = drain(publisher(true, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        assertThat(sent).extracting(s -> s[0]).containsExactlyInAnyOrder(
                "pct.journey.state_changed", "clinical.pct.journey.completed",
                "core.transaction.events");
    }

    @Test
    @DisplayName("the partition key stays the aggregate id, preserving per-journey ordering")
    void partitionKeyIsStable() {
        List<String[]> sent = drain(publisher(true, true), row("JOURNEY", "JOURNEY_STATE_CHANGED"));

        assertThat(sent).allSatisfy(s -> assertThat(s[1]).isEqualTo("J-1"));
    }
}
