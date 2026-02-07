package zw.gov.mohcc.impilo.zibo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox event publisher for reliable Kafka delivery.
 *
 * <p>Implements the transactional outbox pattern: domain services write
 * events to the {@code event_outbox} table within their transaction, and
 * this publisher polls for unpublished events and delivers them to Kafka.</p>
 *
 * <p>Kafka topic routing is determined by the event type. Events that
 * fail to publish are retried on the next polling cycle.</p>
 *
 * <p>Topic mapping:</p>
 * <ul>
 *   <li>ARTIFACT_PUBLISHED, ARTIFACT_DEPRECATED, ARTIFACT_RETIRED
 *       -> {@code zibo.artifact.lifecycle}</li>
 *   <li>PACK_PUBLISHED, PACK_DEPRECATED
 *       -> {@code zibo.pack.lifecycle}</li>
 *   <li>PACK_ASSIGNED
 *       -> {@code zibo.pack.assigned}</li>
 *   <li>VALIDATION_FAILED
 *       -> {@code zibo.validation.failed}</li>
 *   <li>default -> {@code zibo.events}</li>
 * </ul>
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ZiboProperties ziboProperties;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ZiboProperties ziboProperties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.ziboProperties = ziboProperties;
    }

    /**
     * Poll for unpublished events and deliver them to Kafka.
     *
     * <p>Fetches up to 100 unpublished events ordered by creation time,
     * sends each to the appropriate Kafka topic using the aggregate ID
     * as the message key (for partition ordering), and marks each event
     * with a {@code publishedAt} timestamp on success.</p>
     *
     * <p>If a Kafka send fails, processing stops for the current batch
     * to preserve ordering. The failed event and all subsequent events
     * will be retried on the next poll cycle.</p>
     */
    @Scheduled(fixedDelayString = "${zibo.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (events.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending outbox events", events.size());

        int published = 0;
        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                published++;
                log.debug("Published event {} (type={}) to topic {}",
                        event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                log.error("Failed to publish event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                // Stop processing to preserve ordering — will retry on next poll
                break;
            }
        }

        if (published > 0) {
            log.info("Published {}/{} outbox events to Kafka", published, events.size());
        }
    }

    /**
     * Resolve the Kafka topic for a given event type.
     *
     * @param eventType the outbox event type string
     * @return the Kafka topic name to publish to
     */
    String resolveTopic(String eventType) {
        if (eventType == null) {
            return "zibo.events";
        }
        return switch (eventType) {
            case "ARTIFACT_CREATED", "ARTIFACT_UPDATED",
                 "ARTIFACT_PUBLISHED", "ARTIFACT_DEPRECATED", "ARTIFACT_RETIRED"
                    -> "zibo.artifact.lifecycle";
            case "PACK_PUBLISHED", "PACK_DEPRECATED"
                    -> "zibo.pack.lifecycle";
            case "PACK_ASSIGNED"
                    -> "zibo.pack.assigned";
            case "VALIDATION_FAILED"
                    -> "zibo.validation.failed";
            default -> "zibo.events";
        };
    }
}
