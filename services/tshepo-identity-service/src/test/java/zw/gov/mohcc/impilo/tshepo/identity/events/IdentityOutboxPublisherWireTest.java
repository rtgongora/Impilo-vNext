package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What tshepo-identity actually puts on Kafka after the CompanionOutboxPublisher conversion.
 *
 * <p>Identity's outbox had no tenant column at all, so this is the service where the v1.1
 * envelope had the least to build from. The tenant now comes from the caller — every one of
 * the eleven call sites already had it in scope — and the legacy topic keeps carrying the
 * raw payload it always did.</p>
 */
class IdentityOutboxPublisherWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String LEGACY_TOPIC = "platform.identity.events";
    private static final String PAYLOAD =
            "{\"tenantId\":\"00000000-0000-4000-8000-000000000001\",\"cpid\":\"CPID-1\"}";

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

    private IdentityOutboxPublisher publisher() {
        return new IdentityOutboxPublisher(repository, kafka, LEGACY_TOPIC, null);
    }

    private EventOutboxEntity row(String aggregateType, String eventType) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setId(1L);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId("CPID-1");
        entity.setEventType(eventType);
        entity.setPayload(PAYLOAD);
        entity.setTenantId(TENANT);
        entity.setSubjectType(aggregateType);
        entity.setSubjectId("CPID-1");
        entity.setOccurredAt(Instant.parse("2026-08-07T09:00:00Z"));
        entity.setIdempotencyKey("idem-1");
        return entity;
    }

    private List<String[]> drain(EventOutboxEntity entity) {
        when(repository.findUnpublished()).thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        publisher().publishPendingEvents();

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
    void theLegacyTopicKeepsCarryingTheRawPayload() {
        List<String[]> sent = drain(row("IdMapping", "MAPPING_CREATED"));

        assertThat(bodyOn(sent, LEGACY_TOPIC)).isEqualTo(PAYLOAD);
    }

    @Test
    void v11TopicCarriesACanonicalEventTypeAndRealFederationContext() throws Exception {
        List<String[]> sent = drain(row("IdMapping", "MAPPING_CREATED"));

        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.identity.idmapping"));
        // "id_mapping.mapping_created" is redundant to read but faithful: the stored type is
        // MAPPING_CREATED and the aggregate is IdMapping, so no prefix actually matches.
        // Trimming it to "created" would be guesswork about which words are the noun.
        assertThat(envelope.path("event_type").asText())
                .isEqualTo("impilo.identity.id_mapping.mapping_created.v1");
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
        assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(PAYLOAD));
    }

    @Test
    void aCompoundActionKeepsItsMeaningInTheCanonicalEventType() {
        assertThat(IdentityEventTypes.canonical("ScopedToken", "WORK_CONTEXT_TOKEN_ISSUED"))
                .isEqualTo("impilo.identity.scoped_token.work_context_token_issued.v1");
        assertThat(IdentityEventTypes.canonical("ScopedToken", "TOKEN_REVOKED"))
                .isEqualTo("impilo.identity.scoped_token.token_revoked.v1");
        assertThat(IdentityEventTypes.canonical("ProvisionalCpid", "OCPID_CREATED"))
                .isEqualTo("impilo.identity.provisional_cpid.ocpid_created.v1");
    }

    @Test
    void v11TopicNeverCollidesWithTheLegacyTopic() {
        List<String[]> sent = drain(row("IdMapping", "MAPPING_CREATED"));

        assertThat(sent).extracting(s -> s[0]).doesNotHaveDuplicates();
        assertThat("impilo.identity.idmapping").isNotEqualTo(LEGACY_TOPIC);
    }

    @Test
    void legacyOnlyModeLeavesTheEstateExactlyAsItWas() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");

        List<String[]> sent = drain(row("IdMapping", "MAPPING_CREATED"));

        assertThat(sent).extracting(s -> s[0]).containsExactly(LEGACY_TOPIC);
    }

    @Test
    void aRowWithoutATenantIsRefusedRatherThanPublishedWithAPlaceholder() {
        EventOutboxEntity entity = row("IdMapping", "MAPPING_CREATED");
        entity.setTenantId(null);
        System.setProperty("EMIT_MODE", "V1_1_ONLY");

        when(repository.findUnpublished()).thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThat(publisher().publishPendingEvents()).isZero();
        assertThat(entity.getPublishedAt())
                .as("a refused row must not be recorded as published")
                .isNull();
    }

    @Test
    void thePartitionKeyStaysTheAggregateId() {
        List<String[]> sent = drain(row("ScopedToken", "TOKEN_ISSUED"));

        assertThat(sent).allSatisfy(s -> assertThat(s[1]).isEqualTo("CPID-1"));
    }
}
