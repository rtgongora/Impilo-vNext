package zw.gov.mohcc.impilo.contract.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.gov.mohcc.impilo.contract.envelope.EnvelopeValidationResult;
import zw.gov.mohcc.impilo.contract.envelope.EventEnvelopeValidator;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EmitMode;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Kafka wire contract for outbox publishing.
 *
 * <p>Nothing else in the estate protects this. The {@code *GoldenContractTest} suites validate
 * REST envelopes through a synthetic probe controller and never touch the outbox, and
 * {@code contracts/asyncapi/*} documents the raw payload as it is today — so it describes the
 * current shape rather than constraining the new one.</p>
 *
 * <p>Converting a service to {@link CompanionOutboxPublisher} changes what lands on Kafka. Two
 * distinct guarantees have to hold simultaneously, and they pull in opposite directions:</p>
 *
 * <ol>
 *   <li><b>Legacy consumers must not notice.</b> Consumers such as
 *       {@code InventoryEventConsumer.handleDispenseComplete} read {@code tenantId} at the JSON
 *       <em>top level</em>. If the legacy topic ever starts carrying an envelope, that read
 *       returns null and the handler logs "missing tenant/facility/store/item, skipping" —
 *       it does not throw. Stock silently stops being deducted. That failure mode is why the
 *       legacy body is asserted byte-for-byte here rather than merely "parses".</li>
 *   <li><b>The v1.1 topic must carry a well-formed envelope</b> — all 15 required fields, a
 *       canonical event type, and no fabricated tenant/pod.</li>
 * </ol>
 *
 * <p>The two are kept apart by topic namespace: legacy topics are service-chosen
 * ({@code pharmacy.dispense.complete}), v1.1 topics are always {@code impilo.{service}.{domain}}.
 * A collision would deliver envelopes to a legacy consumer, so it is asserted explicitly.</p>
 */
@DisplayName("Outbox Kafka wire contract")
class OutboxWireContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMIT_MODE_PROPERTY = "EMIT_MODE";

    /** The exact payload a producing service stores in event_outbox.payload_json. */
    private static final String STORED_PAYLOAD = """
            {"tenantId":"11111111-1111-1111-1111-111111111111",\
            "facilityId":"22222222-2222-2222-2222-222222222222",\
            "storeId":"33333333-3333-3333-3333-333333333333",\
            "itemCode":"PARA-500","qty":12,"orderId":"RX-9001"}""";

    private String savedEmitMode;

    @BeforeEach
    void captureEmitMode() {
        savedEmitMode = System.getProperty(EMIT_MODE_PROPERTY);
    }

    @AfterEach
    void restoreEmitMode() {
        if (savedEmitMode == null) {
            System.clearProperty(EMIT_MODE_PROPERTY);
        } else {
            System.setProperty(EMIT_MODE_PROPERTY, savedEmitMode);
        }
    }

    // ─────────────────────────── 1. legacy consumers must not notice ───────────────────────────

    @Test
    @DisplayName("legacy topic carries the stored payload byte-for-byte, never an envelope")
    void legacyTopicCarriesStoredPayloadUnchanged() {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        Sent legacy = publisher.onTopic("pharmacy.dispense.complete");
        assertThat(legacy.value())
                .as("legacy body must be the stored payload verbatim — a consumer reading "
                        + "top-level fields breaks silently on any re-wrapping")
                .isEqualTo(STORED_PAYLOAD);
    }

    @Test
    @DisplayName("canary: InventoryEventConsumer's top-level reads still resolve on the legacy topic")
    void canaryTopLevelReadsStillResolve() throws Exception {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        JsonNode event = MAPPER.readTree(publisher.onTopic("pharmacy.dispense.complete").value());
        // Mirrors InventoryEventConsumer.text(event, "tenantId", "tenant_id") and friends.
        assertThat(text(event, "tenantId", "tenant_id")).isNotNull();
        assertThat(text(event, "facilityId", "facility_id")).isNotNull();
        assertThat(text(event, "storeId", "store_id")).isNotNull();
        assertThat(text(event, "itemCode", "item_code")).isNotNull();
        assertThat(event.path("qty").asInt()).isPositive();
    }

    @Test
    @DisplayName("an envelope on the legacy topic would fail the canary — the guard is not vacuous")
    void canaryWouldFailIfLegacyTopicCarriedAnEnvelope() throws Exception {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);
        publisher.publishPendingEvents();

        // Negative control: the v1.1 body is what the legacy topic must never carry.
        JsonNode envelope = MAPPER.readTree(publisher.onTopic(v11Topic()).value());

        assertThat(text(envelope, "tenantId", "tenant_id"))
                .as("envelope does carry tenant_id, so the canary needs a sharper probe")
                .isNotNull();
        assertThat(text(envelope, "facilityId", "facility_id"))
                .as("domain fields live under payload in an envelope — this is exactly the "
                        + "read that returns null and makes the consumer skip in silence")
                .isNull();
        assertThat(envelope.path("qty").asInt()).isZero();
    }

    // ─────────────────────────── 2. the v1.1 envelope is well formed ───────────────────────────

    @Test
    @DisplayName("v1.1 topic carries an envelope satisfying all 15 required fields")
    void v11TopicCarriesValidEnvelope() {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        String envelopeJson = publisher.onTopic(v11Topic()).value();
        EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelopeJson);
        assertThat(result.valid())
                .as("envelope violations: %s", result.violations())
                .isTrue();
    }

    @Test
    @DisplayName("the domain payload survives intact under the envelope's payload key")
    void envelopePreservesDomainPayload() throws Exception {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        JsonNode envelope = MAPPER.readTree(publisher.onTopic(v11Topic()).value());
        assertThat(envelope.path("payload")).isEqualTo(MAPPER.readTree(STORED_PAYLOAD));
    }

    @Test
    @DisplayName("tenant_id and pod_id are carried through, never fabricated")
    void envelopeCarriesRealTenantAndPod() throws Exception {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        JsonNode envelope = MAPPER.readTree(publisher.onTopic(v11Topic()).value());
        assertThat(envelope.path("tenant_id").asText()).isEqualTo(RecordingRow.TENANT_ID);
        assertThat(envelope.path("pod_id").asText())
                .as("a fabricated 'unknown' here is the §1.3(7) pattern the architecture forbids")
                .isEqualTo(RecordingRow.POD_ID)
                .isNotEqualTo("unknown");
    }

    /**
     * The red-proof. Each required field is removed from the publisher's <em>actual</em> output —
     * not from a hand-written sample — and the validator must reject it. Without this the suite
     * could pass against a validator that checks nothing.
     */
    @ParameterizedTest(name = "dropping {0} turns the contract RED")
    @ValueSource(strings = {
            "event_id", "event_type", "schema_version", "correlation_id", "causation_id",
            "idempotency_key", "producer", "tenant_id", "pod_id", "occurred_at",
            "emitted_at", "subject_type", "subject_id", "payload", "meta"
    })
    @DisplayName("removing any required field fails the contract")
    void removingAnyRequiredFieldFailsTheContract(String field) throws Exception {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);
        publisher.publishPendingEvents();

        ObjectNode envelope = (ObjectNode) MAPPER.readTree(publisher.onTopic(v11Topic()).value());
        assertThat(envelope.has(field))
                .as("publisher never emitted '%s' — the field list and the wire format have drifted", field)
                .isTrue();
        envelope.remove(field);

        EnvelopeValidationResult result = EventEnvelopeValidator.validate(envelope);
        assertThat(result.valid())
                .as("contract stayed green without '%s'", field)
                .isFalse();
        assertThat(result.violations())
                .anyMatch(v -> v.contains(field));
    }

    // ─────────────────────── 2b. absent context refuses, never invents ───────────────────────

    @Test
    @DisplayName("a row with no tenant refuses to build rather than publishing 'unknown'")
    void missingTenantRefusesToBuild() {
        RecordingPublisher publisher = publisherIn(EmitMode.V1_1_ONLY);
        publisher.rows.set(0, new RecordingRow() {
            @Override public String tenantId() { return null; }
        });

        publisher.publishPendingEvents();

        assertThat(publisher.sent())
                .as("a fabricated tenant_id is indistinguishable on the wire from a real one")
                .isEmpty();
    }

    @Test
    @DisplayName("a row with no pod refuses to build rather than publishing 'unknown'")
    void missingPodRefusesToBuild() {
        RecordingPublisher publisher = publisherIn(EmitMode.V1_1_ONLY);
        publisher.rows.set(0, new RecordingRow() {
            @Override public String podId() { return null; }
        });

        publisher.publishPendingEvents();

        assertThat(publisher.sent()).isEmpty();
    }

    @Test
    @DisplayName("refusing a v1.1 envelope does not silently mark the row published")
    void refusedRowIsNotMarkedPublished() {
        RecordingPublisher publisher = publisherIn(EmitMode.V1_1_ONLY);
        publisher.rows.set(0, new RecordingRow() {
            @Override public String tenantId() { return null; }
        });

        assertThat(publisher.publishPendingEvents())
                .as("counting a refused row as published loses the event permanently")
                .isZero();
    }

    // ────────────────── 2c. multi-topic legacy fan-out survives conversion ──────────────────

    @Test
    @DisplayName("additional legacy topics still receive the raw payload")
    void additionalLegacyTopicsArePreserved() {
        RecordingPublisher publisher = new RecordingPublisher() {
            @Override
            protected List<String> additionalLegacyTopics(OutboxRow row) {
                // A service fanning one payload to a companion and a core-transaction stream.
                // Arrays.asList, not List.of — the trailing null is deliberate, and List.of
                // rejects nulls outright. A null topic must be skipped, not thrown on.
                return java.util.Arrays.asList("pharmacy.dispense.companion", "core.transaction.events",
                        "pharmacy.dispense.complete", null);
            }
        };
        System.setProperty(EMIT_MODE_PROPERTY, EmitMode.DUAL.name());

        publisher.publishPendingEvents();

        assertThat(publisher.topics()).containsExactlyInAnyOrder(
                "pharmacy.dispense.complete",
                "pharmacy.dispense.companion",
                "core.transaction.events",
                v11Topic());
        assertThat(publisher.onTopic("core.transaction.events").value()).isEqualTo(STORED_PAYLOAD);
        assertThat(publisher.onTopic("pharmacy.dispense.companion").value()).isEqualTo(STORED_PAYLOAD);
    }

    @Test
    @DisplayName("the default fan-out is empty, so converted services are unaffected")
    void additionalLegacyTopicsDefaultToNone() {
        RecordingPublisher publisher = publisherIn(EmitMode.LEGACY_ONLY);

        publisher.publishPendingEvents();

        assertThat(publisher.topics()).containsExactly("pharmacy.dispense.complete");
    }

    // ─────────────────────────── 3. the two namespaces stay apart ───────────────────────────

    @Test
    @DisplayName("v1.1 topic never collides with the legacy topic")
    void v11TopicNeverCollidesWithLegacyTopic() {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        List<String> topics = publisher.topics();
        assertThat(topics).doesNotHaveDuplicates();
        assertThat(v11Topic())
                .as("a collision delivers envelopes to a legacy consumer")
                .isNotEqualTo("pharmacy.dispense.complete")
                .startsWith("impilo.");
    }

    @Test
    @DisplayName("DUAL emits exactly one legacy and one v1.1 message per row")
    void dualEmitsBothExactlyOnce() {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        int published = publisher.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(publisher.sent()).hasSize(2);
    }

    @Test
    @DisplayName("LEGACY_ONLY emits nothing to the v1.1 topic")
    void legacyOnlySuppressesV11() {
        RecordingPublisher publisher = publisherIn(EmitMode.LEGACY_ONLY);

        publisher.publishPendingEvents();

        assertThat(publisher.topics()).containsExactly("pharmacy.dispense.complete");
    }

    @Test
    @DisplayName("V1_1_ONLY stops feeding legacy consumers — the cutover, not the migration")
    void v11OnlySuppressesLegacy() {
        RecordingPublisher publisher = publisherIn(EmitMode.V1_1_ONLY);

        publisher.publishPendingEvents();

        assertThat(publisher.topics()).containsExactly(v11Topic());
    }

    @Test
    @DisplayName("the partition key stays the aggregate id, so ordering per subject is preserved")
    void partitionKeyIsStable() {
        RecordingPublisher publisher = publisherIn(EmitMode.DUAL);

        publisher.publishPendingEvents();

        assertThat(publisher.sent()).allSatisfy(
                s -> assertThat(s.key()).isEqualTo(RecordingRow.AGGREGATE_ID));
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static String v11Topic() {
        return new EventTopicRegistry("pharmacy").v11Topic(RecordingRow.AGGREGATE_TYPE);
    }

    private RecordingPublisher publisherIn(EmitMode mode) {
        System.setProperty(EMIT_MODE_PROPERTY, mode.name());
        return new RecordingPublisher();
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }

    /** Captures what would have reached Kafka. */
    private record Sent(String topic, String key, String value) {}

    /**
     * A minimal but faithful subclass — the same five integration points every converted
     * service implements. Exercising the real base class is the point: a hand-rolled sample
     * envelope would prove nothing about what publishers actually emit.
     */
    private static class RecordingPublisher extends CompanionOutboxPublisher {

        private final List<Sent> sent = new ArrayList<>();
        private final List<RecordingRow> rows = new ArrayList<>(List.of(new RecordingRow()));

        RecordingPublisher() {
            super(new DualEmitPolicy(), new EventTopicRegistry("pharmacy"));
        }

        @Override
        protected List<? extends OutboxRow> fetchUnpublished() {
            return List.copyOf(rows);
        }

        @Override
        protected void sendToKafka(String topic, String key, String value) {
            sent.add(new Sent(topic, key, value));
        }

        @Override
        protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
            rows.clear();
        }

        @Override
        protected void markFailed(OutboxRow row, String errorMessage) {
            rows.clear();
        }

        @Override
        protected String resolveLegacyTopic(OutboxRow row) {
            return "pharmacy.dispense.complete";
        }

        List<Sent> sent() {
            return sent;
        }

        List<String> topics() {
            return sent.stream().map(Sent::topic).toList();
        }

        Sent onTopic(String topic) {
            return sent.stream()
                    .filter(s -> s.topic().equals(topic))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "nothing published to " + topic + "; got " + topics()));
        }
    }

    /** An outbox row as a converted service maps it. */
    private static class RecordingRow implements CompanionOutboxPublisher.OutboxRow {

        static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
        static final String POD_ID = "national";
        static final String AGGREGATE_TYPE = "dispense";
        static final String AGGREGATE_ID = "RX-9001";

        @Override public Long id() { return 1L; }
        @Override public String aggregateType() { return AGGREGATE_TYPE; }
        @Override public String aggregateId() { return AGGREGATE_ID; }
        @Override public String eventType() { return "impilo.pharmacy.dispense.completed.v1"; }
        @Override public String payloadJson() { return STORED_PAYLOAD; }
        @Override public OffsetDateTime occurredAt() { return OffsetDateTime.parse("2026-08-07T09:00:00Z"); }
        @Override public OffsetDateTime publishedAt() { return null; }
        @Override public String tenantId() { return TENANT_ID; }
        @Override public String podId() { return POD_ID; }
        @Override public String correlationId() { return "corr-1"; }
        @Override public String causationId() { return "cause-1"; }
        @Override public String idempotencyKey() { return "idem-1"; }
        @Override public int schemaVersion() { return 1; }
        @Override public String subjectType() { return "PATIENT"; }
        @Override public String subjectId() { return "CPID-1"; }
    }
}
