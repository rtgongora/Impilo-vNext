package zw.gov.mohcc.impilo.clinical.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalEventOutboxEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalEventOutboxRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave 1 item 1 (handover.md &sect;5): proves the CKP Kafka relay does not just flip on but really
 * moves a row.
 *
 * <p>{@code impilo.clinical.kafka.relay-enabled} defaulted to {@code false} in every environment, so
 * every {@code DetectedIssue} the &sect;9 multimorbidity engine produced sat in
 * {@code clinical.event_outbox} and reached nobody — {@link ClinicalKafkaConfig} and
 * {@link ClinicalKafkaOutboxRelay} are both {@code @ConditionalOnProperty} on that flag, so with it
 * off neither bean is even constructed. Turning the property on in a deploy value file is not itself
 * evidence that anything moves; this test is that evidence, against a real embedded Kafka broker
 * rather than a mock.</p>
 *
 * <p>It proves, in one pass through the production code path:</p>
 * <ol>
 *   <li>a real HTTP call to {@code POST /internal/v1/clinical/multimorbidity/assess} with two
 *       anticoagulants and a subject CPID writes a real {@code clinical.event_outbox} row (the
 *       existing {@code MultimorbidityShrPublisher} path — not re-tested here, only relied upon);</li>
 *   <li>the real {@link ClinicalKafkaOutboxRelay}, which only exists because the property is on,
 *       drains that row onto a real broker, on the exact topic {@link ClinicalKafkaTopics} routes
 *       {@code butano-service}'s {@code ButanoEventConsumer.consumeMultimorbidityIssue} to listen on
 *       — not the {@code impilo.clinical.events} catch-all a missing routing-map entry would silently
 *       fall to;</li>
 *   <li>the message on the wire carries the exact field names ({@code subjectCpid}, {@code code},
 *       {@code severity}) that consumer reads, so a producer/consumer field-name mismatch — the other
 *       ordinary way an outbox spine reaches nobody — would fail this test too;</li>
 *   <li>the row is marked {@code published_at}, so a second relay pass never re-sends it.</li>
 * </ol>
 *
 * <p><strong>What this test cannot prove from inside CKP's own module</strong> is that a live BUTANO
 * pod turns the message into a FHIR {@code DetectedIssue} resource — that boundary belongs to
 * {@code butano-service}'s own {@code DetectedIssueShrBoundaryTest}, which asserts
 * {@code buildDetectedIssue} against exactly this payload shape (same field names, same
 * {@code MM_COMBO_DUAL_ANTICOAGULANT}-style codes). Between the two, the full path from HTTP request
 * to archived FHIR resource is exercised except for one hop: a running BUTANO consuming a running
 * Kafka inside a deployed environment, which needs the preview deploy this change deliberately does
 * not perform (held for explicit authorization).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = "impilo.clinical.multimorbidity.issue.detected")
@TestPropertySource(properties = {
        "impilo.clinical.kafka.relay-enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class ClinicalKafkaOutboxRelayIT {

    private static final String TOPIC = "impilo.clinical.multimorbidity.issue.detected";
    private static final String SUBJECT_CPID = "CPID-IT-KAFKA-RELAY-1";
    private static final String EXPECTED_CODE = "MM_COMBO_DUAL_ANTICOAGULANT";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired
    private ClinicalKafkaOutboxRelay relay;
    @Autowired
    private ClinicalEventOutboxRepository outboxRepository;

    @Test
    void aDetectedIssueOutboxRowIsRelayedToTheExactTopicButanoConsumesWithTheFieldNamesItReads()
            throws Exception {
        Consumer<String, String> consumer = subscribedConsumer();
        try {
            mockMvc.perform(post("/internal/v1/clinical/multimorbidity/assess")
                            .header("X-Tenant-ID", "moh-zw-it")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectCpid\":\"" + SUBJECT_CPID + "\","
                                    + "\"medicationCodes\":[\"warfarin\",\"rivaroxaban\"]}"))
                    .andExpect(status().isOk());

            String expectedSubjectId = SUBJECT_CPID + "|" + EXPECTED_CODE;
            assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                    .as("the assess call must have written the MULTIMORBIDITY_ISSUE_DETECTED row "
                            + "before it can be relayed — if this fails, the fault is upstream of the "
                            + "relay this test targets")
                    .anySatisfy(row -> assertThat(row.getSubjectId()).isEqualTo(expectedSubjectId));

            // Called directly rather than waited for on its @Scheduled cadence: this test asserts what
            // the relay does when it runs, not how promptly the scheduler runs it.
            relay.publishPending();

            ConsumerRecord<String, String> record = findRecordWithCode(consumer, EXPECTED_CODE);

            JsonNode payload = MAPPER.readTree(record.value());
            assertThat(payload.path("subjectCpid").asText())
                    .as("ButanoEventConsumer reads subject_cpid/subjectCpid/cpid — this is the field "
                            + "name it actually looks for")
                    .isEqualTo(SUBJECT_CPID);
            assertThat(payload.path("code").asText()).isEqualTo(EXPECTED_CODE);
            assertThat(payload.hasNonNull("severity"))
                    .as("severity must be present so BUTANO does not have to default it")
                    .isTrue();
            assertThat(payload.has("message"))
                    .as("the engine's interpolated prose must never cross onto the wire")
                    .isFalse();

            ClinicalEventOutboxEntity relayed = outboxRepository.findAll().stream()
                    .filter(row -> expectedSubjectId.equals(row.getSubjectId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("relayed row vanished from the outbox table"));
            assertThat(relayed.getPublishedAt())
                    .as("a relayed row must be marked published_at so a second relay pass never "
                            + "re-sends the same issue")
                    .isNotNull();
        } finally {
            consumer.close();
        }
    }

    private static ConsumerRecord<String, String> findRecordWithCode(Consumer<String, String> consumer,
                                                                      String code) {
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        List<ConsumerRecord<String, String>> onTopic = new ArrayList<>();
        records.records(TOPIC).forEach(onTopic::add);
        return onTopic.stream()
                .filter(r -> r.value() != null && r.value().contains(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no message on " + TOPIC + " carried code " + code + "; received: " + onTopic));
    }

    private Consumer<String, String> subscribedConsumer() {
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("ckp-relay-it", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
        return consumer;
    }
}
