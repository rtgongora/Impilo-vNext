package zw.gov.mohcc.impilo.credential.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.credential.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.credential.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Credential Verification Event Outbox Publisher — reliable Kafka delivery.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * Topics:
 *   - creds.credential.issued
 *   - creds.credential.revoked
 *   - creds.credential.verified
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${credential.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage());
                // Will be retried on next poll
                break;
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "credential.issued" -> "creds.credential.issued";
            case "credential.revoked" -> "creds.credential.revoked";
            case "credential.verified" -> "creds.credential.verified";
            default -> "creds.events";
        };
    }
}
