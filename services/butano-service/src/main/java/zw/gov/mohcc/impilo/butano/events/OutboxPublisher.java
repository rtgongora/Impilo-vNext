package zw.gov.mohcc.impilo.butano.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.butano.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.butano.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * BUTANO Event Outbox Publisher — reliable Kafka delivery.
 *
 * <p>Polls the butano_event_outbox table for unpublished events and
 * publishes them to the appropriate Kafka topic based on aggregate type.
 * Uses the transactional outbox pattern to ensure at-least-once delivery
 * with transactional consistency.</p>
 *
 * <p>Topics:</p>
 * <ul>
 *   <li>{@code butano.resource.created} — new FHIR resources stored in the SHR</li>
 *   <li>{@code butano.resource.updated} — existing FHIR resources modified</li>
 *   <li>{@code butano.reconcile.completed} — O-CPID to CPID reconciliation finished</li>
 * </ul>
 *
 * <p>On Kafka send failure the batch is aborted and will be retried on
 * the next polling interval. The partial-commit design ensures events
 * that were already marked as published are not resent.</p>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    static final String TOPIC_RESOURCE_CREATED = "butano.resource.created";
    static final String TOPIC_RESOURCE_UPDATED = "butano.resource.updated";
    static final String TOPIC_RECONCILE_COMPLETED = "butano.reconcile.completed";
    static final String TOPIC_DEFAULT = "butano.events";

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Poll for unpublished outbox events and deliver them to Kafka.
     *
     * <p>Runs on a fixed delay (configurable via {@code butano.outbox.poll-interval-ms},
     * default 2000 ms). Each invocation processes up to 100 events in creation-time
     * order. Events are marked as published individually so that a failure mid-batch
     * does not re-deliver already-sent events on the next poll.</p>
     */
    @Scheduled(fixedDelayString = "${butano.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);

                log.debug("Published outbox event {} [{}] to topic {}",
                        event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} [{}] to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                // Abort remaining events — they will be retried on next poll
                break;
            }
        }

        if (!events.isEmpty()) {
            log.info("Outbox poll complete: processed {} event(s)", events.size());
        }
    }

    /**
     * Resolve the Kafka topic for a given event type.
     *
     * @param eventType the event type from the outbox entry
     * @return the Kafka topic name
     */
    String resolveTopic(String eventType) {
        if (eventType == null) {
            return TOPIC_DEFAULT;
        }
        return switch (eventType) {
            case "RESOURCE_CREATED" -> TOPIC_RESOURCE_CREATED;
            case "RESOURCE_UPDATED" -> TOPIC_RESOURCE_UPDATED;
            case "RECONCILE_COMPLETED" -> TOPIC_RECONCILE_COMPLETED;
            default -> TOPIC_DEFAULT;
        };
    }
}
