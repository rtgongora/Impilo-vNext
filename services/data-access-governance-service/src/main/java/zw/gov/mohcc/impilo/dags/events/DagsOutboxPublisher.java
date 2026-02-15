package zw.gov.mohcc.impilo.dags.events;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;

@Component
public class DagsOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DagsOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DagsOutboxPublisher(EventOutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${dags.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }

    static String resolveTopic(String eventType) {
        return switch (eventType) {
            case "POLICY_CREATED" -> "impilo.dags.policy.created.v1";
            case "ACCESS_REQUESTED" -> "impilo.dags.access.requested.v1";
            case "ACCESS_APPROVED" -> "impilo.dags.access.approved.v1";
            case "ACCESS_DENIED" -> "impilo.dags.access.denied.v1";
            default -> "impilo.dags.unknown";
        };
    }
}
