package zw.gov.mohcc.impilo.surv.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
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
        publisher = new SurvOutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Nested
    @DisplayName("Topic Routing")
    class TopicRouting {

        @Test
        void routesSignalCreated() {
            assertThat(SurvOutboxPublisher.resolveTopic("SIGNAL_CREATED"))
                    .isEqualTo("impilo.surv.signal.created.v1");
        }

        @Test
        void routesSignalHit() {
            assertThat(SurvOutboxPublisher.resolveTopic("SIGNAL_HIT"))
                    .isEqualTo("impilo.surv.signal.hit.v1");
        }

        @Test
        void routesCaseOpened() {
            assertThat(SurvOutboxPublisher.resolveTopic("CASE_OPENED"))
                    .isEqualTo("impilo.surv.case.opened.v1");
        }

        @Test
        void routesUnknownToFallback() {
            assertThat(SurvOutboxPublisher.resolveTopic("OTHER"))
                    .isEqualTo("impilo.surv.unknown");
        }
    }

    @Nested
    @DisplayName("Publishing")
    class Publishing {

        @Test
        void doesNothingWhenNoPendingEvents() {
            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(Collections.emptyList());

            publisher.publishPendingEvents();

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

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("impilo.surv.signal.created.v1"), eq("sig-1"), eq("{\"id\":1}"));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getPublishedAt()).isNotNull();
        }
    }
}
