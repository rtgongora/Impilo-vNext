package zw.gov.mohcc.impilo.shareslip.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Share Slip Outbox Publisher -- reliable Kafka delivery.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * Topics:
 *   - share.link: share.link.created, share.link.claimed, share.link.expired, share.link.revoked
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

    @Scheduled(fixedDelayString = "${share-slip.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getAggregateType());
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

    private String resolveTopic(String aggregateType) {
        return switch (aggregateType) {
            case "SHARE_LINK" -> "share.link";
            case "SHARE_SLIP" -> "share.events";
            default -> "share.events";
        };
    }
}
