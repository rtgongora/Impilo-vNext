package zw.gov.mohcc.impilo.learning.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

/**
 * Pure Mockito unit tests for {@link LearningOutboxPublisher}.
 *
 * <p>No Spring context, no Kafka broker, no DB. Verifies:</p>
 * <ul>
 *   <li>topic routing returns the configured default topic for any event type
 *       (Phase 2 is single-topic by approved decision);</li>
 *   <li>successful Kafka publish marks the row {@code publishedAt} and saves it;</li>
 *   <li>a single failing send does not break the batch and leaves the failing
 *       row's {@code publishedAt} unset so the next poll retries it.</li>
 * </ul>
 */
class LearningOutboxPublisherTest {

    private static final String DEFAULT_TOPIC = "platform.learning.events";

    @Test
    @DisplayName("routeTopic returns the configured default topic for every event type (Phase 2 single-topic)")
    void routeTopicReturnsConfiguredDefault() {
        LearningOutboxPublisher publisher = new LearningOutboxPublisher(
                mock(LearningOutboxRepository.class),
                kafkaTemplateMock(),
                DEFAULT_TOPIC,
                100,
                null);

        assertThat(publisher.routeTopic("learning.completion.recorded")).isEqualTo(DEFAULT_TOPIC);
        assertThat(publisher.routeTopic("fundo.sync.completed")).isEqualTo(DEFAULT_TOPIC);
        assertThat(publisher.routeTopic("anything.else")).isEqualTo(DEFAULT_TOPIC);
        assertThat(publisher.routeTopic(null)).isEqualTo(DEFAULT_TOPIC);
        assertThat(publisher.routeTopic("")).isEqualTo(DEFAULT_TOPIC);
    }

