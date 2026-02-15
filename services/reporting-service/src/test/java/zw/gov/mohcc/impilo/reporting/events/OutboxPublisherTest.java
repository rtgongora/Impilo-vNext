package zw.gov.mohcc.impilo.reporting.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate);
    }

    // ---------------------------------------------------------------
    // routeTopic
    // ---------------------------------------------------------------

    @Test
    @DisplayName("routeTopic routes REPORT_CREATED to correct topic")
    void routeTopicReportCreated() {
        assertThat(OutboxPublisher.routeTopic("REPORT_CREATED"))
                .isEqualTo("impilo.reporting.report.created.v1");
    }

    @Test
    @DisplayName("routeTopic routes REPORT_RAN to correct topic")
    void routeTopicReportRan() {
        assertThat(OutboxPublisher.routeTopic("REPORT_RAN"))
                .isEqualTo("impilo.reporting.report.ran.v1");
    }

    @Test
    @DisplayName("routeTopic routes SCHEDULE_CREATED to correct topic")
    void routeTopicScheduleCreated() {
        assertThat(OutboxPublisher.routeTopic("SCHEDULE_CREATED"))
                .isEqualTo("impilo.reporting.schedule.created.v1");
    }

    @Test
    @DisplayName("routeTopic routes unknown event to fallback topic")
    void routeTopicFallback() {
        assertThat(OutboxPublisher.routeTopic("UNKNOWN_EVENT"))
                .isEqualTo("impilo.reporting.events");
    }

    // ---------------------------------------------------------------
    // publishPendingEvents
    // ---------------------------------------------------------------

    @Test
    @DisplayName("publishPendingEvents sends events to Kafka and marks published")
    void publishPendingEventsSuccess() {
        EventOutboxEntity event = buildEvent("REPORT_CREATED", "agg-1", "{\"test\":true}");
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send(
                eq("impilo.reporting.report.created.v1"),
                eq("agg-1"),
                eq("{\"test\":true}"));

        ArgumentCaptor<EventOutboxEntity> captor =
                ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishPendingEvents does nothing when no pending events")
    void publishPendingEventsNoPending() {
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());

        publisher.publishPendingEvents();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("publishPendingEvents stops on Kafka failure")
    void publishPendingEventsKafkaFailure() {
        EventOutboxEntity event1 = buildEvent("REPORT_CREATED", "agg-1", "{}");
        EventOutboxEntity event2 = buildEvent("REPORT_RAN", "agg-2", "{}");
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("Kafka down"))
                .when(kafkaTemplate).send(any(), any(), any());

        publisher.publishPendingEvents();

        // Should have attempted first event only, then broken
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishPendingEvents publishes multiple events in order")
    void publishPendingEventsMultiple() {
        EventOutboxEntity event1 = buildEvent("REPORT_CREATED", "agg-1", "{\"n\":1}");
        EventOutboxEntity event2 = buildEvent("SCHEDULE_CREATED", "agg-2", "{\"n\":2}");
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event1, event2));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send(
                eq("impilo.reporting.report.created.v1"), eq("agg-1"), eq("{\"n\":1}"));
        verify(kafkaTemplate).send(
                eq("impilo.reporting.schedule.created.v1"), eq("agg-2"), eq("{\"n\":2}"));
        verify(outboxRepository, times(2)).save(any());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private EventOutboxEntity buildEvent(String eventType, String aggregateId, String payload) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setId(1L);
        entity.setAggregateType("REPORT");
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }
}
