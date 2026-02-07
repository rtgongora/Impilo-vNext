package zw.gov.mohcc.impilo.landela.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.landela.config.LandelaProperties;
import zw.gov.mohcc.impilo.landela.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.landela.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Landela Adapter Event Outbox Publisher — reliable Kafka delivery.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * Topics:
 *   - {prefix}.document.created
 *   - {prefix}.document.updated
 *   - {prefix}.document.archived
 *   - {prefix}.document.revoked
 *   - {prefix}.document.superseded
 *   - {prefix}.document.deleted
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LandelaProperties properties;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           LandelaProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${landela.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                log.debug("Published event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage());
                // Will be retried on next poll
                break;
            }
        }
    }

    private String resolveTopic(String eventType) {
        String prefix = properties.getKafka().getTopicPrefix();
        return switch (eventType) {
            case "DOCUMENT_CREATED" -> prefix + ".document.created";
            case "DOCUMENT_UPDATED" -> prefix + ".document.updated";
            case "DOCUMENT_ARCHIVED" -> prefix + ".document.archived";
            case "DOCUMENT_REVOKED" -> prefix + ".document.revoked";
            case "DOCUMENT_SUPERSEDED" -> prefix + ".document.superseded";
            case "DOCUMENT_DELETED" -> prefix + ".document.deleted";
            default -> prefix + ".document.events";
        };
    }
}