    @Test
    @DisplayName("publishPendingEvents does nothing when the outbox has no pending rows")
    void emptyOutboxIsNoOp() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        KafkaTemplate<String, String> kafka = kafkaTemplateMock();

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, null);
        publisher.publishPendingEvents();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Successful Kafka publish marks the row publishedAt and saves it")
    void successfulPublishMarksRowPublished() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        LearningOutboxEntity row = entity("LearningCompletion", "agg-1", "learning.completion.recorded", "{\"ok\":true}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(new ArrayList<>(List.of(row)));

        KafkaTemplate<String, String> kafka = kafkaTemplateMock();

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, null);
        publisher.publishPendingEvents();

        verify(kafka, times(1)).send(eq(DEFAULT_TOPIC), eq("agg-1"), eq("{\"ok\":true}"));
        verify(repo, times(1)).save(row);
        assertThat(row.getPublishedAt())
                .as("publishedAt must be set after a successful Kafka send")
                .isNotNull();
    }

    @Test
    @DisplayName("A single failed send is logged and skipped — other rows still publish")
    void failedSendDoesNotBreakBatch() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        LearningOutboxEntity good = entity("LearningResource", "agg-good", "learning.resource.opened", "{\"a\":1}");
        LearningOutboxEntity bad = entity("Fundo", "agg-bad", "fundo.sync.completed", "{\"b\":2}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(new ArrayList<>(List.of(bad, good)));

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq(DEFAULT_TOPIC), eq("agg-bad"), anyString()))
                .thenThrow(new RuntimeException("simulated broker outage"));
        when(kafka.send(eq(DEFAULT_TOPIC), eq("agg-good"), anyString())).thenReturn(null);

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, null);
        publisher.publishPendingEvents();

        assertThat(bad.getPublishedAt())
                .as("Failed row must NOT be marked published — next poll retries it")
                .isNull();
        assertThat(good.getPublishedAt())
                .as("Successful row must be marked published in the same batch")
                .isNotNull();
        verify(repo, times(1)).save(good);
        verify(repo, never()).save(bad);
    }

    // ── Phase 3A: Micrometer metrics ───────────────────────────────────

    @Test
    @DisplayName("Successful publish increments learning.outbox.published{outcome=success} on the MeterRegistry")
    void successfulPublishIncrementsSuccessCounter() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        LearningOutboxEntity row = entity("LearningCompletion", "agg-1", "learning.completion.recorded", "{\"ok\":true}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(new ArrayList<>(List.of(row)));

        KafkaTemplate<String, String> kafka = kafkaTemplateMock();
        MeterRegistry registry = new SimpleMeterRegistry();

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, registry);
        publisher.publishPendingEvents();

        Counter success = registry.find(LearningOutboxPublisher.METRIC_PUBLISHED)
                .tag(LearningOutboxPublisher.TAG_OUTCOME, LearningOutboxPublisher.OUTCOME_SUCCESS)
                .counter();
        Counter failure = registry.find(LearningOutboxPublisher.METRIC_PUBLISHED)
                .tag(LearningOutboxPublisher.TAG_OUTCOME, LearningOutboxPublisher.OUTCOME_FAILURE)
                .counter();
        assertThat(success).as("success counter must be registered").isNotNull();
        assertThat(failure).as("failure counter must be registered").isNotNull();
        assertThat(success.count()).isEqualTo(1d);
        assertThat(failure.count()).isEqualTo(0d);
    }

    @Test
    @DisplayName("Failed send increments learning.outbox.published{outcome=failure}; success counter only counts successes")
    void failedSendIncrementsFailureCounter() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        LearningOutboxEntity good = entity("LearningResource", "agg-good", "learning.resource.opened", "{\"a\":1}");
        LearningOutboxEntity bad = entity("Fundo", "agg-bad", "fundo.sync.completed", "{\"b\":2}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(new ArrayList<>(List.of(bad, good)));

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq(DEFAULT_TOPIC), eq("agg-bad"), anyString()))
                .thenThrow(new RuntimeException("simulated broker outage"));
        when(kafka.send(eq(DEFAULT_TOPIC), eq("agg-good"), anyString())).thenReturn(null);
        MeterRegistry registry = new SimpleMeterRegistry();

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, registry);
        publisher.publishPendingEvents();

        double successes = registry.find(LearningOutboxPublisher.METRIC_PUBLISHED)
                .tag(LearningOutboxPublisher.TAG_OUTCOME, LearningOutboxPublisher.OUTCOME_SUCCESS)
                .counter()
                .count();
        double failures = registry.find(LearningOutboxPublisher.METRIC_PUBLISHED)
                .tag(LearningOutboxPublisher.TAG_OUTCOME, LearningOutboxPublisher.OUTCOME_FAILURE)
                .counter()
                .count();
        assertThat(successes).isEqualTo(1d);
        assertThat(failures).isEqualTo(1d);
    }

    @Test
    @DisplayName("Publisher remains functional when no MeterRegistry is supplied (back-compat constructor)")
    void noMeterRegistryDoesNotBreakPublisher() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        LearningOutboxEntity row = entity("LearningCompletion", "agg-1", "learning.completion.recorded", "{\"ok\":true}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(new ArrayList<>(List.of(row)));

        KafkaTemplate<String, String> kafka = kafkaTemplateMock();

        // 4-arg constructor (no MeterRegistry) — Phase 2 contract.
        LearningOutboxPublisher publisher = new LearningOutboxPublisher(repo, kafka, DEFAULT_TOPIC, 100, null);
        publisher.publishPendingEvents();

        assertThat(row.getPublishedAt()).isNotNull();
        verify(repo, times(1)).save(row);
    }

    @Test
    @DisplayName("currentBacklog() returns the unpublished row count from the repository")
    void currentBacklogReadsFromRepository() {
        LearningOutboxRepository repo = mock(LearningOutboxRepository.class);
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(new ArrayList<>(List.of(
                entity("LearningResource", "a", "learning.resource.opened", "{}"),
                entity("Fundo", "b", "fundo.sync.completed", "{}"),
                entity("LearningCompletion", "c", "learning.completion.recorded", "{}"))));

        LearningOutboxPublisher publisher = new LearningOutboxPublisher(
                repo, kafkaTemplateMock(), DEFAULT_TOPIC, 100, new SimpleMeterRegistry());

        assertThat(publisher.currentBacklog()).isEqualTo(3d);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static LearningOutboxEntity entity(
            String aggregateType, String aggregateId, String eventType, String payloadJson) {
        LearningOutboxEntity e = new LearningOutboxEntity();
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setPayloadJson(payloadJson);
        // Mirror the entity's @PrePersist defaults so the row looks like a freshly-flushed one.
        try {
            var idField = LearningOutboxEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, UUID.randomUUID());
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(reflectiveOperationException);
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> kafkaTemplateMock() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> null);
        // also stub the (ProducerRecord) overload so unexpected signatures don't NPE
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocation -> null);
        return kafka;
    }
}
