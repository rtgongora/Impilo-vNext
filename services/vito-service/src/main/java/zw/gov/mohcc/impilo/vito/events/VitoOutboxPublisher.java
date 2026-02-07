package zw.gov.mohcc.impilo.vito.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * VITO Event Outbox Publisher — reliable Kafka delivery.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * Topics:
 *   - vito.identity: IDENTITY_CREATED, IDENTITY_VERIFIED, IDENTITY_DECEASED
 *   - vito.cards:    CARD_REQUESTED, CARD_ACTIVATED, CARD_REVOKED
 *   - vito.wallet:   WALLET_TOPPED_UP, WALLET_PAYMENT
 */
@Component
public class VitoOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VitoOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public VitoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${vito.outbox.poll-interval-ms:2000}")
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
            case "CLIENT" -> "vito.identity";
            case "SMART_CARD" -> "vito.cards";
            case "WALLET" -> "vito.wallet";
            default -> "vito.events";
        };
    }
}
