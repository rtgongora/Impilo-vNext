package zw.gov.mohcc.impilo.referral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.referral.core.ReferralOutboxPublisher;
import zw.gov.mohcc.impilo.referral.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What referral actually puts on Kafka after the CompanionOutboxPublisher conversion.
 *
 * <p>The estate-wide envelope shape is pinned in {@code OutboxWireContractTest}; this test
 * pins the referral-specific half that the shared contract cannot see — the legacy topic
 * name, the canonical event type derived for this service, and the fields
 * {@code SchedulingReferralConsumer} reads out of the message.</p>
 */
class ReferralOutboxPublisherWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SURGICAL_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID REFERRAL_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");

    private EventOutboxRepository repository;
    private KafkaTemplate<String, String> kafka;
    private ReferralOutboxPublisher publisher;
    private String savedEmitMode;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        savedEmitMode = System.getProperty("EMIT_MODE");
        System.setProperty("EMIT_MODE", "DUAL");

        repository = mock(EventOutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(kafka);
        publisher = new ReferralOutboxPublisher(repository, provider, null);
    }

    @AfterEach
    void tearDown() {
        if (savedEmitMode == null) {
            System.clearProperty("EMIT_MODE");
        } else {
            System.setProperty("EMIT_MODE", savedEmitMode);
        }
    }

    private EventOutboxEntity surgicalAccepted() {
        String payload = """
                {"tenantId":"00000000-0000-4000-8000-000000000001",\
                "surgicalReferralId":"aaaaaaaa-0000-4000-8000-00000000000a",\
                "referralId":"bbbbbbbb-0000-4000-8000-00000000000b",\
                "patientId":"P-1","intendedProcedureZiboCode":"ZIBO-CHOLE",\
                "clinicalPriority":"P2","decision":"ACCEPTED"}""";
        EventOutboxEntity entity = EventOutboxEntity.create(
                "SurgicalReferral", SURGICAL_ID.toString(), "referral.surgical.accepted",
                TENANT, "PATIENT", "P-1", payload);
        entity.setId(1L);
        return entity;
    }

    private List<String[]> drain(EventOutboxEntity entity) {
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        publisher.publishPendingEvents();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka, org.mockito.Mockito.atLeastOnce())
                .send(topic.capture(), key.capture(), value.capture());

        List<String[]> sent = new java.util.ArrayList<>();
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
    void legacyTopicKeepsItsNameAndItsRawPayload() throws Exception {
        List<String[]> sent = drain(surgicalAccepted());

        // The legacy topic has always been the event type itself. Renaming it would
        // silently orphan scheduling-service's listener.
        String body = bodyOn(sent, "referral.surgical.accepted");
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.path("referralId").asText()).isEqualTo(REFERRAL_ID.toString());
        assertThat(root.path("tenantId").asText()).isEqualTo(TENANT.toString());
        assertThat(root.has("payload"))
                .as("legacy body must be the domain payload itself, not a wrapper")
                .isFalse();
    }

    @Test
    void v11TopicCarriesACanonicalEventType() throws Exception {
        List<String[]> sent = drain(surgicalAccepted());

        JsonNode envelope = MAPPER.readTree(bodyOn(sent, "impilo.referral.surgicalreferral"));
        // The stored event type "referral.surgical.accepted" is not a valid v1.1 event type.
        // Feeding it through unchanged would emit an envelope that fails the event-type contract.
        assertThat(envelope.path("event_type").asText())
                .isEqualTo("impilo.referral.surgical_referral.accepted.v1");
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
        assertThat(envelope.path("subject_type").asText()).isEqualTo("PATIENT");
        assertThat(envelope.path("subject_id").asText()).isEqualTo("P-1");
    }

    /**
     * SchedulingReferralConsumer unwraps {@code payload} when present and falls back to the
     * root when it is not. Both shapes must therefore yield the same three required fields —
     * this is the property that lets the consumer survive the conversion untouched.
     */
    @Test
    void schedulingConsumerReadsBothShapes() throws Exception {
        List<String[]> sent = drain(surgicalAccepted());

        for (String topic : List.of("referral.surgical.accepted", "impilo.referral.surgicalreferral")) {
            JsonNode root = MAPPER.readTree(bodyOn(sent, topic));
            JsonNode payload = root.has("payload") && !root.get("payload").isNull()
                    ? root.get("payload")
                    : root;
            assertThat(payload.path("tenantId").asText())
                    .as("tenantId unreadable on %s", topic).isEqualTo(TENANT.toString());
            assertThat(payload.path("referralId").asText())
                    .as("referralId unreadable on %s", topic).isEqualTo(REFERRAL_ID.toString());
            assertThat(payload.path("patientId").asText())
                    .as("patientId unreadable on %s", topic).isEqualTo("P-1");
        }
    }

    @Test
    void internalLifecycleEventsStayOffKafkaButStillGetAnEnvelope() {
        EventOutboxEntity entity = EventOutboxEntity.create(
                "Referral", REFERRAL_ID.toString(), "REFERRAL_CREATED",
                TENANT, "PATIENT", "P-1", "{\"status\":\"PENDING\"}");
        entity.setId(1L);

        List<String[]> sent = drain(entity);

        // UPPER_SNAKE lifecycle events were internal before the conversion and remain so:
        // no legacy topic is resolved for them, so no new legacy traffic appears.
        assertThat(sent).extracting(s -> s[0]).containsExactly("impilo.referral.referral");
    }

    @Test
    void aRowWithoutATenantIsRefusedAtAppendRatherThanPublishedWithAPlaceholder() {
        assertThatThrownBy(() -> publisher.append(
                "Referral", REFERRAL_ID.toString(), "REFERRAL_CREATED",
                null, "PATIENT", "P-1", Map.of("status", "PENDING")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a tenant");
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void anAbsentBrokerDoesNotMarkTheRowPublished() {
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaTemplate<String, String>> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        ReferralOutboxPublisher brokerless = new ReferralOutboxPublisher(repository, empty, null);

        EventOutboxEntity entity = surgicalAccepted();
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(entity));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThat(brokerless.publishPendingEvents()).isZero();
        assertThat(entity.getPublishedAt())
                .as("marking a row published when nothing was sent loses the event silently")
                .isNull();
    }

    @Test
    void appendStoresTheDomainPayloadNotAnEnvelope() {
        publisher.append("SurgicalReferral", SURGICAL_ID.toString(), "referral.surgical.accepted",
                TENANT, "PATIENT", "P-1", Map.of("referralId", REFERRAL_ID.toString()));

        ArgumentCaptor<EventOutboxEntity> saved = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPayloadJson())
                .isEqualTo("{\"referralId\":\"" + REFERRAL_ID + "\"}");
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getValue().getPodId()).isEqualTo("national");
        assertThat(saved.getValue().getIdempotencyKey()).isNotBlank();
    }

    @Test
    void idempotencyKeysAreDistinctPerEvent() {
        publisher.append("Referral", REFERRAL_ID.toString(), "REFERRAL_CREATED",
                TENANT, "PATIENT", "P-1", Map.of());
        publisher.append("Referral", REFERRAL_ID.toString(), "REFERRAL_ACCEPTED",
                TENANT, "PATIENT", "P-1", Map.of());

        ArgumentCaptor<EventOutboxEntity> saved = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getIdempotencyKey())
                .isNotEqualTo(saved.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyOnlyModeLeavesTheEstateExactlyAsItWas() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(kafka);
        ReferralOutboxPublisher legacyOnly = new ReferralOutboxPublisher(repository, provider, null);

        EventOutboxEntity entity = surgicalAccepted();
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        legacyOnly.publishPendingEvents();

        verify(kafka).send(anyString(), anyString(), anyString());
        verify(kafka).send(org.mockito.ArgumentMatchers.eq("referral.surgical.accepted"),
                anyString(), anyString());
    }
}
