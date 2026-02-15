package zw.gov.mohcc.impilo.campaigns.events;

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
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class CampaignsOutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private CampaignsOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CampaignsOutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Nested
    @DisplayName("Topic Routing")
    class TopicRouting {

        @Test
        void routesCampaignCreated() {
            assertThat(CampaignsOutboxPublisher.resolveTopic("CAMPAIGN_CREATED"))
                    .isEqualTo("impilo.campaigns.created.v1");
        }

        @Test
        void routesEnrollmentCreated() {
            assertThat(CampaignsOutboxPublisher.resolveTopic("ENROLLMENT_CREATED"))
                    .isEqualTo("impilo.campaigns.enrolled.v1");
        }

        @Test
        void routesCampaignDispatched() {
            assertThat(CampaignsOutboxPublisher.resolveTopic("CAMPAIGN_DISPATCHED"))
                    .isEqualTo("impilo.campaigns.dispatched.v1");
        }

        @Test
        void routesUnknownToFallback() {
            assertThat(CampaignsOutboxPublisher.resolveTopic("OTHER"))
                    .isEqualTo("impilo.campaigns.unknown");
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
            event.setAggregateType("CAMPAIGN");
            event.setAggregateId("camp-1");
            event.setEventType("CAMPAIGN_CREATED");
            event.setPayload("{\"id\":1}");

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("impilo.campaigns.created.v1"), eq("camp-1"), eq("{\"id\":1}"));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getPublishedAt()).isNotNull();
        }
    }
}
