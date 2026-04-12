package zw.gov.mohcc.impilo.surv.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class SurvOutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private SurvOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SurvOutboxPublisher(outboxRepository, kafkaTemplate, "LEGACY_ONLY");
    }

    @Nested
    @DisplayName("Topic Routing")
    class TopicRouting {

        @Test
        void routesSignalCreated() {
            assertThat(SurvOutboxPublisher.resolveAnalyticsTopic("SIGNAL_CREATED"))
                    .isEqualTo("analytics.surveillance.event");
        }

        @Test
        void routesSignalHit() {
            assertThat(SurvOutboxPublisher.resolveAnalyticsTopic("SIGNAL_HIT"))
                    .isEqualTo("analytics.surveillance.event");
        }

        @Test
        void routesCaseOpened() {
            assertThat(SurvOutboxPublisher.resolveAnalyticsTopic("CASE_OPENED"))
                    .isEqualTo("analytics.surveillance.event");
        }

        @Test
        void routesAlertTriggered() {
            assertThat(SurvOutboxPublisher.resolveAnalyticsTopic("ALERT_TRIGGERED"))
                    .isEqualTo("analytics.surveillance.alert");
        }

        @Test
        void routesUnknownToFallback() {
            assertThat(SurvOutboxPublisher.resolveAnalyticsTopic("OTHER"))
                    .isEqualTo("analytics.surveillance.event");
        }
    }

    @Nested
    @DisplayName("Publishing")
    class Publishing {

        @Test
        void doesNothingWhenNoPendingEvents() {
            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(Collections.emptyList());

            publisher.poll();

            verify(kafkaTemplate, never()).send(any(), any(), any());
        }

        @Test
        void publishesAndMarksEvent() {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setId(1L);
            event.setAggregateType("SIGNAL");
            event.setAggregateId("sig-1");
            event.setEventType("SIGNAL_CREATED");
            event.setPayload("{\"id\":1}");
            event.setOccurredAt(java.time.OffsetDateTime.now());

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));
            when(outboxRepository.findById(1L)).thenReturn(Optional.of(event));

            publisher.poll();

            verify(kafkaTemplate).send(eq("analytics.surveillance.event"), eq("sig-1"), eq("{\"id\":1}"));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            assertThat(captor.getValue().getPublishedAt()).isNotNull();
        }
    }
}
