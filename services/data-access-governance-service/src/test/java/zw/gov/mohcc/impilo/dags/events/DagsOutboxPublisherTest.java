package zw.gov.mohcc.impilo.dags.events;

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
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class DagsOutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private DagsOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DagsOutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Nested
    @DisplayName("Topic Routing")
    class TopicRouting {

        @Test
        void routesPolicyCreated() {
            assertThat(DagsOutboxPublisher.resolveTopic("POLICY_CREATED"))
                    .isEqualTo("impilo.dags.policy.created.v1");
        }

        @Test
        void routesAccessRequested() {
            assertThat(DagsOutboxPublisher.resolveTopic("ACCESS_REQUESTED"))
                    .isEqualTo("impilo.dags.access.requested.v1");
        }

        @Test
        void routesAccessApproved() {
            assertThat(DagsOutboxPublisher.resolveTopic("ACCESS_APPROVED"))
                    .isEqualTo("impilo.dags.access.approved.v1");
        }

        @Test
        void routesAccessDenied() {
            assertThat(DagsOutboxPublisher.resolveTopic("ACCESS_DENIED"))
                    .isEqualTo("impilo.dags.access.denied.v1");
        }

        @Test
        void routesUnknownToFallback() {
            assertThat(DagsOutboxPublisher.resolveTopic("SOMETHING_ELSE"))
                    .isEqualTo("impilo.dags.unknown");
        }
    }

    @Nested
    @DisplayName("Publishing Behavior")
    class Publishing {

        @Test
        void doesNothingWhenNoPendingEvents() {
            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(Collections.emptyList());

            publisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(any(), any(), any());
        }

        @Test
        void publishesEventAndSetsPublishedAt() {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setId(1L);
            event.setAggregateType("POLICY");
            event.setAggregateId("policy-1");
            event.setEventType("POLICY_CREATED");
            event.setPayload("{\"id\":1}");

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("impilo.dags.policy.created.v1"), eq("policy-1"), eq("{\"id\":1}"));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getPublishedAt()).isNotNull();
        }
    }
}
