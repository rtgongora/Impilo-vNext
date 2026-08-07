package zw.gov.mohcc.impilo.referral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.referral.core.ReferralOutboxPublisher;
import zw.gov.mohcc.impilo.referral.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.EventOutboxRepository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The round trip through a real broker: an outbox row goes in, real bytes come back off
 * both topics, and the consumer's read still resolves.
 *
 * <p>Mocked Kafka proves what the publisher <em>intended</em> to send. This proves what a
 * broker actually accepted and handed back — serialization, partition key, topic naming and
 * all. Enabled only when {@code KAFKA_BOOTSTRAP} names a reachable broker, so it never turns
 * CI red on a machine without one:</p>
 *
 * <pre>
 * KAFKA_BOOTSTRAP=localhost:19092 mvn -pl referral-service test -Dtest=ReferralOutboxKafkaRoundTripTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP", matches = ".+")
class ReferralOutboxKafkaRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SURGICAL_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID REFERRAL_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");

    private static final String LEGACY_TOPIC = "referral.surgical.accepted";
    private static final String V11_TOPIC = "impilo.referral.surgicalreferral";

    private String bootstrap;
    private String savedEmitMode;
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        bootstrap = System.getenv("KAFKA_BOOTSTRAP");
        savedEmitMode = System.getProperty("EMIT_MODE");
        System.setProperty("EMIT_MODE", "DUAL");

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @AfterEach
    void tearDown() {
        if (savedEmitMode == null) {
            System.clearProperty("EMIT_MODE");
        } else {
            System.setProperty("EMIT_MODE", savedEmitMode);
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOutboxRowReachesBothTopicsAndStillParses() throws Exception {
        String payload = """
                {"tenantId":"00000000-0000-4000-8000-000000000001",\
                "surgicalReferralId":"aaaaaaaa-0000-4000-8000-00000000000a",\
                "referralId":"bbbbbbbb-0000-4000-8000-00000000000b",\
                "patientId":"P-1","clinicalPriority":"P2","decision":"ACCEPTED"}""";

        EventOutboxEntity entity = EventOutboxEntity.create(
                "SurgicalReferral", SURGICAL_ID.toString(), LEGACY_TOPIC,
                TENANT, "PATIENT", "P-1", payload);
        entity.setId(1L);

        EventOutboxRepository repository = mock(EventOutboxRepository.class);
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(entity), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(kafkaTemplate);

        // Subscribe before publishing so nothing is missed.
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(LEGACY_TOPIC, V11_TOPIC));
            consumer.poll(Duration.ofSeconds(2));
            consumer.seekToBeginning(consumer.assignment());

            ReferralOutboxPublisher publisher = new ReferralOutboxPublisher(repository, provider, null);
            assertThat(publisher.publishPendingEvents()).isEqualTo(1);
            kafkaTemplate.flush();

            Map<String, String> received = drain(consumer);

            assertThat(received).containsKeys(LEGACY_TOPIC, V11_TOPIC);

            // Legacy topic: the exact domain payload, no wrapper.
            assertThat(received.get(LEGACY_TOPIC)).isEqualTo(payload);

            // v1.1 topic: a full envelope carrying real federation context.
            JsonNode envelope = MAPPER.readTree(received.get(V11_TOPIC));
            assertThat(envelope.path("event_type").asText())
                    .isEqualTo("impilo.referral.surgical_referral.accepted.v1");
            assertThat(envelope.path("tenant_id").asText()).isEqualTo(TENANT.toString());
            assertThat(envelope.path("pod_id").asText()).isEqualTo("national");
            assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(payload));

            // The consumer's read — payload when present, root otherwise — resolves on both.
            for (String topic : List.of(LEGACY_TOPIC, V11_TOPIC)) {
                JsonNode root = MAPPER.readTree(received.get(topic));
                JsonNode body = root.has("payload") && !root.get("payload").isNull()
                        ? root.get("payload") : root;
                assertThat(body.path("tenantId").asText()).isEqualTo(TENANT.toString());
                assertThat(body.path("referralId").asText()).isEqualTo(REFERRAL_ID.toString());
                assertThat(body.path("patientId").asText()).isEqualTo("P-1");
            }
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "referral-roundtrip-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private Map<String, String> drain(KafkaConsumer<String, String> consumer) {
        Map<String, String> byTopic = new HashMap<>();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && byTopic.size() < 2) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                byTopic.putIfAbsent(record.topic(), record.value());
                assertThat(record.key())
                        .as("partition key must stay the aggregate id for per-subject ordering")
                        .isEqualTo(SURGICAL_ID.toString());
            }
        }
        return byTopic;
    }
}
