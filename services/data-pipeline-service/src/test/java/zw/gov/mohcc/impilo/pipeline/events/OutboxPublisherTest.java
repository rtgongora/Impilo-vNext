package zw.gov.mohcc.impilo.pipeline.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxPublisher}.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Nested
    @DisplayName("Topic Routing")
    class TopicRouting {

        @Test
        @DisplayName("EVENT_INGESTED routes to impilo.pipeline.event.ingested.v1")
        void eventIngestedTopic() {
            assertThat(OutboxPublisher.routeTopic("EVENT_INGESTED"))
                    .isEqualTo("impilo.pipeline.event.ingested.v1");
        }

        @Test
        @DisplayName("WATERMARK_UPDATED routes to impilo.pipeline.watermark.updated.v1")
        void watermarkUpdatedTopic() {
            assertThat(OutboxPublisher.routeTopic("WATERMARK_UPDATED"))
                    .isEqualTo("impilo.pipeline.watermark.updated.v1");
        }

        @Test
        @DisplayName("Unknown event type routes to fallback topic")
        void unknownEventTypeFallback() {
            assertThat(OutboxPublisher.routeTopic("UNKNOWN_TYPE"))
                    .isEqualTo("impilo.pipeline.events");
        }

        @Test
        @DisplayName("Null event type routes to fallback topic")
        void nullEventTypeFallback() {
            assertThat(OutboxPublisher.routeTopic(null))
                    .isEqualTo("impilo.pipeline.events");
        }
    }

    @Nested
    @DisplayName("Publishing Behavior")
    class PublishingBehavior {

        @Test
        @DisplayName("No-op when no pending events")
        void noopWhenEmpty() {
            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(Collections.emptyList());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("Publishes pending events and marks them published")
        void publishesPendingEvents() {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setId(1L);
            event.setAggregateType("IngestedEvent");
            event.setAggregateId("evt-001");
            event.setEventType("EVENT_INGESTED");
            event.setPayload("{\"eventId\":\"evt-001\"}");
            event.setTenantId(UUID.randomUUID());

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(
                    eq("impilo.pipeline.event.ingested.v1"),
                    eq("evt-001"),
                    eq("{\"eventId\":\"evt-001\"}"));
            verify(outboxRepository).save(event);
            assertThat(event.getPublishedAt()).isNotNull();
        }
    }
}
