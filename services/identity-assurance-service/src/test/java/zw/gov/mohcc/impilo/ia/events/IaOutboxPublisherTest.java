package zw.gov.mohcc.impilo.ia.events;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class IaOutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private IaOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new IaOutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Nested
    @DisplayName("Publish Pending Events")
    class PublishPendingEvents {

        @Test
        void publishesAttestationRecordedToCorrectTopic() {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setId(1L);
            event.setAggregateId("att-1");
            event.setEventType("ATTESTATION_RECORDED");
            event.setPayload("{\"id\":1}");

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send("impilo.ia.attestation.recorded.v1", "att-1", "{\"id\":1}");
            assertThat(event.getPublishedAt()).isNotNull();
        }

        @Test
        void publishesRiskAssessedToCorrectTopic() {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setId(2L);
            event.setAggregateId("risk-1");
            event.setEventType("RISK_ASSESSED");
            event.setPayload("{\"id\":2}");

            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(List.of(event));

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send("impilo.ia.risk.assessed.v1", "risk-1", "{\"id\":2}");
        }

        @Test
        void doesNothingWhenNoEvents() {
            when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                    .thenReturn(Collections.emptyList());

            publisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("Topic Resolution")
    class TopicResolution {

        @Test
        void resolvesKnownEventTypes() {
            assertThat(IaOutboxPublisher.resolveTopic("ATTESTATION_RECORDED"))
                    .isEqualTo("impilo.ia.attestation.recorded.v1");
            assertThat(IaOutboxPublisher.resolveTopic("RISK_ASSESSED"))
                    .isEqualTo("impilo.ia.risk.assessed.v1");
            assertThat(IaOutboxPublisher.resolveTopic("UNKNOWN_EVENT"))
                    .isEqualTo("impilo.ia.unknown");
        }
    }
}
